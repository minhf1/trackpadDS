package com.example.ayn_thor_bottom_screen_power_tool

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.roundToInt

class FloatingTrackpadLayout(
    private val activity: android.app.Activity
) : FrameLayout(activity) {

    init {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(UiConstants.Sizes.TRACKPAD_CARD_RADIUS).toFloat()
                setColor(UiConstants.Colors.TRACKPAD_CARD)
            }
            elevation = dp(UiConstants.Sizes.TRACKPAD_ELEVATION).toFloat()
        }

        val header = View(activity).apply {
            setBackgroundColor(UiConstants.Colors.TRACKPAD_HEADER)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(UiConstants.Sizes.TRACKPAD_HEADER)
            )
        }

        val trackpad = TrackpadView(activity).apply {
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
        header.setOnTouchListener(WindowDragTouchListener(activity))
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).roundToInt()
    }

    private class WindowDragTouchListener(
        private val activity: android.app.Activity
    ) : OnTouchListener {
        private var lastRawX = 0f
        private var lastRawY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
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

                    val lp = activity.window.attributes
                    lp.x = (lp.x + dx).toInt()
                    lp.y = (lp.y + dy).toInt()
                    activity.window.attributes = lp
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return false
            }
            return false
        }
    }
}
