package com.lagradost.cloudstream3.ui.player

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.io.File

/**
 * Records what happens around a stutter, to the app log (tag [TAG]), while "Show playback stats"
 * is on. Every line starts with DIAG so the log can be filtered.
 *
 * - CLOCK: the player position moved more or less than real time while playing
 * - PLAYBACK_LAG / MAIN_LAG: the playback thread (feeds the decoders) or the main thread ran a
 *   task late
 * - UNDERRUN, DROPPED, errors, discontinuities, buffering: straight from media3
 * - CPU: on every problem, the app threads that used the most CPU in the last second, by name
 * - GC: collections, and blocking ones, since the last line
 */
@OptIn(UnstableApi::class)
class PlaybackDiagnostics(private val player: ExoPlayer) : AnalyticsListener {
    companion object {
        const val TAG = "CS3Diag"
        private const val TICK_MS = 100L
        private const val CLOCK_JUMP_MS = 80L
        private const val THREAD_LAG_MS = 50L
        private const val MAIN_LAG_MS = 100L
        private const val HISTORY_LINES = 600

        /**
         * The system log on Fire TV is small and drops this app's lines when it fills up, so the
         * lines are also kept here and added to the logcat export
         */
        private val history = ArrayDeque<String>()
        private val timeFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)

        @Volatile
        var problemCount = 0
            private set

        @Volatile
        var lastProblem: String? = null
            private set

        fun record(line: String) {
            synchronized(history) {
                history.addLast("${timeFormat.format(java.util.Date())} $line")
                while (history.size > HISTORY_LINES) history.removeFirst()
            }
        }

