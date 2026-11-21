package com.lagradost.cloudstream3.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * [DefaultLoadControl] that gives up the back buffer when the buffer is almost full.
 *
 * The back buffer counts towards [targetBufferBytes]. On high bitrate video (4K remuxes) a 30s
 * back buffer is larger than the entire target, so the default load control stops loading until
 * less than 500ms is left to play, and only then drops the back buffer and loads again.
 * Shrinking the back buffer as the target is approached keeps the memory for what is ahead.
 */
@OptIn(UnstableApi::class)
class MemoryAwareLoadControl private constructor(
    private val bufferAllocator: DefaultAllocator,
    private val targetBufferBytes: Int,
    maxBufferMs: Int,
    backBufferMs: Int,
) : DefaultLoadControl(
    bufferAllocator,
    DEFAULT_MIN_BUFFER_MS,
    DEFAULT_MIN_BUFFER_MS,
    maxBufferMs,
    maxBufferMs,
    DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    targetBufferBytes,
    DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
    DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
    backBufferMs,
    true,
) {
    constructor(targetBufferBytes: Int, maxBufferMs: Int, backBufferMs: Int) : this(
        DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
        targetBufferBytes,
        maxBufferMs,
        backBufferMs,
    )

    override fun getBackBufferDurationUs(playerId: PlayerId): Long {
        return scaleBackBuffer(
            super.getBackBufferDurationUs(playerId),
            bufferAllocator.totalBytesAllocated.toLong(),
            targetBufferBytes.toLong(),
        )
    }

    companion object {
        /** What [DefaultLoadControl] targets for a normal video with audio and subtitles */
        private const val DEFAULT_TARGET_BYTES = DEFAULT_MUXED_BUFFER_SIZE
        private const val MAX_AUTOMATIC_TARGET_BYTES = 400L * 1024L * 1024L

        /** The back buffer is kept in full below this fraction of the target */
        private const val BACK_BUFFER_FULL_BELOW = 0.5
        /** And is fully dropped above this fraction of the target */
        private const val BACK_BUFFER_NONE_ABOVE = 0.9

        /**
         * Buffer size to use when the user has not picked one: the media3 default, or more if
         * the heap has room for it.
         */
        fun automaticTargetBufferBytes(maxHeapBytes: Long = Runtime.getRuntime().maxMemory()): Int {
            return (maxHeapBytes * 2 / 5)
                .coerceIn(DEFAULT_TARGET_BYTES.toLong(), MAX_AUTOMATIC_TARGET_BYTES)
                .toInt()
        }

        fun scaleBackBuffer(backBufferUs: Long, allocatedBytes: Long, targetBytes: Long): Long {
            if (targetBytes <= 0L) return backBufferUs
            val used = allocatedBytes.toDouble() / targetBytes.toDouble()
            val keep = (BACK_BUFFER_NONE_ABOVE - used) /
                    (BACK_BUFFER_NONE_ABOVE - BACK_BUFFER_FULL_BELOW)
            return (backBufferUs * keep.coerceIn(0.0, 1.0)).toLong()
        }
    }
}
