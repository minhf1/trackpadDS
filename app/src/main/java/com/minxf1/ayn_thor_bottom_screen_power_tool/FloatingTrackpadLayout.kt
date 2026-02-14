package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class FloatingTrackpadLayout(
    activity: Activity
) : FrameLayout(activity) {

    // Build the trackpad card UI and wire dragging to the header.
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

    // dp.
    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).roundToInt()
    }

    // WindowDragTouchListener.
    private class WindowDragTouchListener(
        private val activity: Activity
    ) : OnTouchListener {
        private var lastRawX = 0f
        private var lastRawY = 0f

        // onTouch.
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