        /** Everything recorded so far, for the logcat export */
        fun historyText(): String = synchronized(history) {
            if (history.isEmpty()) "" else "Playback diagnostics\n" + history.joinToString("\n")
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val playbackHandler = Handler(player.playbackLooper)
    private var running = false

    private var lastWallMs = 0L
    private var lastPositionMs = 0L
    private var lastFrameNs = 0L
    private var lastGc = gcCounts()

    /** Thread id to (name, cpu ticks) from the last sample */
    private var lastCpu: Map<Int, Pair<String, Long>> = emptyMap()
    private var cpuSampleWallMs = 0L

    fun start() {
        if (running) return
        running = true
        player.addAnalyticsListener(this)
        lastWallMs = SystemClock.elapsedRealtime()
        lastPositionMs = player.currentPosition
        lastCpu = sampleThreads()
        cpuSampleWallMs = lastWallMs
        log("start ${player.videoFormat?.let { "${it.width}x${it.height} ${it.sampleMimeType} ${it.frameRate}fps" } ?: ""}")
        main.postDelayed(tick, TICK_MS)
        schedulePlaybackProbe()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacks(tick)
        playbackHandler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        try {
            player.removeAnalyticsListener(this)
        } catch (_: Throwable) {
        }
        log("stop")
    }

    private fun log(message: String) {
        Log.i(TAG, "DIAG $message")
        record(message)
    }

    /** A problem: log it with the busiest threads and collections since the last second */
    private fun problem(message: String) {
        val gc = gcCounts()
        val gcText = "gc +${gc.first - lastGc.first} blocking +${gc.second - lastGc.second} (+${gc.third - lastGc.third} ms)"
        lastGc = gc
        problemCount++
        lastProblem = message
        log("$message | $gcText | buffer ${(player.bufferedPosition - player.currentPosition) / 1000f}s | cpu ${busiestThreads()}")
    }

    // ---- clock: position against real time, every 100 ms -------------------------------------

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            val position = player.currentPosition
            if (player.isPlaying && player.playbackParameters.speed == 1f) {
                val wall = now - lastWallMs
                val moved = position - lastPositionMs
                val off = moved - wall
                if (kotlin.math.abs(off) > CLOCK_JUMP_MS && kotlin.math.abs(off) < 5_000) {
                    problem("CLOCK moved ${moved}ms in ${wall}ms real (${if (off > 0) "+" else ""}${off}ms) at ${position}ms")
                }
            }
            lastWallMs = now
            lastPositionMs = position
            // Keep a CPU baseline about one second old
            if (now - cpuSampleWallMs >= 1000) {
                lastCpu = sampleThreads()
                cpuSampleWallMs = now
            }
            main.postDelayed(this, TICK_MS)
        }
    }

    // ---- playback thread: how late does a posted task run -------------------------------------

    private fun schedulePlaybackProbe() {
        if (!running) return
        val postedAt = SystemClock.uptimeMillis()
        playbackHandler.postDelayed({
            val late = SystemClock.uptimeMillis() - postedAt - TICK_MS
            if (late > THREAD_LAG_MS) problem("PLAYBACK_LAG ${late}ms")
            schedulePlaybackProbe()
        }, TICK_MS)
    }

    // ---- main thread: gaps between frames -------------------------------------------------------

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameNs != 0L) {
                val gapMs = (frameTimeNanos - lastFrameNs) / 1_000_000
                if (gapMs > MAIN_LAG_MS) problem("MAIN_LAG ${gapMs}ms")
            }
            lastFrameNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ---- media3 events ---------------------------------------------------------------------------

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long
    ) = problem("UNDERRUN audio buffer ${bufferSizeMs}ms, last fed ${elapsedSinceLastFeedMs}ms ago")

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) = problem("DROPPED $droppedFrames frames in ${elapsedMs}ms")

    override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) =
        problem("AUDIO_SINK_ERROR $audioSinkError")

    override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) =
        problem("AUDIO_CODEC_ERROR $audioCodecError")

    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) =
        problem("VIDEO_CODEC_ERROR $videoCodecError")

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) =
        problem("PLAYER_ERROR ${error.errorCodeName} ${error.message}")

    override fun onPositionDiscontinuity(
        eventTime: AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) = log("DISCONTINUITY reason $reason ${oldPosition.positionMs} -> ${newPosition.positionMs}")

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        val name = when (state) {
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "IDLE"
        }
        if (state == Player.STATE_BUFFERING) problem("STATE $name") else log("STATE $name")
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) = log("audio decoder $decoderName")

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) = log("video decoder $decoderName")

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: androidx.media3.common.Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
    ) = log("audio format ${format.sampleMimeType} ${format.channelCount}ch ${format.sampleRate}Hz")

    // ---- threads and collections ------------------------------------------------------------------

    private fun gcCounts(): Triple<Long, Long, Long> = Triple(
        Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L,
        Debug.getRuntimeStat("art.gc.blocking-gc-count")?.toLongOrNull() ?: 0L,
        Debug.getRuntimeStat("art.gc.blocking-gc-time")?.toLongOrNull() ?: 0L,
    )

    /** Name and CPU ticks (user + system) of every thread of this app */
    private fun sampleThreads(): Map<Int, Pair<String, Long>> {
        val result = HashMap<Int, Pair<String, Long>>()
        try {
            File("/proc/self/task").listFiles()?.forEach { dir ->
                val tid = dir.name.toIntOrNull() ?: return@forEach
                try {
                    val stat = File(dir, "stat").readText()
                    // The name is in brackets and may contain spaces, the fields follow it
                    val close = stat.lastIndexOf(')')
                    val name = stat.substring(stat.indexOf('(') + 1, close)
                    val fields = stat.substring(close + 2).split(' ')
                    // utime and stime are fields 14 and 15, here 11 and 12 after the name
                    val ticks = fields[11].toLong() + fields[12].toLong()
                    result[tid] = name to ticks
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }

    /** The app threads that used the most CPU since the last sample, as name:percent of a core */
    private fun busiestThreads(): String {
        val now = sampleThreads()
        val wallMs = (SystemClock.elapsedRealtime() - cpuSampleWallMs).coerceAtLeast(1)
        // Ticks are 1/100 s on Android
        val busiest = now.mapNotNull { (tid, value) ->
            val before = lastCpu[tid]?.second ?: return@mapNotNull null
            val usedMs = (value.second - before) * 10
            if (usedMs <= 0) null else value.first to usedMs
        }.sortedByDescending { it.second }.take(6)
        val total = now.values.sumOf { it.second } - lastCpu.values.sumOf { it.second }
        return "total ${total * 10 * 100 / wallMs}% " + busiest.joinToString(" ") { (name, ms) ->
            "$name:${ms * 100 / wallMs}%"
        }
    }
}
