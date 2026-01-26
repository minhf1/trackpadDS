package com.example.ayn_thor_bottom_screen_power_tool

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.roundToInt

class FloatingTrackpadLayout(ctx: Context) : FrameLayout(ctx) {

    private val headerHeightDp = 36

    init {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.argb(220, 30, 30, 30))
            }
            elevation = 20f
        }

        val header = View(ctx).apply {
            setBackgroundColor(Color.argb(220, 50, 50, 50))
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(headerHeightDp)
            )
        }

        val trackpad = TrackpadView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        card.addView(header)
        card.addView(trackpad)

        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        })

        // Drag the activity window by dragging the header
        header.setOnTouchListener(WindowDragTouchListener(ctx))
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).roundToInt()
    }

    private class WindowDragTouchListener(val ctx: Context) : OnTouchListener {
        private var lastRawX = 0f
        private var lastRawY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val a = ctx as? Activity ?: return false

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = e.rawX
                    lastRawY = e.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - lastRawX
                    val dy = e.rawY - lastRawY
                    lastRawX = e.rawX
                    lastRawY = e.rawY

                    val lp = a.window.attributes
                    lp.x = (lp.x + dx).toInt()
                    lp.y = (lp.y + dy).toInt()
                    a.window.attributes = lp
                    return true
                }
            }
            return false
        }
    }
}
