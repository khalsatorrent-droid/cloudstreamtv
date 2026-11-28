package com.lagradost.cloudstream3.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class VideoFrameRateProbeTest {
    /** Timestamps as mkv stores them: rounded to the millisecond, some b-frames swapped */
    private fun mkvTimestamps(fps: Double, count: Int = 150, startUs: Long = 0): LongArray {
        val ts = LongArray(count) { i -> ((startUs + i * 1_000_000.0 / fps) / 1000).roundToLong() * 1000 }
        var k = 1
        while (k + 1 < count) {
            val t = ts[k]; ts[k] = ts[k + 1]; ts[k + 1] = t
            k += 4
        }
        return ts
    }

    private fun measure(fps: Double, startUs: Long = 0): Float? {
        val ts = mkvTimestamps(fps, startUs = startUs)
        return VideoFrameRateProbe.measure(ts, ts.size)
    }

    @Test
    fun tells_23_976_from_24() {
        assertEquals(24000f / 1001f, measure(24000.0 / 1001.0)!!, 0.0001f)
        assertEquals(24f, measure(24.0)!!, 0.0001f)
        assertEquals(24000f / 1001f, measure(24000.0 / 1001.0, startUs = 5_000_000_000)!!, 0.0001f)
    }

    @Test
    fun other_standard_rates() {
        assertEquals(25f, measure(25.0)!!, 0.0001f)
        assertEquals(30000f / 1001f, measure(30000.0 / 1001.0)!!, 0.0001f)
        assertEquals(60000f / 1001f, measure(60000.0 / 1001.0)!!, 0.001f)
    }

    @Test
    fun a_seek_in_the_samples_is_ignored() {
        val fps = 24000.0 / 1001.0
        val first = mkvTimestamps(fps, 70)
        val second = mkvTimestamps(fps, 80, startUs = 60_000_000)
        val ts = first + second
        assertEquals(24000f / 1001f, VideoFrameRateProbe.measure(ts, ts.size)!!, 0.0001f)
    }

    @Test
    fun too_few_samples() {
        val ts = mkvTimestamps(24.0, 10)
        assertNull(VideoFrameRateProbe.measure(ts, ts.size))
    }

    @Test
    fun differs_only_beyond_the_same_rate_margin() {
        assertTrue(VideoFrameRateProbe.differs(24000f / 1001f, 24f))
        assertFalse(VideoFrameRateProbe.differs(24f, 24f))
        assertTrue(VideoFrameRateProbe.differs(24f, 0f))
    }
}
