package com.example.ayn_thor_bottom_screen_power_tool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView

class MirrorActivity : Activity() {
    private var primaryW = 0
    private var primaryH = 0
    private val enableGestureExclusion = true
    private var lockEdgeX = false
    private var lockEdgeY = false
    private var lockedX = 0f
    private var lockedY = 0f
    private var mirrorDragEnabled = false
    private var mirrorDragButton: ImageButton? = null
    private var mirrorRenderClick = true
    private var lastRootW = 0
    private var lastRootH = 0

    // onCreate.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Resolve primary display metrics for touch mapping.
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val primary = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        if (primary != null) {
            val primaryCtx = createDisplayContext(primary)
            val primaryMetrics = primaryCtx.resources.displayMetrics
            primaryW = primaryMetrics.widthPixels
            primaryH = primaryMetrics.heightPixels
        } else {
            val primaryMetrics = resources.displayMetrics
            primaryW = primaryMetrics.widthPixels
            primaryH = primaryMetrics.heightPixels
        }

        // Root container and prefs that drive mirror behavior.
        val root = FrameLayout(this)
        val uiPrefs = getSharedPreferences(MirrorConstants.Prefs.UI, Context.MODE_PRIVATE)
        mirrorRenderClick = uiPrefs.getBoolean(MirrorConstants.Prefs.RENDER_CLICK, true)
        mirrorDragEnabled = getSharedPreferences(MirrorConstants.Prefs.MIRROR_POSITIONS, Context.MODE_PRIVATE)
            .getBoolean(MirrorConstants.Prefs.DRAG_ENABLED, false)
        // Cast container draws a border around the mirrored content.
        val castContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(MirrorConstants.Layout.CAST_BORDER_DP), UiConstants.Colors.CAST_BORDER)
            }
        }
        // SurfaceView hosts the mirrored frames and forwards touches to the service.
        val surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnTouchListener { _, event ->
                if (primaryW <= 0 || primaryH <= 0) return@setOnTouchListener false
                // Convert screen touch to mirrored content coordinates.
                val castRect = getCastRect(castContainer)
                val w = castRect.width().coerceAtLeast(1f)
                val h = castRect.height().coerceAtLeast(1f)
                val localX = event.rawX - castRect.left
                val localY = event.rawY - castRect.top
                val outsideX = localX < 0f || localX > w
                val outsideY = localY < 0f || localY > h
                val rawX = (localX / w) * primaryW.toFloat()
                val rawY = (localY / h) * primaryH.toFloat()
                var x = rawX.coerceIn(0f, (primaryW - 1).coerceAtLeast(0).toFloat())
                var y = rawY.coerceIn(0f, (primaryH - 1).coerceAtLeast(0).toFloat())
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Lock to edge when touch starts outside the cast area.
                        lockEdgeX = outsideX
                        lockEdgeY = outsideY
                        if (lockEdgeX) {
                            lockedX = if (localX < 0f) 0f else (primaryW - 1).coerceAtLeast(0).toFloat()
                        }
                        if (lockEdgeY) {
                            lockedY = if (localY < 0f) 0f else (primaryH - 1).coerceAtLeast(0).toFloat()
                        }
                        if (lockEdgeX) x = lockedX
                        if (lockEdgeY) y = lockedY
                        // Begin mirrored touch stream.
                        PointerAccessibilityService.instance?.mirrorTouchDown(x, y)
                        if (mirrorRenderClick) {
                            PointerService.instance?.updateMirrorTouch(x, y, true)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (lockEdgeX) x = lockedX
                        if (lockEdgeY) y = lockedY
                        // Continue mirrored touch stream.
                        PointerAccessibilityService.instance?.mirrorTouchMove(x, y)
                        if (mirrorRenderClick) {
                            PointerService.instance?.updateMirrorTouch(x, y, true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (lockEdgeX) x = lockedX
                        if (lockEdgeY) y = lockedY
                        // End mirrored touch stream.
                        PointerAccessibilityService.instance?.mirrorTouchUp(x, y)
                        if (mirrorRenderClick) {
                            PointerService.instance?.updateMirrorTouch(x, y, false)
                        }
                        lockEdgeX = false
                        lockEdgeY = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // Cancel mirrored touch stream.
                        PointerAccessibilityService.instance?.mirrorTouchCancel()
                        if (mirrorRenderClick) {
                            PointerService.instance?.updateMirrorTouch(x, y, false)
                        }
                        lockEdgeX = false
                        lockEdgeY = false
                        true
                    }
                    else -> false
                }
            }
        }

        castContainer.addView(surfaceView)
        root.addView(castContainer)
        setContentView(root)

        // Resolve which mirror controls should be visible.
        val showMirrorToggle = uiPrefs.getBoolean("mirror_show_toggle", true)
        val showBack = uiPrefs.getBoolean("mirror_show_back", true)
        val showHome = uiPrefs.getBoolean("mirror_show_home", true)
        val showRecents = uiPrefs.getBoolean("mirror_show_recents", true)
        val showDrag = uiPrefs.getBoolean("mirror_show_drag", true)

        if (showMirrorToggle) {
            val mirrorToggle = createMirrorButton(
                root,
                MirrorConstants.Keys.MIRROR_TOGGLE,
                R.drawable.ic_mirror,
                MirrorConstants.ButtonIndex.TOGGLE
            ) {
                finish()
            }
            root.addView(mirrorToggle)
        }
        if (showBack) {
            val backButton = createMirrorButton(
                root,
                MirrorConstants.Keys.MIRROR_BACK,
                R.drawable.ic_back,
                MirrorConstants.ButtonIndex.BACK
            ) {
                PointerAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                )
            }
            root.addView(backButton)
        }
        if (showHome) {
            val homeButton = createMirrorButton(
                root,
                MirrorConstants.Keys.MIRROR_HOME,
                R.drawable.ic_home,
                MirrorConstants.ButtonIndex.HOME
            ) {
                PointerAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                )
            }
            root.addView(homeButton)
        }
        if (showRecents) {
            val recentsButton = createMirrorButton(
                root,
                MirrorConstants.Keys.MIRROR_RECENTS,
                R.drawable.ic_menu,
                MirrorConstants.ButtonIndex.RECENTS
            ) {
                PointerAccessibilityService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                )
            }
            root.addView(recentsButton)
        }
        if (showDrag) {
            val dragButton = createMirrorButton(
                root,
                MirrorConstants.Keys.MIRROR_DRAG,
                R.drawable.ic_drag,
                MirrorConstants.ButtonIndex.DRAG
            ) {
                toggleMirrorDrag()
            }
            mirrorDragButton = dragButton
            root.addView(dragButton)
        }
        updateMirrorDragAppearance()

        // Attach/detach the mirror surface in the service.
        surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            // surfaceCreated.
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                PointerService.instance?.attachMirrorSurface(holder.surface)
            }
            // surfaceChanged.
            override fun surfaceChanged(
                holder: android.view.SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {}
            // surfaceDestroyed.
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                PointerService.instance?.detachMirrorSurface()
            }
        })

        // Recompute layout/exclusion when size changes.
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            maybeUpdateLayout(root, castContainer)
        }
        root.post { maybeUpdateLayout(root, castContainer) }

        if (enableGestureExclusion) {
            root.post { updateGestureExclusion(root) }
        }
    }

    // onBackPressed.
    override fun onBackPressed() {}

    // onDestroy.
    override fun onDestroy() {
        super.onDestroy()
        PointerAccessibilityService.instance?.mirrorTouchCancel()
        PointerService.instance?.detachMirrorSurface()
        stopMirrorService()
    }

    // stopMirrorService.
    private fun stopMirrorService() {
        val intent = Intent(this, PointerService::class.java).apply {
            action = MirrorConstants.Actions.STOP_MIRROR
        }
        startForegroundService(intent)
    }

    // maybeUpdateLayout.
    private fun maybeUpdateLayout(root: FrameLayout, castContainer: FrameLayout) {
        val w = root.width
        val h = root.height
        if (w <= 0 || h <= 0) return
        if (w == lastRootW && h == lastRootH) return
        lastRootW = w
        lastRootH = h
        updateCastLayout(root, castContainer)
        updateMirrorButtonDefaults(root)
        if (enableGestureExclusion) {
            updateGestureExclusion(root)
        }
    }

    // createMirrorButton.
    private fun createMirrorButton(
        root: FrameLayout,
        key: String,
        icon: Int,
        index: Int,
        action: () -> Unit
    ): ImageButton {
        val sizePx = getButtonSizePx()
        val uiPrefs = getSharedPreferences(MirrorConstants.Prefs.UI, Context.MODE_PRIVATE)
        val button = ImageButton(this).apply {
            tag = key
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            background = GradientDrawable().apply {
                cornerRadius = sizePx / 2f
                setColor(UiConstants.Colors.BUTTON_BG)
            }
            setImageResource(icon)
            setColorFilter(UiConstants.Colors.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener {
                if (uiPrefs.getBoolean(MirrorConstants.Prefs.HAPTIC_BUTTONS, true)) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
                action()
            }
        }
        applyMirrorButtonPosition(root, button, key, index)
        button.setOnTouchListener(
            MirrorButtonDragTouchListener(
                key,
                button,
                allowTapWhenDragEnabled = key == MirrorConstants.Keys.MIRROR_DRAG
            )
        )
        return button
    }

    // applyMirrorButtonPosition.
    private fun applyMirrorButtonPosition(
        root: FrameLayout,
        button: ImageButton,
        key: String,
        index: Int
    ) {
        val prefs = getSharedPreferences(MirrorConstants.Prefs.MIRROR_POSITIONS, Context.MODE_PRIVATE)
        val sizePx = getButtonSizePx()
        val gapPx = dp(MirrorConstants.Layout.BUTTON_GAP_DP)
        val margin = dp(MirrorConstants.Layout.BUTTON_MARGIN_DP)
        val defaultX = (root.width - sizePx - margin).coerceAtLeast(0)
        val defaultY = margin + index * (sizePx + gapPx)
        val x = prefs.getInt("${key}_x", defaultX)
        val y = prefs.getInt("${key}_y", defaultY)
        val lp = button.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = x
        lp.topMargin = y
        button.layoutParams = lp
    }

    // updateMirrorButtonDefaults.
    private fun updateMirrorButtonDefaults(root: FrameLayout) {
        for (i in 0 until root.childCount) {
            val view = root.getChildAt(i)
            val key = view.tag as? String ?: continue
            if (view is ImageButton) {
                val index = when (key) {
                    MirrorConstants.Keys.MIRROR_TOGGLE -> MirrorConstants.ButtonIndex.TOGGLE
                    MirrorConstants.Keys.MIRROR_BACK -> MirrorConstants.ButtonIndex.BACK
                    MirrorConstants.Keys.MIRROR_HOME -> MirrorConstants.ButtonIndex.HOME
                    MirrorConstants.Keys.MIRROR_RECENTS -> MirrorConstants.ButtonIndex.RECENTS
                    MirrorConstants.Keys.MIRROR_DRAG -> MirrorConstants.ButtonIndex.DRAG
                    else -> MirrorConstants.ButtonIndex.TOGGLE
                }
                applyMirrorButtonPosition(root, view, key, index)
            }
        }
    }

    // toggleMirrorDrag.
    private fun toggleMirrorDrag() {
        mirrorDragEnabled = !mirrorDragEnabled
        getSharedPreferences(MirrorConstants.Prefs.MIRROR_POSITIONS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MirrorConstants.Prefs.DRAG_ENABLED, mirrorDragEnabled)
            .apply()
        updateMirrorDragAppearance()
    }

    // updateMirrorDragAppearance.
    private fun updateMirrorDragAppearance() {
        val button = mirrorDragButton ?: return
        val bg = button.background as? GradientDrawable ?: return
        if (mirrorDragEnabled) {
            bg.setColor(UiConstants.Colors.BUTTON_BG_ACTIVE)
        } else {
            bg.setColor(UiConstants.Colors.BUTTON_BG)
        }
    }

    private inner class MirrorButtonDragTouchListener(
        private val key: String,
        private val view: ImageButton,
        private val allowTapWhenDragEnabled: Boolean
    ) : android.view.View.OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var touchSlop = 0

        // onTouch.
        override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
            val lp = view.layoutParams as FrameLayout.LayoutParams
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX
                    lastY = e.rawY
                    moved = false
                    if (touchSlop == 0) {
                        touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop
                    }
                    if (!mirrorDragEnabled) {
                        return allowTapWhenDragEnabled
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!mirrorDragEnabled) return allowTapWhenDragEnabled
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY
                    lastX = e.rawX
                    lastY = e.rawY
                    if (!moved &&
                        (kotlin.math.abs(dx) >= touchSlop || kotlin.math.abs(dy) >= touchSlop)) {
                        moved = true
                    }
                    if (moved) {
                        val maxX = (lastRootW - view.width).coerceAtLeast(0)
                        val maxY = (lastRootH - view.height).coerceAtLeast(0)
                        lp.leftMargin = (lp.leftMargin + dx).toInt().coerceIn(0, maxX)
                        lp.topMargin = (lp.topMargin + dy).toInt().coerceIn(0, maxY)
                        view.layoutParams = lp
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!mirrorDragEnabled) {
                        if (allowTapWhenDragEnabled) {
                            v.performClick()
                            return true
                        }
                        return false
                    }
                    if (!moved && allowTapWhenDragEnabled) {
                        v.performClick()
                        return true
                    }
                    if (moved) {
                        val prefs = getSharedPreferences(MirrorConstants.Prefs.MIRROR_POSITIONS, Context.MODE_PRIVATE)
                        prefs.edit()
                            .putInt("${key}_x", lp.leftMargin)
                            .putInt("${key}_y", lp.topMargin)
                            .apply()
                    }
                    return true
                }
            }
            return false
        }
    }

    // updateCastLayout.
    private fun updateCastLayout(root: FrameLayout, castContainer: FrameLayout) {
        val rootW = root.width
        val rootH = root.height
        if (rootW <= 0 || rootH <= 0 || primaryW <= 0 || primaryH <= 0) return
        var targetW = rootW
        var targetH = (rootW * primaryH.toFloat() / primaryW).toInt()
        if (targetH > rootH) {
            targetH = rootH
            targetW = (rootH * primaryW.toFloat() / primaryH).toInt()
        }
        val params = castContainer.layoutParams as FrameLayout.LayoutParams
        if (params.width != targetW || params.height != targetH) {
            params.width = targetW.coerceAtLeast(1)
            params.height = targetH.coerceAtLeast(1)
            castContainer.layoutParams = params
        }
    }

    // updateGestureExclusion.
    private fun updateGestureExclusion(root: FrameLayout) {
        val excludeWidth = dp(MirrorConstants.Layout.GESTURE_EXCLUSION_WIDTH_DP)
        val rect = android.graphics.Rect(
            root.width - excludeWidth,
            0,
            root.width,
            root.height
        )
        root.systemGestureExclusionRects = listOf(rect)
    }

    // getCastRect.
    private fun getCastRect(castContainer: FrameLayout): android.graphics.RectF {
        val location = IntArray(2)
        castContainer.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        return android.graphics.RectF(
            left,
            top,
            left + castContainer.width,
            top + castContainer.height
        )
    }

    // dp.
    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    // getButtonSizePx.
    private fun getButtonSizePx(): Int {
        val prefs = getSharedPreferences(MirrorConstants.Prefs.UI, Context.MODE_PRIVATE)
        val minPx = dp(UiConstants.Sizes.BUTTON_MIN)
        val maxPx = dp(UiConstants.Sizes.BUTTON_MAX)
        val defaultPx = dp(UiConstants.Sizes.BUTTON_DEFAULT)
        return prefs.getInt(MirrorConstants.Prefs.BUTTON_SIZE, defaultPx).coerceIn(minPx, maxPx)
    }
}
