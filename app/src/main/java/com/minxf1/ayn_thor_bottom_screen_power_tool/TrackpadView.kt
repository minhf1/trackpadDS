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
    private var primaryPointerId: Int? = null
    private var secondaryPointerId: Int? = null
    private var secondaryDownTime = 0L
    private var secondaryDownX = 0f
    private var secondaryDownY = 0f

    private fun isTapClick(
        upTime: Long,
        upX: Float,
        upY: Float,
        startTime: Long,
        startX: Float,
        startY: Float
    ): Boolean {
        val tapTimeoutMs = uiPrefs.getInt(
            "trackpad_click_timeout_ms",
            UiConstants.Sliders.TRACKPAD_CLICK_TIMEOUT_DEFAULT_MS
        )
        val tapDistancePx = uiPrefs.getInt(
            "trackpad_click_distance_px",
            UiConstants.Sliders.TRACKPAD_CLICK_DISTANCE_DEFAULT_PX
        ).toFloat()
        val dt = upTime - startTime
        val dist = abs(upX - startX) + abs(upY - startY)
        return dt < tapTimeoutMs && dist < tapDistancePx
    }

    private fun resetState() {
        lastX = 0f
        lastY = 0f
        downTime = 0L
        downX = 0f
        downY = 0f
        primaryPointerId = null
        secondaryPointerId = null
        secondaryDownTime = 0L
        secondaryDownX = 0f
        secondaryDownY = 0f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        activePointerCount = when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> (e.pointerCount - 1).coerceAtLeast(0)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
            else -> e.pointerCount
        }

        if (activePointerCount !in 0..2) {
            return true
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                downTime = e.eventTime
                downX = e.x
                downY = e.y
                primaryPointerId = e.getPointerId(0)
                secondaryPointerId = null
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerCount == 2 && secondaryPointerId == null) {
                    val pointerId = e.getPointerId(e.actionIndex)
                    secondaryPointerId = pointerId
                    secondaryDownTime = e.eventTime
                    secondaryDownX = e.getX(e.actionIndex)
                    secondaryDownY = e.getY(e.actionIndex)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val primaryId = primaryPointerId ?: return true
                val primaryIndex = e.findPointerIndex(primaryId)
                if (primaryIndex < 0) {
                    return true
                }
                val sensitivity =
                    uiPrefs.getFloat("cursor_sensitivity", 4.5f).coerceIn(0.5f, 6f)
                val x = e.getX(primaryIndex)
                val y = e.getY(primaryIndex)
                val dxRaw = (x - lastX) * sensitivity
                val dyRaw = (y - lastY) * sensitivity
                lastX = x
                lastY = y
                val (dx, dy) = RotationUtil.mapDeltaForRotation(4, dxRaw, dyRaw)

                PointerBus.moveBy(dx, dy)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isTapClick(e.eventTime, e.x, e.y, downTime, downX, downY)) {
                    val s = PointerBus.get()
                    if (uiPrefs.getBoolean("haptic_trackpad_press", true)) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    PointerAccessibilityService.instance?.clickAt(s.x, s.y)
                }
                resetState()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = e.getPointerId(e.actionIndex)
                if (pointerId == secondaryPointerId) {
                    val multiTouchClickEnabled =
                        uiPrefs.getBoolean("trackpad_click_multitouch", true)
                    if (multiTouchClickEnabled && isTapClick(
                            e.eventTime,
                            e.getX(e.actionIndex),
                            e.getY(e.actionIndex),
                            secondaryDownTime,
                            secondaryDownX,
                            secondaryDownY
                        )) {
                        val s = PointerBus.get()
                        if (uiPrefs.getBoolean("haptic_trackpad_press", true)) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        PointerAccessibilityService.instance?.clickAt(s.x, s.y)
                    }
                    secondaryPointerId = null
                } else if (pointerId == primaryPointerId) {
                    primaryPointerId = secondaryPointerId
                    if (primaryPointerId != null) {
                        val nextIndex = e.findPointerIndex(primaryPointerId!!)
                        if (nextIndex >= 0) {
                            lastX = e.getX(nextIndex)
                            lastY = e.getY(nextIndex)
                        }
                    }
                    secondaryPointerId = null
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetState()
                return true
            }
        }

        return super.onTouchEvent(e)
    }
}
