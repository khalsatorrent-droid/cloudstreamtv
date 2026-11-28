package com.lagradost.cloudstream3.ui.player

import kotlin.math.abs

/**
 * Measures the real frame rate of a video from the timestamps of its first frames.
 *
 * Files often state the wrong rate: a 23.976 fps stream labelled 24.000 is common in mkv
 * files from streaming services. The TV then runs at 24.000 Hz for 23.976 fps video and has to
 * show one frame twice about every 42 seconds, seen as the picture holding for a moment while
 * the sound goes on. The timestamps tell the truth, so the display can be switched to the
 * rate the video really has.
 */
object VideoFrameRateProbe {
    /** Called with the measured rate when it differs from the stated one, on any thread */
    @Volatile
    var listener: ((Float) -> Unit)? = null

    /** Last measured rate, for the playback stats */
    @Volatile
    var lastMeasured: Float = 0f
        internal set

    /** About 6 seconds of 24 fps video, enough to tell 23.976 from 24 with 1 ms timestamps */
    internal const val SAMPLE_COUNT = 150

    private val standardRates = floatArrayOf(
        24000f / 1001f, 24f, 25f, 30000f / 1001f, 30f,
        48000f / 1001f, 48f, 50f, 60000f / 1001f, 60f,
    )

    /** Within this the measured rate is taken to be the standard rate next to it */
    private const val SNAP_ERROR = 0.0006f

    /** Two rates closer than this count as the same, see FrameRateMatcher */
    private const val SAME_RATE_ERROR = 0.0005f

    /**
     * The frame rate the timestamps show, or null if they are too irregular to tell.
     * Order does not matter (b-frames come out of order) and a jump (a seek) is ignored.
     */
    internal fun measure(timestampsUs: LongArray, count: Int): Float? {
        if (count < 24) return null
        val sorted = timestampsUs.copyOf(count).apply { sort() }
        val diffs = LongArray(count - 1) { sorted[it + 1] - sorted[it] }
        val median = diffs.copyOf().apply { sort() }[diffs.size / 2]
        if (median <= 0) return null
        // Only steps near the usual frame step: skips a seek, keeps the +-1 ms rounding of mkv
        var sum = 0L
        var steps = 0
        for (diff in diffs) {
            if (abs(diff - median) * 4 <= median) {
                sum += diff
                steps++
            }
        }
        if (steps < count / 2) return null
        val fps = 1_000_000f * steps / sum
        return snap(fps)
    }

    /** The standard rate next to [fps] if it is within the measuring error, else [fps] */
    internal fun snap(fps: Float): Float {
        val nearest = standardRates.minByOrNull { abs(it - fps) / it } ?: return fps
        return if (abs(nearest - fps) / nearest <= SNAP_ERROR) nearest else fps
    }

    internal fun differs(measured: Float, stated: Float): Boolean =
        stated <= 0f || abs(measured - stated) / stated > SAME_RATE_ERROR

    /** Collects the first frame timestamps of one video track */
    class Sampler(private val statedFps: Float) {
        private val timestamps = LongArray(SAMPLE_COUNT)
        private var count = 0
        private var done = false

        fun add(timeUs: Long) {
            if (done) return
            timestamps[count++] = timeUs
            if (count < SAMPLE_COUNT) return
            done = true
            val measured = measure(timestamps, count) ?: return
            lastMeasured = measured
            if (differs(measured, statedFps)) listener?.invoke(measured)
        }
    }
}
