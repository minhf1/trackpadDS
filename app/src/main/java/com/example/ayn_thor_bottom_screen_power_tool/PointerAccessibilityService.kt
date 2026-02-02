package com.example.ayn_thor_bottom_screen_power_tool

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import kotlin.math.abs
import kotlin.math.sign

class PointerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: PointerAccessibilityService? = null
            private set
    }

    private var mirrorStroke: GestureDescription.StrokeDescription? = null
    private var mirrorLastX = 0f
    private var mirrorLastY = 0f
    private var mirrorLastEventMs = 0L
    private val mirrorHoldDurationMs = 10_000L
    private val mirrorMinSegmentMs = 4L
    private val mirrorMaxSegmentMs = 12L
    private val mirrorDispatchIntervalMs = 6L
    private var mirrorPendingX = 0f
    private var mirrorPendingY = 0f
    private var mirrorHasPending = false

    private val lastTopByDisplay = mutableMapOf<Int, ComponentName>()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        val cls = event.className?.toString()
        if (cls.isNullOrBlank()) return
        val component = ComponentName(pkg, cls)
        val displayId = getEventDisplayId(event)
        if (displayId >= 0) {
            lastTopByDisplay[displayId] = component
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun getLastTopComponent(displayId: Int): ComponentName? {
        return lastTopByDisplay[displayId]
    }

    fun swapByLaunchingApps(primaryId: Int, secondaryId: Int) {
        val primaryComp = lastTopByDisplay[primaryId]
        val secondaryComp = lastTopByDisplay[secondaryId]

        if (primaryComp != null && secondaryComp != null) {
            launchOnDisplay(primaryComp, secondaryId)
            launchOnDisplay(secondaryComp, primaryId)
        } else if (primaryComp != null) {
            launchOnDisplay(primaryComp, secondaryId)
        } else if (secondaryComp != null) {
            launchOnDisplay(secondaryComp, primaryId)
        }
    }

    private fun launchOnDisplay(component: ComponentName, displayId: Int) {
        val intent = Intent().setComponent(component)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        try {
            options.setLaunchDisplayId(displayId)
            startActivity(intent, options.toBundle())
        } catch (_: Throwable) {
            // If launch display is blocked, fall back to normal launch.
            try {
                startActivity(intent)
            } catch (_: Throwable) {
                // Last resort: try launcher intent if the activity is not exported.
                val fallback = packageManager.getLaunchIntentForPackage(component.packageName)
                fallback?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (fallback != null) startActivity(fallback)
            }
        }
    }

    private fun getEventDisplayId(event: AccessibilityEvent): Int {
        return try {
            val method = event.javaClass.getMethod("getDisplayId")
            (method.invoke(event) as? Int) ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    fun clickAt(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun rightClickAt(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 450)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun mirrorTouchDown(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }
        // Fix this function - bounding box not correct
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            mirrorHoldDurationMs,
            true
        )
        mirrorStroke = stroke
        mirrorLastX = x
        mirrorLastY = y
        mirrorLastEventMs = now
        mirrorHasPending = false
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun mirrorTouchMove(x: Float, y: Float) {
        val current = mirrorStroke ?: run {
            mirrorTouchDown(x, y)
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - mirrorLastEventMs < mirrorDispatchIntervalMs) {
            mirrorPendingX = x
            mirrorPendingY = y
            mirrorHasPending = true
            return
        }
        val endX = if (mirrorHasPending) mirrorPendingX else x
        val endY = if (mirrorHasPending) mirrorPendingY else y
        mirrorHasPending = false
        val duration = (now - mirrorLastEventMs)
            .coerceAtLeast(mirrorMinSegmentMs)
            .coerceAtMost(mirrorMaxSegmentMs)
        val path = Path().apply {
            moveTo(mirrorLastX, mirrorLastY)
            lineTo(endX, endY)
        }
        val next = current.continueStroke(path, 0, duration, true)
        mirrorStroke = next
        mirrorLastX = endX
        mirrorLastY = endY
        mirrorLastEventMs = now
        dispatchGesture(GestureDescription.Builder().addStroke(next).build(), null, null)
    }

    fun mirrorTouchUp(x: Float, y: Float) {
        val current = mirrorStroke ?: return
        val now = SystemClock.uptimeMillis()
        val endX = if (mirrorHasPending) mirrorPendingX else x
        val endY = if (mirrorHasPending) mirrorPendingY else y
        mirrorHasPending = false
        val duration = (now - mirrorLastEventMs)
            .coerceAtLeast(mirrorMinSegmentMs)
            .coerceAtMost(mirrorMaxSegmentMs)
        val path = Path().apply {
            moveTo(mirrorLastX, mirrorLastY)
            lineTo(endX, endY)
        }
        val next = current.continueStroke(path, 0, duration, false)
        mirrorStroke = null
        mirrorLastEventMs = 0L
        mirrorHasPending = false
        dispatchGesture(GestureDescription.Builder().addStroke(next).build(), null, null)
    }

    fun mirrorTouchCancel() {
        mirrorStroke = null
        mirrorLastEventMs = 0L
        mirrorHasPending = false
    }

    fun focusPrimaryBySwipe(displayW: Int, displayH: Int) {
        if (displayW <= 0 || displayH <= 0) return
        val startX = 6f
        val startY = (displayH - 8).toFloat().coerceIn(0f, (displayH - 1).toFloat())
        val endX = (startX + 8f).coerceIn(0f, (displayW - 1).toFloat())
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, startY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Scroll by synthesizing a swipe gesture centered at (x,y).
     * deltaY > 0 => scroll down (finger moves up); deltaY < 0 => scroll up (finger moves down).
     */
    fun scrollAt(
        x: Float,
        y: Float,
        deltaX: Float,
        deltaY: Float,
        displayW: Int,
        displayH: Int
    ) {
        val magnitudeX = (-deltaX).coerceIn(-800f, 800f)
        val magnitudeY = (-deltaY).coerceIn(-800f, 800f)
        if (abs(magnitudeX) < 4f && abs(magnitudeY) < 4f) return

        if (displayW <= 0 || displayH <= 0) return

        // For "scroll down", typical gesture is finger swipes up.
        val maxX = (displayW - 1).toFloat()
        val maxY = (displayH - 1).toFloat()
        val spanX = kotlin.math.abs(magnitudeX)
        val spanY = kotlin.math.abs(magnitudeY)

        val minSpan = 48f
        val safeMargin = 24f
        val safeX = x.coerceIn(safeMargin, maxX - safeMargin)
        val safeY = y.coerceIn(safeMargin, maxY - safeMargin)

        val desiredSpanX = if (spanX >= 4f) spanX.coerceAtLeast(minSpan) else 0f
        val desiredSpanY = if (spanY >= 4f) spanY.coerceAtLeast(minSpan) else 0f

        val rawStartX = safeX + (magnitudeX.sign * (desiredSpanX / 2f))
        val rawEndX = safeX - (magnitudeX.sign * (desiredSpanX / 2f))
        val rawStartY = safeY + (magnitudeY.sign * (desiredSpanY / 2f))
        val rawEndY = safeY - (magnitudeY.sign * (desiredSpanY / 2f))

        val clampedStartX = rawStartX.coerceIn(0f, maxX)
        val clampedEndX = rawEndX.coerceIn(0f, maxX)
        val startY = rawStartY.coerceIn(0f, maxY)
        val endY = rawEndY.coerceIn(0f, maxY)
        if (clampedStartX == clampedEndX && startY == endY) return

        val path = Path().apply {
            moveTo(clampedStartX, startY)
            lineTo(clampedEndX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 120)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
