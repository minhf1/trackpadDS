package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import kotlin.math.abs
import kotlin.math.sign

class PointerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: PointerAccessibilityService? = null
            private set
    }

    private var holdStroke: GestureDescription.StrokeDescription? = null
    private val uiPrefs: SharedPreferences by lazy {
        getSharedPreferences("ui_config", MODE_PRIVATE)
    }
    private var holdLastX = 0f
    private var holdLastY = 0f
    private var holdLastEventMs = 0L
    private var holdStartMs = 0L
    private val holdDurationMs = 60_000L
    private val holdMinSegmentMs = 8L
    private val holdMaxSegmentMs = 20L
    private val holdDispatchIntervalMs = 20L
    private val holdStartDelayMs = 50L
    private var holdPendingX = 0f
    private var holdPendingY = 0f
    private var holdHasPending = false
    private var holdRebaseInProgress = false

    private val lastTopByDisplay = mutableMapOf<Int, ComponentName>()
    private var gestureSeq = 0

    private fun dispatchGestureLogged(
        tag: String,
        gesture: GestureDescription,
        onCompleted: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        val seq = ++gestureSeq
        val ok = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onCompleted?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w("PointerAS", "gesture cancelled tag=$tag seq=$seq")
                    if (tag.startsWith("hold")) {
                        onHoldGestureCancelled()
                    }
                    onCancelled?.invoke()
                }
            },
            null
        )
        if (!ok) {
            Log.w("PointerAS", "gesture dispatch failed tag=$tag seq=$seq")
            if (tag.startsWith("hold")) {
                onHoldGestureCancelled()
            }
            onCancelled?.invoke()
        }
    }

    private fun onHoldGestureCancelled() {
        // Clear hold state to stop spamming holdMove/Up after a rejection.
        touchHoldCancel()
    }

    private fun clampToDisplay(x: Float, y: Float): Pair<Float, Float> {
        val s = PointerBus.get()
        val maxX = (s.displayW - 1).coerceAtLeast(0).toFloat()
        val maxY = (s.displayH - 1).coerceAtLeast(0).toFloat()
        return Pair(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
    }

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
        if (holdStroke != null) return
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGestureLogged("click", gesture)
    }

    fun rightClickAt(x: Float, y: Float) {
        if (holdStroke != null) return
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 450)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGestureLogged("rightClick", gesture)
    }

    fun touchHoldDown(x: Float, y: Float) {
        val (cx, cy) = clampToDisplay(x, y)
        val now = SystemClock.uptimeMillis()
        val (nx, ny) = clampToDisplay(cx + 0.1f, cy + 0.1f)
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(nx, ny)
        }
        // Fix this function - bounding box not correct
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            holdDurationMs,
            true
        )
        holdStroke = stroke
        holdLastX = cx
        holdLastY = cy
        holdLastEventMs = now
        holdStartMs = now
        holdHasPending = false
        holdRebaseInProgress = false
        dispatchGestureLogged("holdDown", GestureDescription.Builder().addStroke(stroke).build())
    }

    fun touchHoldMove(x: Float, y: Float) {
        val (cx, cy) = clampToDisplay(x, y)
        val current = holdStroke ?: run {
            touchHoldDown(cx, cy)
            return
        }
        if (holdRebaseInProgress) return
        val now = SystemClock.uptimeMillis()
        if (now - holdStartMs < holdStartDelayMs) {
            holdPendingX = cx
            holdPendingY = cy
            holdHasPending = true
            return
        }
        if (now - holdLastEventMs < holdDispatchIntervalMs) {
            holdPendingX = cx
            holdPendingY = cy
            holdHasPending = true
            return
        }
        val endX = if (holdHasPending) holdPendingX else cx
        val endY = if (holdHasPending) holdPendingY else cy
        holdHasPending = false
        val duration = (now - holdLastEventMs)
            .coerceAtLeast(holdMinSegmentMs)
            .coerceAtMost(holdMaxSegmentMs)
        val path = Path().apply {
            moveTo(holdLastX, holdLastY)
            lineTo(endX, endY)
        }
        val next = current.continueStroke(path, 0, duration, true)
        holdStroke = next
        holdLastX = endX
        holdLastY = endY
        holdLastEventMs = now
        dispatchGestureLogged("holdMove", GestureDescription.Builder().addStroke(next).build())
    }

    fun touchHoldUp(x: Float, y: Float) {
        val (cx, cy) = clampToDisplay(x, y)
        val current = holdStroke ?: return
        if (holdRebaseInProgress) return
        val now = SystemClock.uptimeMillis()
        val endX = if (holdHasPending) holdPendingX else cx
        val endY = if (holdHasPending) holdPendingY else cy
        holdHasPending = false
        val duration = (now - holdLastEventMs)
            .coerceAtLeast(holdMinSegmentMs)
            .coerceAtMost(holdMaxSegmentMs)
        val path = Path().apply {
            moveTo(holdLastX, holdLastY)
            lineTo(endX, endY)
        }
        val next = current.continueStroke(path, 0, duration, false)
        holdStroke = null
        holdLastEventMs = 0L
        holdStartMs = 0L
        holdHasPending = false
        holdRebaseInProgress = false
        dispatchGestureLogged("holdUp", GestureDescription.Builder().addStroke(next).build())
    }

    fun touchHoldRebase(x: Float, y: Float) {
        val (targetX, targetY) = clampToDisplay(x, y)
        val current = holdStroke ?: run {
            touchHoldDown(targetX, targetY)
            return
        }
        if (holdRebaseInProgress) return
        holdRebaseInProgress = true
        val now = SystemClock.uptimeMillis()
        val duration = (now - holdLastEventMs)
            .coerceAtLeast(holdMinSegmentMs)
            .coerceAtMost(holdMaxSegmentMs)
        val (nx, ny) = clampToDisplay(holdLastX + 0.1f, holdLastY + 0.1f)
        val path = Path().apply {
            moveTo(holdLastX, holdLastY)
            lineTo(nx, ny)
        }
        val endStroke = current.continueStroke(path, 0, duration, false)
        holdStroke = null
        holdLastEventMs = 0L
        holdStartMs = 0L
        holdHasPending = false
        dispatchGestureLogged(
            "holdRebaseUp",
            GestureDescription.Builder().addStroke(endStroke).build(),
            onCompleted = {
                touchHoldDown(targetX, targetY)
            },
            onCancelled = {
                holdRebaseInProgress = false
            }
        )
    }

    fun touchHoldCancel() {
        holdStroke = null
        holdLastEventMs = 0L
        holdStartMs = 0L
        holdHasPending = false
        holdRebaseInProgress = false
    }

    fun focusPrimaryBySwipe(displayW: Int, displayH: Int) {
        if (holdStroke != null) return
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
        dispatchGestureLogged("focusSwipe", gesture)
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
        if (holdStroke != null) return
        val magnitudeX = (-deltaX).coerceIn(-800f, 800f)
        val magnitudeY = (-deltaY).coerceIn(-800f, 800f)
        if (abs(magnitudeX) < 4f && abs(magnitudeY) < 4f) return

        if (displayW <= 0 || displayH <= 0) return

        // For "scroll down", typical gesture is finger swipes up.
        val maxX = (displayW - 1).toFloat()
        val maxY = (displayH - 1).toFloat()
        val spanX = abs(magnitudeX)
        val spanY = abs(magnitudeY)

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
        dispatchGestureLogged("scroll", gesture)
    }

    fun zoomInAt(x: Float, y: Float, displayW: Int, displayH: Int) {
        dispatchPinchGesture(x, y, displayW, displayH, zoomIn = true)
    }

    fun zoomOutAt(x: Float, y: Float, displayW: Int, displayH: Int) {
        dispatchPinchGesture(x, y, displayW, displayH, zoomIn = false)
    }

    private fun dispatchPinchGesture(
        x: Float,
        y: Float,
        displayW: Int,
        displayH: Int,
        zoomIn: Boolean
    ) {
        if (holdStroke != null) return
        if (displayW <= 0 || displayH <= 0) return

        val maxX = (displayW - 1).toFloat()
        val maxY = (displayH - 1).toFloat()
        val safeMargin = 28f
        val cx = x.coerceIn(safeMargin, maxX - safeMargin)
        val cy = y.coerceIn(safeMargin, maxY - safeMargin)

        val minDim = minOf(displayW, displayH).toFloat()
        val strength = uiPrefs
            .getInt("zoom_gesture_strength_pct", 100)
            .coerceIn(50, 200) / 100f
        val durationMs = uiPrefs
            .getInt("zoom_gesture_duration_ms", 220)
            .coerceIn(80, 600)
            .toLong()

        val smallRadius = ((minDim * 0.05f) * strength).coerceIn(22f, 96f)
        val largeRadius = ((minDim * 0.14f) * strength).coerceIn(60f, 240f)
        val startRadius = if (zoomIn) smallRadius else largeRadius
        val endRadius = if (zoomIn) largeRadius else smallRadius

        val leftStartX = (cx - startRadius).coerceIn(0f, maxX)
        val rightStartX = (cx + startRadius).coerceIn(0f, maxX)
        val leftEndX = (cx - endRadius).coerceIn(0f, maxX)
        val rightEndX = (cx + endRadius).coerceIn(0f, maxX)
        if (leftStartX == leftEndX && rightStartX == rightEndX) return

        val leftPath = Path().apply {
            moveTo(leftStartX, cy)
            lineTo(leftEndX, cy)
        }
        val rightPath = Path().apply {
            moveTo(rightStartX, cy)
            lineTo(rightEndX, cy)
        }

        val leftStroke = GestureDescription.StrokeDescription(leftPath, 0, durationMs)
        val rightStroke = GestureDescription.StrokeDescription(rightPath, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(leftStroke)
            .addStroke(rightStroke)
            .build()
        dispatchGestureLogged(if (zoomIn) "zoomIn" else "zoomOut", gesture)
    }
}

