package com.lagradost.cloudstream3.ui.player

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** Plain copy of the parts of [Display.Mode] that matter here, so the selection can be unit tested. */
internal data class DisplayModeInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/**
 * Switches the HDMI output to the refresh rate that fits the video, so that 23.976/24/25/29.97 fps
 * content is not shown with 3:2 pulldown judder on a 60 Hz output. This is what Fire TV calls
 * "Match original frame rate", and the system setting only applies to the built in apps.
 *
 * Only the refresh rate is changed, the resolution stays as the user picked it, to avoid
 * flipping between 1080p and 4K.
 */
object FrameRateMatcher {
    private const val TAG = "FrameRateMatcher"

    /** Window.preferredDisplayModeId value for "no preference", the system default is used. */
    private const val NO_PREFERRED_MODE = 0

    /** How far the display may be from a whole multiple of the video rate, 23.976 vs 24 is 0.1% */
    private const val MAX_RATIO_ERROR = 0.01f

    /**
     * Below this a mode counts as exact. 24 Hz for 23.976 fps is 0.1% off, which repeats a frame
     * about every 42 seconds, a visible skip on panning shots. 29.97 on 60 Hz does the same
     * every 33 seconds. So an exact mode is worth a switch even when a near one is active
     */
    private const val EXACT_RATIO_ERROR = 0.0005f

    /** 24 fps on a 120 Hz display is still judder free, anything beyond that is not realistic */
    private const val MAX_MULTIPLE = 5

    private const val MIN_VIDEO_FPS = 10f
    private const val MAX_VIDEO_FPS = 120f

    /** Returns how many display refreshes fit in one video frame, and how far it is off. */
    private fun fit(refreshRate: Float, videoFps: Float): Pair<Int, Float>? {
        if (refreshRate <= 0f) return null
        val ratio = refreshRate / videoFps
        val multiple = ratio.roundToInt()
        if (multiple !in 1..MAX_MULTIPLE) return null
        val error = abs(ratio - multiple) / multiple
        return if (error <= MAX_RATIO_ERROR) multiple to error else null
    }

    /**
     * Picks the mode to switch to, or null if the current one is fine (or nothing better fits).
     *
     * A mode fits if its refresh rate is a whole multiple of the frame rate, 30 fps on 60 Hz
     * needs no switch. Exact modes win over near ones (23.976 Hz over 24 Hz for 23.976 fps),
     * then the lowest multiple, then the closest rate. The current mode is kept when it is exact,
     * or when it is near and no exact mode exists.
     */
    internal fun pickMode(
        modes: List<DisplayModeInfo>,
        current: DisplayModeInfo,
        videoFps: Float
    ): DisplayModeInfo? {
        if (!videoFps.isFinite() || videoFps !in MIN_VIDEO_FPS..MAX_VIDEO_FPS) return null
        val currentFit = fit(current.refreshRate, videoFps)
        if (currentFit != null && currentFit.second <= EXACT_RATIO_ERROR) return null

        val (best, bestFit) = modes
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { mode -> fit(mode.refreshRate, videoFps)?.let { mode to it } }
            .minWithOrNull(
                compareBy(
                    { it.second.second > EXACT_RATIO_ERROR },
                    { it.second.first },
                    { it.second.second })
            ) ?: return null

        // Near is as good as it gets on this TV, a switch would only blank the screen
        if (currentFit != null && bestFit.second > EXACT_RATIO_ERROR) return null
        if (best.id == current.id) return null
        return best
    }

    private fun Display.Mode.toInfo() = DisplayModeInfo(
        id = modeId,
        width = physicalWidth,
        height = physicalHeight,
        refreshRate = refreshRate
    )

    fun isEnabled(context: Context): Boolean {
        // Phones and tablets switch their refresh rate on their own, this is for HDMI outputs
        if (!isLayout(TV or EMULATOR)) return false
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.match_frame_rate_key), true)
    }

    @Suppress("DEPRECATION")
    private fun Activity.currentDisplay(): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
        else windowManager?.defaultDisplay
    }

    /**
     * Call when the frame rate of the playing video is known, on the main thread.
     * Returns true when the display is switching, the screen then goes black for a moment.
     */
    fun onVideoFrameRate(activity: Activity?, frameRate: Float): Boolean {
        if (activity == null || !isEnabled(activity)) return false
        try {
            val display = activity.currentDisplay() ?: return false
            val modes = display.supportedModes.map { it.toInfo() }
            val picked = pickMode(modes, display.mode.toInfo(), frameRate) ?: return false

            val window = activity.window
            val params = window.attributes
            if (params.preferredDisplayModeId == picked.id) return false

            Log.i(
                TAG,
                "Video ${frameRate}fps on ${display.mode.refreshRate}Hz, " +
                        "switching to mode ${picked.id} (${picked.refreshRate}Hz)"
            )
            params.preferredDisplayModeId = picked.id
            window.attributes = params
            return true
        } catch (t: Throwable) {
            // A failed switch must never break playback
            Log.e(TAG, "Failed to match frame rate", t)
            return false
        }
    }

    /**
     * Give the display back to the system. The window outlives the player, so without this
     * the menus would stay in the 24 Hz mode after leaving a movie.
     */
    fun reset(activity: Activity?) {
        if (activity == null) return
        try {
            val window = activity.window
            val params = window.attributes
            if (params.preferredDisplayModeId == NO_PREFERRED_MODE) return
            params.preferredDisplayModeId = NO_PREFERRED_MODE
            window.attributes = params
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to reset display mode", t)
        }
    }
}
