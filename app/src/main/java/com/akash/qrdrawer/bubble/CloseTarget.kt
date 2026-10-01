package com.akash.qrdrawer.bubble

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.hypot

/** The "×" drop target shown at the bottom of the screen while the bubble is dragged. */
class CloseTarget(
    private val context: Context,
    private val windowManager: WindowManager,
    private val dp: (Int) -> Int,
    private val screenSize: () -> Point,
) {
    private var view: TextView? = null

    /** True while the dragged bubble is close enough that releasing it hides the bubble. */
    var isActive = false
        private set

    fun show() {
        if (view != null) return
        isActive = false
        val size = dp(SIZE_DP)
        val target = TextView(context).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(IDLE_COLOR)
            }
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val center = center()
            x = center.x - size / 2
            y = center.y - size / 2
        }
        runCatching { windowManager.addView(target, params) }
        target.animate().alpha(1f).setDuration(FADE_MS).start()
        view = target
    }

    fun track(rawX: Float, rawY: Float) {
        val target = view ?: return
        val center = center()
        val near = hypot(rawX - center.x, rawY - center.y) < dp(SIZE_DP)
        if (near == isActive) return
        isActive = near
        val scale = if (near) 1.25f else 1f
        target.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
        (target.background as? GradientDrawable)?.setColor(if (near) ACTIVE_COLOR else IDLE_COLOR)
        if (near) target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun remove() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun center(): Point {
        val screen = screenSize()
        return Point(screen.x / 2, screen.y - dp(BOTTOM_OFFSET_DP))
    }

    private companion object {
        const val SIZE_DP = 64
        const val BOTTOM_OFFSET_DP = 96
        const val FADE_MS = 150L
        const val IDLE_COLOR = 0xCC1B2029.toInt()
        const val ACTIVE_COLOR = 0xEEC62828.toInt()
    }
}
