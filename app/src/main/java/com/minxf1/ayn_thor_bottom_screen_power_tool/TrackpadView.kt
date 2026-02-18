package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.minxf1.ayn_thor_bottom_screen_power_tool.UiConstants.Sliders.SCROLL_SENSITIVITY_DEFAULT
import com.minxf1.ayn_thor_bottom_screen_power_tool.UiConstants.Sliders.SCROLL_SENSITIVITY_MAX
import com.minxf1.ayn_thor_bottom_screen_power_tool.UiConstants.Sliders.SCROLL_SENSITIVITY_MIN
import kotlin.math.abs

class TrackpadView(ctx: Context) : View(ctx) {
    private val logTag = "TrackpadView"
    private val uiPrefs = ctx.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
    private var primaryLastX = 0f
    private var primaryLastY = 0f
    private var secondaryLastX = 0f
    private var secondaryLastY = 0f
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var activePointerCount = 0
    private var primaryPointerId: Int? = null
    private var secondaryPointerId: Int? = null
    private var secondaryDownTime = 0L
    private var secondaryDownX = 0f
    private var secondaryDownY = 0f
    private var isSecondaryHoldActive = false
    private var holdX = 0f
    private var holdY = 0f
    private var scrollReturnX = 0f
    private var scrollReturnY = 0f
    private var hasScrollReturn = false

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
        primaryLastX = 0f
        primaryLastY = 0f
        secondaryLastX = 0f
        secondaryLastY = 0f
        downTime = 0L
        downX = 0f
        downY = 0f
        primaryPointerId = null
        secondaryPointerId = null
        secondaryDownTime = 0L
        secondaryDownX = 0f
        secondaryDownY = 0f
        isSecondaryHoldActive = false
        holdX = 0f
        holdY = 0f
        scrollReturnX = 0f
        scrollReturnY = 0f
        hasScrollReturn = false
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
                primaryLastX = e.x
                primaryLastY = e.y
                downTime = e.eventTime
                downX = e.x
                downY = e.y
                primaryPointerId = e.getPointerId(0)
                secondaryPointerId = null
                Log.d(
                    logTag,
                    "DOWN pc=${e.pointerCount} pid0=$primaryPointerId x=${e.x} y=${e.y}"
                )
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerCount == 2 && secondaryPointerId == null) {
                    val pointerId = e.getPointerId(e.actionIndex)
                    secondaryPointerId = pointerId
                    secondaryDownTime = e.eventTime
                    secondaryDownX = e.getX(e.actionIndex)
                    secondaryDownY = e.getY(e.actionIndex)
                    secondaryLastX = secondaryDownX
                    secondaryLastY = secondaryDownY
                    val s = PointerBus.get()
                    holdX = s.x
                    holdY = s.y
                    scrollReturnX = s.x
                    scrollReturnY = s.y
                    hasScrollReturn = true
                    PointerAccessibilityService.instance?.touchHoldDown(s.x, s.y)
                    isSecondaryHoldActive = true
                    Log.d(
                        logTag,
                        "POINTER_DOWN pc=${e.pointerCount} actionIdx=${e.actionIndex} " +
                            "pid=$pointerId sx=$secondaryDownX sy=$secondaryDownY hold=($holdX,$holdY)"
                    )
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val controlId = if (isSecondaryHoldActive) {
                    secondaryPointerId
                } else {
                    primaryPointerId
                } ?: return true
                val controlIndex = e.findPointerIndex(controlId)
                if (controlIndex < 0) {
                    Log.d(
                        logTag,
                        "MOVE no index controlId=$controlId pc=${e.pointerCount} " +
                            "primaryId=$primaryPointerId secondaryId=$secondaryPointerId " +
                            "secHold=$isSecondaryHoldActive"
                    )
                    return true
                }
                val sensitivity = if (isSecondaryHoldActive) {
                    uiPrefs.getFloat("scroll_sensitivity", SCROLL_SENSITIVITY_DEFAULT).coerceIn(
                        SCROLL_SENSITIVITY_MIN, SCROLL_SENSITIVITY_MAX
                    )
                } else {
                    uiPrefs.getFloat("cursor_sensitivity", 4.5f).coerceIn(0.5f, 6f)
                }
                val x = e.getX(controlIndex)
                val y = e.getY(controlIndex)
                val (lastXRef, lastYRef) = if (isSecondaryHoldActive) {
                    Pair(secondaryLastX, secondaryLastY)
                } else {
                    Pair(primaryLastX, primaryLastY)
                }
                val dxRaw = (x - lastXRef) * sensitivity
                val dyRaw = (y - lastYRef) * sensitivity
                if (isSecondaryHoldActive) {
                    secondaryLastX = x
                    secondaryLastY = y
                } else {
                    primaryLastX = x
                    primaryLastY = y
                }
                val (dx, dy) = RotationUtil.mapDeltaForRotation(4, dxRaw, dyRaw)
                PointerBus.moveBy(dx, dy)
                val secondaryId = secondaryPointerId
                if (secondaryId != null) {
                    holdX += dx
                    holdY += dy
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance?.touchHoldMove(s.x, s.y)
                }
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
                Log.d(
                    logTag,
                    "UP pc=${e.pointerCount} primaryId=$primaryPointerId secondaryId=$secondaryPointerId"
                )
                resetState()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = e.getPointerId(e.actionIndex)
                if (pointerId == secondaryPointerId) {
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance?.touchHoldUp(s.x, s.y)
                    if (hasScrollReturn) {
                        PointerBus.set(scrollReturnX, scrollReturnY)
                    }
                    secondaryPointerId = null
                    isSecondaryHoldActive = false
                    hasScrollReturn = false
                    Log.d(
                        logTag,
                        "POINTER_UP secondary pid=$pointerId pc=${e.pointerCount} hold=($holdX,$holdY)"
                    )
                } else if (pointerId == primaryPointerId) {
                    primaryPointerId = secondaryPointerId
                    if (primaryPointerId != null) {
                        val nextIndex = e.findPointerIndex(primaryPointerId!!)
                        if (nextIndex >= 0) {
                            primaryLastX = e.getX(nextIndex)
                            primaryLastY = e.getY(nextIndex)
                        }
                    }
                    if (primaryPointerId == secondaryPointerId) {
                        primaryLastX = secondaryLastX
                        primaryLastY = secondaryLastY
                    }
                    secondaryPointerId = null
                    Log.d(
                        logTag,
                        "POINTER_UP primary pid=$pointerId pc=${e.pointerCount} newPrimary=$primaryPointerId"
                    )
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
