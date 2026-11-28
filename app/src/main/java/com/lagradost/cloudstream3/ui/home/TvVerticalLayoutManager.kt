package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The list of home rows on TV. Moving up or down glides to the next row, but pressing again
 * while it glides (or holding the remote) left the page trailing behind the focus. A press soon
 * after the last one moves the page at once.
 */
class TvVerticalLayoutManager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayoutManager(context, attrs, defStyleAttr, defStyleRes) {
    private var lastScrollRequestMs = 0L

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        val pressedQuickly = now - lastScrollRequestMs < QUICK_PRESS_MS
        lastScrollRequestMs = now
        if (pressedQuickly) parent.stopScroll()
        return super.requestChildRectangleOnScreen(
            parent, child, rect, immediate || pressedQuickly, focusedChildVisible
        )
    }

    private companion object {
        const val QUICK_PRESS_MS = 200L
    }
}
