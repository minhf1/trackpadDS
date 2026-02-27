package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.content.Context
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
    private var isPrimaryHoldActive = false
    private var touchInputEnabled = true

    private fun readFloatPreferenceCompat(key: String, defaultValue: Float): Float {
        return when (val raw = uiPrefs.all[key]) {
            is Float -> raw
            is Int -> raw.toFloat()
            is Long -> raw.toFloat()
            is Double -> raw.toFloat()
            is String -> raw.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    fun setTouchInputEnabled(enabled: Boolean) {
        if (touchInputEnabled == enabled) return
        touchInputEnabled = enabled
        if (!enabled) {
            resetState()
        }
    }

    private fun endSecondaryGesture(commitUp: Boolean) {
        if (isSecondaryHoldActive) {
            val s = PointerBus.get()
            if (commitUp) {
                PointerAccessibilityService.instance?.touchHoldUp(s.x, s.y)
                if (hasScrollReturn) {
                    PointerBus.set(scrollReturnX, scrollReturnY)
                }
            } else {
                PointerAccessibilityService.instance?.touchHoldCancel()
            }
        }
        PointerBus.clearGhostCursor()
        secondaryPointerId = null
        isSecondaryHoldActive = false
        hasScrollReturn = false
    }

    private fun recenterCursorAfterAlwaysSwipeGestureIfNeeded(alwaysSwipeEnabled: Boolean) {
        if (!alwaysSwipeEnabled) return
        if (!uiPrefs.getBoolean("always_swipe_return_center_on_finish", true)) return
        val s = PointerBus.get()
        val centerX = ((s.displayW - 1).coerceAtLeast(0) / 2f)
        val centerY = ((s.displayH - 1).coerceAtLeast(0) / 2f)
        PointerBus.set(centerX, centerY)
    }

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
        // If a two-finger hold is still active, cancel it before clearing local state.
        endSecondaryGesture(commitUp = false)
        if (isPrimaryHoldActive) {
            PointerAccessibilityService.instance?.touchHoldCancel()
            recenterCursorAfterAlwaysSwipeGestureIfNeeded(
                alwaysSwipeEnabled = uiPrefs.getBoolean("always_swipe_enabled", false)
            )
            isPrimaryHoldActive = false
        }
        primaryLastX = 0f
        primaryLastY = 0f
        secondaryLastX = 0f
        secondaryLastY = 0f
        downTime = 0L
        downX = 0f
        downY = 0f
        primaryPointerId = null
        secondaryDownTime = 0L
        secondaryDownX = 0f
        secondaryDownY = 0f
        holdX = 0f
        holdY = 0f
        scrollReturnX = 0f
        scrollReturnY = 0f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!touchInputEnabled) return false
        val alwaysSwipeEnabled = uiPrefs.getBoolean("always_swipe_enabled", false)
        activePointerCount = when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> (e.pointerCount - 1).coerceAtLeast(0)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
            else -> e.pointerCount
        }

        if (activePointerCount !in 0..2) {
            // We only support 1-2 fingers; reset to avoid stuck pointer ids.
            resetState()
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
                if (alwaysSwipeEnabled) {
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance?.touchHoldDown(s.x, s.y)
                    isPrimaryHoldActive = true
                }
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
                    if (!alwaysSwipeEnabled) {
                        val s = PointerBus.get()
                        holdX = s.x
                        holdY = s.y
                        scrollReturnX = s.x
                        scrollReturnY = s.y
                        hasScrollReturn = true
                        PointerBus.setGhostCursor(scrollReturnX, scrollReturnY)
                        PointerAccessibilityService.instance?.touchHoldDown(s.x, s.y)
                        isSecondaryHoldActive = true
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (primaryPointerId == null && e.pointerCount > 0) {
                    // Recover if state was reset while a finger stayed down.
                    primaryPointerId = e.getPointerId(0)
                    primaryLastX = e.getX(0)
                    primaryLastY = e.getY(0)
                }
                val cursorSensitivity = readFloatPreferenceCompat("cursor_sensitivity", 4.5f)
                    .coerceIn(0.5f, 6f)
                var dxRawTotal = 0f
                var dyRawTotal = 0f

                val primaryId = primaryPointerId
                if (primaryId != null) {
                    val idx = e.findPointerIndex(primaryId)
                    if (idx >= 0) {
                        val x = e.getX(idx)
                        val y = e.getY(idx)
                        dxRawTotal += (x - primaryLastX) * cursorSensitivity
                        dyRawTotal += (y - primaryLastY) * cursorSensitivity
                        primaryLastX = x
                        primaryLastY = y
                    } else {
                        // Attempt to recover by re-binding to any available pointer.
                        if (e.pointerCount > 0) {
                            primaryPointerId = e.getPointerId(0)
                            primaryLastX = e.getX(0)
                            primaryLastY = e.getY(0)
                        } else {
                            resetState()
                            return true
                        }
                    }
                }

                val secondaryId = secondaryPointerId
                if (secondaryId != null) {
                    val idx = e.findPointerIndex(secondaryId)
                    if (idx >= 0) {
                        val x = e.getX(idx)
                        val y = e.getY(idx)
                        dxRawTotal += (x - secondaryLastX) * cursorSensitivity
                        dyRawTotal += (y - secondaryLastY) * cursorSensitivity
                        secondaryLastX = x
                        secondaryLastY = y
                    } else {
                        // Secondary pointer disappeared without a clean POINTER_UP.
                        endSecondaryGesture(commitUp = false)
                    }
                }

                if (dxRawTotal == 0f && dyRawTotal == 0f) {
                    return true
                }

                val (dx, dy) = RotationUtil.mapDeltaForRotation(4, dxRawTotal, dyRawTotal)
                PointerBus.moveBy(dx, dy)
                if (isPrimaryHoldActive) {
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance?.touchHoldMove(s.x, s.y)
                } else if (secondaryId != null) {
                    holdX += dx
                    holdY += dy
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance?.touchHoldMove(s.x, s.y)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val primaryHoldWasActive = isPrimaryHoldActive
                if (isSecondaryHoldActive) {
                    endSecondaryGesture(commitUp = true)
                }
                if (primaryHoldWasActive) {
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance?.touchHoldUp(s.x, s.y)
                    isPrimaryHoldActive = false
                    recenterCursorAfterAlwaysSwipeGestureIfNeeded(alwaysSwipeEnabled)
                } else if (isTapClick(e.eventTime, e.x, e.y, downTime, downX, downY)) {
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
                    endSecondaryGesture(commitUp = true)
                } else if (pointerId == primaryPointerId) {
                    val fallbackPointerId = secondaryPointerId
                    if (fallbackPointerId != null) {
                        endSecondaryGesture(commitUp = true)
                    }
                    primaryPointerId = fallbackPointerId
                    if (primaryPointerId != null) {
                        // Keep delta baseline stable when promoting secondary -> primary.
                        primaryLastX = secondaryLastX
                        primaryLastY = secondaryLastY
                        val nextIndex = e.findPointerIndex(primaryPointerId!!)
                        if (nextIndex >= 0) {
                            primaryLastX = e.getX(nextIndex)
                            primaryLastY = e.getY(nextIndex)
                        }
                    }
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
