package com.example.ayn_thor_bottom_screen_power_tool

import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class TrackpadView(ctx: android.content.Context) : View(ctx) {
    private val uiPrefs = ctx.getSharedPreferences("ui_config", android.content.Context.MODE_PRIVATE)
    private var lastX = 0f
    private var lastY = 0f

    private var isTwoFinger = false
    private var lastScrollX = 0f
    private var lastScrollY = 0f
    private var isScrolling = false
    private var scrollAccumulatorX = 0f
    private var scrollAccumulatorY = 0f
    private var filteredScrollX = 0f
    private var filteredScrollY = 0f
    private var hasTwoFingerGesture = false
    private var needsFocusSwipe = true

    // Tune this
    private val enableScrollSmoothing = true
    private val scrollSmoothing = 0.1f
    private val scrollThreshold = 1f

    // Tap detection
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var primaryPointerId: Int? = null
    private val pointerDownTime = mutableMapOf<Int, Long>()
    private val pointerDownX = mutableMapOf<Int, Float>()
    private val pointerDownY = mutableMapOf<Int, Float>()
    private var hasTwoFingerScroll = false

    private fun centroidY(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) {
            sum += e.getY(i)
        }
        return sum / e.pointerCount
    }

    private fun centroidX(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) {
            sum += e.getX(i)
        }
        return sum / e.pointerCount
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (PointerService.dragEnabled) return false
        val pointerCount = e.pointerCount

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isScrolling = false
                scrollAccumulatorX = 0f
                scrollAccumulatorY = 0f
                filteredScrollX = 0f
                filteredScrollY = 0f
                isTwoFinger = false
                hasTwoFingerGesture = false
                hasTwoFingerScroll = false

                if (needsFocusSwipe && uiPrefs.getBoolean("auto_focus_primary_on_touch", true)) {
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance
                        ?.focusPrimaryBySwipe(s.displayW, s.displayH)
                    needsFocusSwipe = false
                }

                lastX = e.x
                lastY = e.y
                downTime = e.eventTime
                downX = e.x
                downY = e.y
                primaryPointerId = e.getPointerId(0)
                primaryPointerId?.let { pointerId ->
                    pointerDownTime[pointerId] = e.eventTime
                    pointerDownX[pointerId] = e.x
                    pointerDownY[pointerId] = e.y
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val multiTouchClickEnabled =
                    uiPrefs.getBoolean("trackpad_click_multitouch", true)
                // After a pointer-up, MotionEvent still reports the old pointerCount
                // during this callback. Effective count will be pointerCount - 1.
                val remaining = e.pointerCount - 1
                val pointerId = e.getPointerId(e.actionIndex)
                if (multiTouchClickEnabled &&
                    remaining >= 1 &&
                    pointerId != primaryPointerId &&
                    !hasTwoFingerScroll) {
                    val dt = e.eventTime - (pointerDownTime[pointerId] ?: e.eventTime)
                    val dist =
                        abs(e.getX(e.actionIndex) - (pointerDownX[pointerId] ?: e.getX(e.actionIndex))) +
                            abs(e.getY(e.actionIndex) - (pointerDownY[pointerId] ?: e.getY(e.actionIndex)))
                    if (dt < 200 && dist < 20f) {
                        val s = PointerBus.get()
                        if (uiPrefs.getBoolean("haptic_trackpad_press", true)) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        PointerAccessibilityService.instance?.clickAt(s.x, s.y)
                    }
                }
                pointerDownTime.remove(pointerId)
                pointerDownX.remove(pointerId)
                pointerDownY.remove(pointerId)
                if (remaining < 2) {
                    isScrolling = false
                    scrollAccumulatorX = 0f
                    scrollAccumulatorY = 0f
                    filteredScrollX = 0f
                    filteredScrollY = 0f
                    isTwoFinger = false
                    hasTwoFingerScroll = false
                    if (remaining == 1) {
                        val remainingIndex = if (e.actionIndex == 0) 1 else 0
                        lastX = e.getX(remainingIndex)
                        lastY = e.getY(remainingIndex)
                        primaryPointerId = e.getPointerId(remainingIndex)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = e.getPointerId(e.actionIndex)
                pointerDownTime[pointerId] = e.eventTime
                pointerDownX[pointerId] = e.getX(e.actionIndex)
                pointerDownY[pointerId] = e.getY(e.actionIndex)
                if (e.pointerCount == 2) {
                    isTwoFinger = true
                    hasTwoFingerGesture = true
                    hasTwoFingerScroll = false

                    isScrolling = true
                    lastScrollX = centroidX(e)
                    lastScrollY = centroidY(e)
                    scrollAccumulatorX = 0f
                    scrollAccumulatorY = 0f
                    filteredScrollX = 0f
                    filteredScrollY = 0f
                    lastX = lastScrollX
                    lastY = lastScrollY
                } else if (e.pointerCount > 2) {
                    // still treat as two-finger gesture; keep scrolling mode
                    isTwoFinger = true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isScrolling && e.pointerCount >= 2) {
                    val x = centroidX(e)
                    val y = centroidY(e)
                    val dxRaw = x - lastScrollX
                    val dyRaw = y - lastScrollY
                    lastScrollX = x
                    lastScrollY = y

                    if (enableScrollSmoothing) {
                        filteredScrollX =
                            (filteredScrollX * (1f - scrollSmoothing)) + (dxRaw * scrollSmoothing)
                        filteredScrollY =
                            (filteredScrollY * (1f - scrollSmoothing)) + (dyRaw * scrollSmoothing)
                    } else {
                        filteredScrollX = dxRaw
                        filteredScrollY = dyRaw
                    }

                    // Accumulate to smooth jitter
                    scrollAccumulatorX += filteredScrollX
                    scrollAccumulatorY += filteredScrollY

                    // Threshold to avoid micro-scroll spam
                    if (kotlin.math.abs(scrollAccumulatorX) >= scrollThreshold ||
                        kotlin.math.abs(scrollAccumulatorY) >= scrollThreshold) {

                        // Treat scroll as vector (dx, dy)
                        val (mappedDx, mappedDy) =
                            RotationUtil.mapDeltaForRotation(
                                4,        // your hardcoded rotation
                                scrollAccumulatorX,
                                scrollAccumulatorY
                            )

                        val useHorizontal = kotlin.math.abs(mappedDx) >= kotlin.math.abs(mappedDy)
                        val scrollMultiplier =
                            uiPrefs.getInt("scroll_sensitivity", 40).coerceIn(1, 100).toFloat()
                        val scrollDeltaX = if (useHorizontal) mappedDx * scrollMultiplier else 0f
                        val scrollDeltaY = if (useHorizontal) 0f else mappedDy * scrollMultiplier

                        val s = PointerBus.get()
                        PointerAccessibilityService.instance
                            ?.scrollAt(s.x, s.y, scrollDeltaX, scrollDeltaY, s.displayW, s.displayH)
                        hasTwoFingerScroll = true
                        val dirX = when {
                            scrollDeltaX > 0f -> 1
                            scrollDeltaX < 0f -> -1
                            else -> 0
                        }
                        val dirY = when {
                            scrollDeltaY > 0f -> 1
                            scrollDeltaY < 0f -> -1
                            else -> 0
                        }
                        PointerBus.setScrollIndicator(dirX, dirY)

                        scrollAccumulatorX = 0f
                        scrollAccumulatorY = 0f
                    }
                    return true
                }

                val sensitivity =
                    uiPrefs.getFloat("cursor_sensitivity", 4.5f).coerceIn(0.5f, 6f)
                val dxRaw = (e.x - lastX) * sensitivity
                val dyRaw = (e.y - lastY) * sensitivity
                lastX = e.x
                lastY = e.y
//                    Log.d("Trackpad", "rotation=${display?.rotation}")
//                        val rotation = display?.rotation ?: 4
                val (dx, dy) = RotationUtil.mapDeltaForRotation(4, dxRaw, dyRaw)

                PointerBus.moveBy(dx, dy)
                return true

            }

            MotionEvent.ACTION_UP -> {
                // If it was a short, small movement => click
                val dt = e.eventTime - downTime
                val dist = abs(e.x - downX) + abs(e.y - downY)
                if (!isTwoFinger && !hasTwoFingerGesture && dt < 200 && dist < 20f) {
                    val s = PointerBus.get()
                    if (uiPrefs.getBoolean("haptic_trackpad_press", true)) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    PointerAccessibilityService.instance?.clickAt(s.x, s.y)
                }
                isScrolling = false
                scrollAccumulatorX = 0f
                scrollAccumulatorY = 0f
                filteredScrollX = 0f
                filteredScrollY = 0f
                isTwoFinger = false
                hasTwoFingerGesture = false
                hasTwoFingerScroll = false
                needsFocusSwipe = true
                primaryPointerId = null
                pointerDownTime.clear()
                pointerDownX.clear()
                pointerDownY.clear()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isScrolling = false
                scrollAccumulatorX = 0f
                scrollAccumulatorY = 0f
                filteredScrollX = 0f
                filteredScrollY = 0f
                isTwoFinger = false
                hasTwoFingerGesture = false
                hasTwoFingerScroll = false
                needsFocusSwipe = true
                primaryPointerId = null
                pointerDownTime.clear()
                pointerDownX.clear()
                pointerDownY.clear()
                return true
            }

        }

        return super.onTouchEvent(e)
    }

    private fun avgY(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getY(i)
        return sum / e.pointerCount
    }
}
