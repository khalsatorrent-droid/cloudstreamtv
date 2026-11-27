package com.lagradost.cloudstream3.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateMatcherTest {
    private fun mode(id: Int, refresh: Float, width: Int = 3840, height: Int = 2160) =
        DisplayModeInfo(id, width, height, refresh)

    // What a Fire TV Stick 4K typically lists for a 4K TV
    private val m24 = mode(1, 24.0f)
    private val m23 = mode(2, 23.976f)
    private val m25 = mode(3, 25.0f)
    private val m30 = mode(4, 30.0f)
    private val m29 = mode(5, 29.97f)
    private val m50 = mode(6, 50.0f)
    private val m60 = mode(7, 60.0f)
    private val m59 = mode(8, 59.94f)
    private val hd60 = mode(9, 60.0f, 1920, 1080)
    private val hd24 = mode(10, 24.0f, 1920, 1080)
    private val all = listOf(m24, m23, m25, m30, m29, m50, m60, m59, hd60, hd24)

    @Test
    fun film_on_60hz_switches_to_24hz() {
        assertEquals(m24, FrameRateMatcher.pickMode(listOf(m24, m60), m60, 24.0f))
    }

    @Test
    fun ntsc_film_prefers_23_976_over_24() {
        assertEquals(m23, FrameRateMatcher.pickMode(all, m60, 23.976f))
    }

    @Test
    fun pal_content_switches_to_25hz() {
        assertEquals(m25, FrameRateMatcher.pickMode(all, m60, 25.0f))
    }

    @Test
    fun ntsc_video_prefers_29_97() {
        assertEquals(m29, FrameRateMatcher.pickMode(all, m50, 29.97f))
    }

    @Test
    fun ntsc_video_on_60hz_moves_to_59_94() {
        // 60 Hz is two refreshes per frame but 0.1% off, a skipped frame every 33 seconds
        assertEquals(m59, FrameRateMatcher.pickMode(all, m60, 29.97f))
    }

    @Test
    fun ntsc_video_on_60hz_is_left_alone_without_an_exact_mode() {
        assertNull(FrameRateMatcher.pickMode(listOf(m60, m50), m60, 29.97f))
    }

    @Test
    fun fifty_hz_is_used_when_no_exact_rate_exists() {
        // 25 fps, only 50 and 60 listed: 50 is two refreshes per frame
        assertEquals(m50, FrameRateMatcher.pickMode(listOf(m50, m60), m60, 25.0f))
    }

    @Test
    fun matching_current_mode_is_left_alone() {
        assertNull(FrameRateMatcher.pickMode(all, m24, 24.0f))
        assertNull(FrameRateMatcher.pickMode(all, m59, 59.94f))
    }

    @Test
    fun whole_multiple_of_current_mode_is_left_alone() {
        // 30 fps on 60 Hz is judder free, no need to blank the screen
        assertNull(FrameRateMatcher.pickMode(all, m60, 30.0f))
    }

    @Test
    fun near_match_moves_to_the_exact_rate() {
        // 24 Hz for 23.976 fps is 0.1% off, a repeated frame every 42 seconds
        assertEquals(m23, FrameRateMatcher.pickMode(all, m24, 23.976f))
    }

    @Test
    fun near_match_is_left_alone_without_an_exact_mode() {
        assertNull(FrameRateMatcher.pickMode(listOf(m24, m60), m24, 23.976f))
    }

    @Test
    fun mkv_default_duration_rate_matches_23_976() {
        // 41708333 ns per frame, what a 23.976 fps mkv stores
        assertEquals(m23, FrameRateMatcher.pickMode(all, m60, 1_000_000_000f / 41708333))
    }

    @Test
    fun resolution_is_never_changed() {
        // Only a 1080p 24 Hz mode exists, the 4K output must not flip to it
        assertNull(FrameRateMatcher.pickMode(listOf(m60, hd24), m60, 24.0f))
        // And the same the other way around
        assertEquals(hd24, FrameRateMatcher.pickMode(listOf(hd60, hd24, m24), hd60, 24.0f))
    }

    @Test
    fun no_fitting_mode_keeps_the_current_one() {
        assertNull(FrameRateMatcher.pickMode(listOf(m60), mode(0, 50.0f), 24.0f))
    }

    @Test
    fun unusable_frame_rates_are_ignored() {
        assertNull(FrameRateMatcher.pickMode(all, m60, 0f))
        assertNull(FrameRateMatcher.pickMode(all, m60, -1f))
        assertNull(FrameRateMatcher.pickMode(all, m60, Float.NaN))
        assertNull(FrameRateMatcher.pickMode(all, m60, 1000f))
    }
}
