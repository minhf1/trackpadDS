package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class TrackpadView(ctx: Context) : View(ctx) {
    private val uiPrefs = ctx.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var activePointerCount = 0

    override fun onTouchEvent(e: MotionEvent): Boolean {
        activePointerCount = when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> (e.pointerCount - 1).coerceAtLeast(0)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
            else -> e.pointerCount
        }

        if (activePointerCount <= 0 || activePointerCount >= 3) {
            return true
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                downTime = e.eventTime
                downX = e.x
                downY = e.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val sensitivity =
                    uiPrefs.getFloat("cursor_sensitivity", 4.5f).coerceIn(0.5f, 6f)
                val dxRaw = (e.x - lastX) * sensitivity
                val dyRaw = (e.y - lastY) * sensitivity
                lastX = e.x
                lastY = e.y
                val (dx, dy) = RotationUtil.mapDeltaForRotation(4, dxRaw, dyRaw)

                PointerBus.moveBy(dx, dy)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tapTimeoutMs = uiPrefs.getInt(
                    "trackpad_click_timeout_ms",
                    UiConstants.Sliders.TRACKPAD_CLICK_TIMEOUT_DEFAULT_MS
                )
                val tapDistancePx = uiPrefs.getInt(
                    "trackpad_click_distance_px",
                    UiConstants.Sliders.TRACKPAD_CLICK_DISTANCE_DEFAULT_PX
                ).toFloat()
                val dt = e.eventTime - downTime
                val dist = abs(e.x - downX) + abs(e.y - downY)
                if (dt < tapTimeoutMs && dist < tapDistancePx) {
                    val s = PointerBus.get()
                    if (uiPrefs.getBoolean("haptic_trackpad_press", true)) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    PointerAccessibilityService.instance?.clickAt(s.x, s.y)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                return true
            }
        }

        return super.onTouchEvent(e)
    }
}
