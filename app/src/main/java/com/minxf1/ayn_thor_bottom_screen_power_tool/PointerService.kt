package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import android.os.IBinder
import android.os.SystemClock
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt
import android.app.*
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.concurrent.fixedRateTimer
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.content.IntentCompat
import java.lang.ref.WeakReference
import androidx.core.content.edit
import com.example.ayn_thor_bottom_screen_power_tool.R
import java.util.Timer
import kotlin.math.abs

class PointerService : Service() {
    private var screenReceiver: BroadcastReceiver? = null
    companion object {
        @Volatile var dragEnabled: Boolean = false
            private set
        @Volatile private var instanceRef: WeakReference<PointerService>? = null
        val instance: PointerService?
            get() = instanceRef?.get()
    }
    private var cursorWm: WindowManager? = null
    private var cursorLp: WindowManager.LayoutParams? = null
    private var cursorTimer: Timer? = null
    private var cursorView: View? = null
    private var ghostCursorView: View? = null
    private var ghostCursorLp: WindowManager.LayoutParams? = null
    private var cursorSizePx = 0
    private var cursorBaseAlpha = PointerConstants.Alpha.CURSOR
    private var lastCursorX = 0
    private var lastCursorY = 0
    private var lastCursorMoveMs = 0L
    private var cursorFadedOut = false

    private var padWm: WindowManager? = null
    private var padViewA: View? = null
    private var padViewB: View? = null
    private var padLpA: WindowManager.LayoutParams? = null
    private var padLpB: WindowManager.LayoutParams? = null
    private var backView: ImageButton? = null
    private var homeView: ImageButton? = null
    private var recentsView: ImageButton? = null
    private var closeView: ImageButton? = null
    private var dragToggleView: ImageButton? = null
    private var navToggleView: ImageButton? = null
    private var hideToggleView: ImageButton? = null
    private var swapView: ImageButton? = null
    private var lightToggleView: ImageButton? = null
    private var mirrorToggleView: ImageButton? = null
    private var clickView: ImageButton? = null
    private var rightClickView: ImageButton? = null
    private var backLp: WindowManager.LayoutParams? = null
    private var homeLp: WindowManager.LayoutParams? = null
    private var recentsLp: WindowManager.LayoutParams? = null
    private var closeLp: WindowManager.LayoutParams? = null
    private var dragToggleLp: WindowManager.LayoutParams? = null
    private var navToggleLp: WindowManager.LayoutParams? = null
    private var hideToggleLp: WindowManager.LayoutParams? = null
    private var swapLp: WindowManager.LayoutParams? = null
    private var lightToggleLp: WindowManager.LayoutParams? = null
    private var mirrorToggleLp: WindowManager.LayoutParams? = null
    private var clickLp: WindowManager.LayoutParams? = null
    private var rightClickLp: WindowManager.LayoutParams? = null
    private var dragModeEnabled = false
    private var showNavButtons = true
    private var hideOverlays = false
    private var clickThroughEnabled = false
    private var lightOverlayEnabled = false
    private var lightOffKeepControls = false
    private var lightOffPrimaryButton = false
    private var lightOffHideBottomButton = false
    private val uiPrefs by lazy {
        getSharedPreferences("ui_config", MODE_PRIVATE)
    }
    private val positionPrefs by lazy {
        getSharedPreferences("floating_positions", MODE_PRIVATE)
    }
    private val uiPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            applyUiConfig()
        }
    private val positionPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            applyOverlayPositions()
        }
    private var buttonOpacity = 100
    private var trackpadOpacity = 100
    private val trackpadCards = mutableListOf<View>()
    private val trackpadSurfaces = mutableListOf<View>()
    private val trackpadResizeHandles = mutableListOf<View>()
    private val trackpadLabels = mutableListOf<View>()
    private val trackpadHeaderRows = mutableListOf<View>()
    private val trackpadHeaderButtons = mutableListOf<ImageButton>()
    private val floatingButtons = mutableListOf<View>()
    private var mirrorActive = false
    private var mirrorProjection: MediaProjection? = null
    private var mirrorDisplay: VirtualDisplay? = null
    private var mirrorPrimaryW = 0
    private var mirrorPrimaryH = 0
    private var mirrorPrimaryDensity = 0
    private var mirrorTouchView: View? = null
    private var mirrorTouchLp: WindowManager.LayoutParams? = null
    private var mirrorTouchWm: WindowManager? = null
    private val mirrorTouchHandler = Handler(Looper.getMainLooper())
    private val mirrorTouchHide = Runnable { hideMirrorTouch() }
    private var mirrorClickView: View? = null
    private var mirrorClickLp: WindowManager.LayoutParams? = null
    private var lightOverlayView: View? = null
    private var lightOverlayLp: WindowManager.LayoutParams? = null
    private var lightPrimaryView: ImageButton? = null
    private var lightPrimaryLp: WindowManager.LayoutParams? = null
    private var lightPrimaryWm: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // onCreate.
    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)

        // startForeground(...) with type as you already fixed
        val notif = buildNotification()
        startForeground(
            PointerConstants.Notification.ID,
            notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        if (!uiPrefs.contains("nav_buttons_enabled")) {
            uiPrefs.edit { putBoolean("nav_buttons_enabled", true) }
        }
        uiPrefs.edit { putBoolean("overlay_running", true) }

        attachCursorOverlayToPrimary()
        attachTrackpadOverlayToSecondary()  // <-- new
        registerScreenReceiver()
        uiPrefs.registerOnSharedPreferenceChangeListener(uiPrefListener)
        positionPrefs.registerOnSharedPreferenceChangeListener(positionPrefListener)
    }

    // onDestroy.
    override fun onDestroy() {
        instanceRef = null
        uiPrefs.unregisterOnSharedPreferenceChangeListener(uiPrefListener)
        positionPrefs.unregisterOnSharedPreferenceChangeListener(positionPrefListener)
        unregisterScreenReceiver()
        stopMirrorMode(restartOverlays = false)
        detachTrackpad()
        detachTrackpadOverlay()
        detachCursor()
        stopForeground(STOP_FOREGROUND_REMOVE)
        uiPrefs.edit { putBoolean("overlay_running", false) }
        super.onDestroy()
    }

    // onStartCommand.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PointerConstants.Actions.STOP_OVERLAY) {
            stopOverlayAndSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == PointerConstants.Actions.START_MIRROR) {
            val resultCode = intent.getIntExtra(
                PointerConstants.Extras.PROJECTION_RESULT_CODE,
                Activity.RESULT_CANCELED
            )
            val data = IntentCompat.getParcelableExtra(
                intent,
                PointerConstants.Extras.PROJECTION_DATA,
                Intent::class.java
            )
            if (resultCode == Activity.RESULT_OK && data != null) {
                startMirrorMode(resultCode, data)
            }
        } else if (intent?.action == PointerConstants.Actions.STOP_MIRROR) {
            stopMirrorMode()
        }
        return START_STICKY
    }
    // onBind.
    override fun onBind(intent: Intent?): IBinder? = null

    // attachCursorOverlayToPrimary.
    private fun attachCursorOverlayToPrimary() {
        if (cursorView != null) return
        // Ensure we target primary display for overlay
        val displayCtx = getPrimaryDisplayContext()

        cursorWm = displayCtx.getSystemService(WINDOW_SERVICE) as WindowManager
        ensureLightPrimaryButton(displayCtx)

        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val primary = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val (w, h) = getRealBoundsOrFallback(primary, displayCtx)
        PointerBus.setDisplaySize(w, h)

        val metrics = displayCtx.resources.displayMetrics
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            PointerConstants.Sizes.CURSOR_DP,
            metrics
        ).toInt()
        cursorSizePx = sizePx

        ghostCursorView = CursorDotView(displayCtx, sizePx)
        ghostCursorLp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        cursorView = CursorDotView(displayCtx, sizePx)
        updateCursorAppearance()
        cursorLp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val half = sizePx / 2f
            x = (PointerBus.get().x - half).toInt()
            y = (PointerBus.get().y - half).toInt()
        }

        safeAddView(cursorWm, cursorView, cursorLp)
        lastCursorX = cursorLp?.x ?: 0
        lastCursorY = cursorLp?.y ?: 0
        lastCursorMoveMs = SystemClock.uptimeMillis()
        cursorFadedOut = false

        // Update overlay position at ~60Hz
        cursorTimer = fixedRateTimer(
            "cursor",
            initialDelay = 0L,
            period = PointerConstants.Timing.CURSOR_TIMER_PERIOD_MS
        ) {
            val s = PointerBus.get()
            val lp = cursorLp ?: return@fixedRateTimer
            val half = cursorSizePx / 2f
            lp.x = (s.x - half).toInt()
            lp.y = (s.y - half).toInt()
            try {
                // Must run on main thread
                mainHandler.post {
                    val now = SystemClock.uptimeMillis()
                    val indicator = PointerBus.getScrollIndicator()
                    val scrollRecent = now - indicator.atMs <=
                        PointerConstants.Timing.SCROLL_INDICATOR_WINDOW_MS
                    val moved = (lp.x != lastCursorX || lp.y != lastCursorY)
                    if (moved) {
                        lastCursorX = lp.x
                        lastCursorY = lp.y
                        lastCursorMoveMs = now
                        if (cursorFadedOut) {
                            cursorView?.animate()
                                ?.alpha(cursorBaseAlpha)
                                ?.setDuration(PointerConstants.Timing.CURSOR_FADE_IN_MS)
                                ?.start()
                            cursorFadedOut = false
                        }
                    } else if (scrollRecent) {
                        lastCursorMoveMs = now
                        if (cursorFadedOut) {
                            cursorView?.animate()
                                ?.alpha(cursorBaseAlpha)
                                ?.setDuration(PointerConstants.Timing.CURSOR_FADE_IN_MS)
                                ?.start()
                            cursorFadedOut = false
                        }
                    } else if (!cursorFadedOut) {
                        val fadeTimeout = uiPrefs.getInt("cursor_fade_timeout_ms", 1000)
                            .coerceIn(250, 5000)
                        if (now - lastCursorMoveMs >= fadeTimeout) {
                            cursorView?.animate()
                                ?.alpha(0f)
                                ?.setDuration(PointerConstants.Timing.CURSOR_FADE_OUT_MS)
                                ?.start()
                            cursorFadedOut = true
                        }
                    }
                    val showIndicator = scrollRecent
                    (cursorView as? CursorDotView)?.setScrollIndicator(
                        if (showIndicator) indicator.dirX else 0,
                        if (showIndicator) indicator.dirY else 0
                    )
                    val ghost = PointerBus.getGhostCursor()
                    if (ghost.active) {
                        val half = cursorSizePx / 2f
                        val gLp = ghostCursorLp
                        if (gLp != null) {
                            gLp.x = (ghost.x - half).toInt()
                            gLp.y = (ghost.y - half).toInt()
                        }
                        ghostCursorView?.let { gView ->
                            if (gLp != null) {
                                if (gView.parent == null) {
                                    safeAddView(cursorWm, gView, gLp)
                                } else {
                                    safeUpdateViewLayout(cursorWm, gView, gLp)
                                }
                            }
                        }
                    } else {
                        if (ghostCursorView?.parent != null) {
                            safeRemoveView(cursorWm, ghostCursorView)
                        }
                    }
                    safeUpdateViewLayout(cursorWm, cursorView, lp)
                }
            } catch (_: Throwable) {}
        }
    }

    // detachCursor.
    private fun detachCursor() {
        cursorTimer?.cancel()
        cursorTimer = null

        val tmpV = cursorView
        val tmpGhostCursor = ghostCursorView
        val tmpWm = cursorWm

        if (tmpV != null && tmpWm != null) {
            try {
                // Only remove if it is actually attached
                if (tmpV.parent != null) {
                    safeRemoveViewImmediate(tmpWm, tmpV)
                }
            } catch (t: Throwable) {
                // Do NOT swallow silently while debugging
                Log.e("PointerService", "Failed to remove cursor overlay", t)
            }
        }
        if (tmpGhostCursor != null && tmpWm != null) {
            try {
                if (tmpGhostCursor.parent != null) {
                    safeRemoveViewImmediate(tmpWm, tmpGhostCursor)
                }
            } catch (_: Throwable) {
            }
        }
        cursorView = null
        ghostCursorView = null
        cursorWm = null
        cursorLp = null
        ghostCursorLp = null
        cursorSizePx = 0
        cursorBaseAlpha = PointerConstants.Alpha.CURSOR
        detachLightPrimaryButton()
    }

    // attachTrackpadOverlayToSecondary.
    private fun attachTrackpadOverlayToSecondary() {
        if (mirrorActive) return
        if (padViewA != null || padViewB != null) return
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val secondary = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?: return

        val displayCtx = createDisplayContext(secondary)
        padWm = displayCtx.getSystemService(WINDOW_SERVICE) as WindowManager

        val metrics = displayCtx.resources.displayMetrics
        ensureLightOverlay(displayCtx)

        val defaultPadWidth = dp(metrics, 200)
        val defaultPadHeight = dp(metrics, 200)
        val (rawPadAWidth, rawPadAHeight) = loadTrackpadSize(
            "trackpad_right",
            defaultPadWidth,
            defaultPadHeight
        )
        val (rawPadBWidth, rawPadBHeight) = loadTrackpadSize(
            "trackpad_left",
            defaultPadWidth,
            defaultPadHeight
        )
        val displayW = metrics.widthPixels
        val displayH = metrics.heightPixels
        val (padAWidth, padAHeight) = clampTrackpadSize(metrics, rawPadAWidth, rawPadAHeight)
        val (padBWidth, padBHeight) = clampTrackpadSize(metrics, rawPadBWidth, rawPadBHeight)
        if (padAWidth != rawPadAWidth || padAHeight != rawPadAHeight) {
            saveTrackpadSize("trackpad_right", padAWidth, padAHeight)
        }
        if (padBWidth != rawPadBWidth || padBHeight != rawPadBHeight) {
            saveTrackpadSize("trackpad_left", padBWidth, padBHeight)
        }
        val margin = dp(metrics, 16)

        val padADefaultX = margin
        val padADefaultY = (displayH - padAHeight - margin).coerceAtLeast(0)
        val (padAX, padAY) = loadPosition("pad_a", padADefaultX, padADefaultY)
        padLpA = WindowManager.LayoutParams(
            padAWidth,
            padAHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = padAX
            y = padAY
        }

        val padBDefaultX = (displayW - padBWidth - margin).coerceAtLeast(0)
        val padBDefaultY = (displayH - padBHeight - margin).coerceAtLeast(0)
        val (padBX, padBY) = loadPosition("pad_b", padBDefaultX, padBDefaultY)
        padLpB = WindowManager.LayoutParams(
            padBWidth,
            padBHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = padBX
            y = padBY
        }

        padViewA = buildFloatingTrackpadView(displayCtx, isLeft = false)
        padViewB = buildFloatingTrackpadView(displayCtx, isLeft = true)

        safeAddView(padWm, padViewA, padLpA)
        safeAddView(padWm, padViewB, padLpB)
        padViewA?.setOnTouchListener(
            TrackpadDragTouchListener(
                "pad_a",
                "trackpad_right",
                setOf(ResizeCorner.TOP_LEFT, ResizeCorner.BOTTOM_RIGHT),
                { padLpA },
                { lp ->
                    padLpA = lp
                    try { padWm?.updateViewLayout(padViewA, lp) } catch (_: Throwable) {}
                }
            )
        )
        padViewB?.setOnTouchListener(
            TrackpadDragTouchListener(
                "pad_b",
                "trackpad_left",
                setOf(ResizeCorner.TOP_RIGHT, ResizeCorner.BOTTOM_LEFT),
                { padLpB },
                { lp ->
                    padLpB = lp
                    try { padWm?.updateViewLayout(padViewB, lp) } catch (_: Throwable) {}
                }
            )
        )
        attachNavButtonsToSecondary(displayCtx, metrics)
        bringFloatingButtonsToFront()
        updateDragModeVisuals()
        updateHideOverlaysVisuals()
        updateClickThrough()
        applyUiConfig()
    }

    // openMirrorPermission.
    private fun openMirrorPermission() {
        val intent = Intent(this, MirrorPermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // startMirrorMode.
    private fun startMirrorMode(resultCode: Int, data: Intent) {
        if (mirrorActive) return
        val primaryCtx = getPrimaryDisplayContext()
        val metrics = primaryCtx.resources.displayMetrics
        mirrorPrimaryW = metrics.widthPixels
        mirrorPrimaryH = metrics.heightPixels
        mirrorPrimaryDensity = metrics.densityDpi

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mirrorProjection = projectionManager.getMediaProjection(resultCode, data)

        mirrorActive = true
        detachTrackpad()
        detachCursor()
    }

    // stopMirrorMode.
    private fun stopMirrorMode(restartOverlays: Boolean = true) {
        if (!mirrorActive) return
        mirrorActive = false
        detachMirrorSurface()
        hideMirrorTouch()
        detachMirrorTouchOverlay()
        mirrorProjection?.stop()
        mirrorProjection = null
        mirrorPrimaryW = 0
        mirrorPrimaryH = 0
        mirrorPrimaryDensity = 0

        if (restartOverlays) {
            attachCursorOverlayToPrimary()
            attachTrackpadOverlayToSecondary()
        }
    }

    // attachMirrorSurface.
    fun attachMirrorSurface(surface: Surface) {
        if (!mirrorActive || mirrorProjection == null) return
        if (mirrorPrimaryW <= 0 || mirrorPrimaryH <= 0 || mirrorPrimaryDensity <= 0) return
        mirrorDisplay?.release()
        mirrorDisplay = mirrorProjection?.createVirtualDisplay(
            "mirror_primary",
            mirrorPrimaryW,
            mirrorPrimaryH,
            mirrorPrimaryDensity,
            0,
            surface,
            null,
            null
        )
    }

    // detachMirrorSurface.
    fun detachMirrorSurface() {
        mirrorDisplay?.release()
        mirrorDisplay = null
    }

    // updateMirrorTouch.
    fun updateMirrorTouch(x: Float, y: Float, active: Boolean) {
        if (!mirrorActive) return
        ensureMirrorTouchOverlay()
        val view = mirrorTouchView ?: return
        val lp = mirrorTouchLp ?: return
        val size = lp.width.coerceAtLeast(1)
        lp.x = (x - size / 2f).toInt()
        lp.y = (y - size / 2f).toInt()
        view.alpha = PointerConstants.Alpha.MIRROR_TOUCH
        mirrorTouchHandler.removeCallbacks(mirrorTouchHide)
        if (active) {
            mirrorTouchHandler.postDelayed(
                mirrorTouchHide,
                PointerConstants.Timing.MIRROR_TOUCH_HIDE_ACTIVE_MS
            )
        } else {
            mirrorTouchHandler.postDelayed(
                mirrorTouchHide,
                PointerConstants.Timing.MIRROR_TOUCH_HIDE_INACTIVE_MS
            )
        }
        try { mirrorTouchWm?.updateViewLayout(view, lp) } catch (_: Throwable) {}
    }

    // hideMirrorTouch.
    private fun hideMirrorTouch() {
        mirrorTouchView?.alpha = 0f
    }

    // ensureMirrorTouchOverlay.
    private fun ensureMirrorTouchOverlay() {
        if (mirrorTouchView != null) return
        val displayCtx = getPrimaryDisplayContext()
        mirrorTouchWm = displayCtx.getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = displayCtx.resources.displayMetrics
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            PointerConstants.Sizes.MIRROR_TOUCH_DP,
            metrics
        ).toInt()
        mirrorTouchView = View(displayCtx).apply {
            background = GradientDrawable().apply {
                cornerRadius = sizePx / 2f
                setColor(0xFF00C853.toInt())
            }
            alpha = 0f
        }
        mirrorTouchLp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        safeAddView(mirrorTouchWm, mirrorTouchView, mirrorTouchLp)

        val ringSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, metrics).toInt()
        mirrorClickView = View(displayCtx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x00000000)
                setStroke(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        2f,
                        metrics
                    ).toInt(),
                    0x7F00C853
                )
            }
            alpha = 0f
        }
        mirrorClickLp = WindowManager.LayoutParams(
            ringSizePx,
            ringSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        safeAddView(mirrorTouchWm, mirrorClickView, mirrorClickLp)
    }

    // detachMirrorTouchOverlay.
    private fun detachMirrorTouchOverlay() {
        mirrorTouchHandler.removeCallbacks(mirrorTouchHide)
        val view = mirrorTouchView
        val clickView = mirrorClickView
        val wm = mirrorTouchWm
        if (view != null && wm != null) {
            try {
                if (view.parent != null) {
                    safeRemoveViewImmediate(wm, view)
                }
            } catch (_: Throwable) {}
        }
        if (clickView != null && wm != null) {
            try {
                if (clickView.parent != null) {
                    safeRemoveViewImmediate(wm, clickView)
                }
            } catch (_: Throwable) {}
        }
        mirrorTouchView = null
        mirrorTouchLp = null
        mirrorClickView = null
        mirrorClickLp = null
        mirrorTouchWm = null
    }

    // showMirrorClickRing.
    private fun showMirrorClickRing(x: Float, y: Float) {
        val view = mirrorClickView ?: return
        val lp = mirrorClickLp ?: return
        val size = lp.width.coerceAtLeast(1)
        lp.x = (x - size / 2f).toInt()
        lp.y = (y - size / 2f).toInt()
        view.alpha = 0.7f
        view.animate().alpha(0f).setDuration(180L).start()
        try { mirrorTouchWm?.updateViewLayout(view, lp) } catch (_: Throwable) {}
    }

    // showMirrorClickRingAt.
    fun showMirrorClickRingAt(x: Float, y: Float) {
        if (!mirrorActive) return
        ensureMirrorTouchOverlay()
        showMirrorClickRing(x, y)
    }

    // detachTrackpad.
    private fun detachTrackpad() {
        val wm = padWm
        if (wm != null) {
            safeRemoveView(wm, lightOverlayView)
            safeRemoveView(wm, padViewA)
            safeRemoveView(wm, padViewB)
            safeRemoveView(wm, backView)
            safeRemoveView(wm, homeView)
            safeRemoveView(wm, recentsView)
            safeRemoveView(wm, closeView)
            safeRemoveView(wm, dragToggleView)
            safeRemoveView(wm, navToggleView)
            safeRemoveView(wm, hideToggleView)
            safeRemoveView(wm, swapView)
            safeRemoveView(wm, lightToggleView)
            safeRemoveView(wm, mirrorToggleView)
            safeRemoveView(wm, clickView)
            safeRemoveView(wm, rightClickView)
        }
        lightOverlayView = null
        lightOverlayLp = null
        padViewA = null
        padViewB = null
        padLpA = null
        padLpB = null
        trackpadCards.clear()
        trackpadSurfaces.clear()
        trackpadResizeHandles.clear()
        trackpadLabels.clear()
        trackpadHeaderRows.clear()
        trackpadHeaderButtons.clear()
        floatingButtons.clear()
        backView = null
        homeView = null
        recentsView = null
        closeView = null
        dragToggleView = null
        navToggleView = null
        hideToggleView = null
        swapView = null
        lightToggleView = null
        mirrorToggleView = null
        clickView = null
        rightClickView = null
        backLp = null
        homeLp = null
        recentsLp = null
        closeLp = null
        dragToggleLp = null
        navToggleLp = null
        hideToggleLp = null
        swapLp = null
        lightToggleLp = null
        mirrorToggleLp = null
        clickLp = null
        rightClickLp = null
    }

    // createFloatingButton.
    private fun createFloatingButton(
        ctx: Context,
        icon: Int,
        sizePx: Int,
        bgColor: Int,
        action: () -> Unit
    ): ImageButton {
        return ImageButton(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = (sizePx / 2f)
                setColor(bgColor)
            }
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener {
                if (uiPrefs.getBoolean("haptic_button_press", true)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                action()
            }
        }.also { floatingButtons.add(it) }
    }

    // buildFloatingTrackpadView.
    private fun buildFloatingTrackpadView(ctx: Context, isLeft: Boolean): View {
        val metrics = ctx.resources.displayMetrics

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(metrics, 18).toFloat()
                setColor(Color.argb(200, 30, 30, 30))
            }
            elevation = dp(metrics, 12).toFloat()
            setPadding(dp(metrics, 10), dp(metrics, 10), dp(metrics, 10), dp(metrics, 10))
        }
        trackpadCards.add(card)

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isLeft) Gravity.START else Gravity.END
            setPadding(dp(metrics, 4), dp(metrics, 2), dp(metrics, 4), dp(metrics, 6))
            visibility = View.GONE
        }
        trackpadHeaderRows.add(headerRow)

        headerRow.addView(
            createHeaderButton(ctx, R.drawable.ic_back, dp(metrics, 28)) {
                PointerAccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_BACK)
            }
        )
        headerRow.addView(
            createHeaderButton(ctx, R.drawable.ic_home, dp(metrics, 28)) {
                PointerAccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
        headerRow.addView(
            createHeaderButton(ctx, R.drawable.ic_menu, dp(metrics, 28)) {
                PointerAccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        )
        headerRow.addView(
            createHeaderButton(ctx, android.R.drawable.ic_menu_close_clear_cancel, dp(metrics, 28)) {
                stopOverlayAndSelf()
            }
        )

        val container = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val trackpad = TrackpadView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(metrics, 14).toFloat()
                setColor(Color.argb(50, 30, 30, 30))
            }
        }
        trackpadSurfaces.add(trackpad)
        container.addView(trackpad)

        val handleSize = dp(metrics, 22)
        val handleMargin = dp(metrics, 8)
        val (firstGravity, secondGravity) = if (isLeft) {
            (Gravity.TOP or Gravity.END) to (Gravity.BOTTOM or Gravity.START)
        } else {
            (Gravity.TOP or Gravity.START) to (Gravity.BOTTOM or Gravity.END)
        }

        val handleA = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(handleSize, handleSize).apply {
                gravity = firstGravity
                setMargins(handleMargin, handleMargin, handleMargin, handleMargin)
            }
            setImageResource(R.drawable.ic_resize_handle)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val handleB = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(handleSize, handleSize).apply {
                gravity = secondGravity
                setMargins(handleMargin, handleMargin, handleMargin, handleMargin)
            }
            setImageResource(R.drawable.ic_resize_handle)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        if (!isLeft) {
            handleA.scaleX = -1f
            handleB.scaleX = -1f
        }
        container.addView(handleA)
        container.addView(handleB)
        trackpadResizeHandles.add(handleA)
        trackpadResizeHandles.add(handleB)

        val label = TextView(ctx).apply {
            text = if (isLeft) "L" else "R"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        container.addView(label)
        trackpadLabels.add(label)

        card.addView(headerRow)
        card.addView(container)
        return card
    }

    // createHeaderButton.
    private fun createHeaderButton(
        ctx: Context,
        icon: Int,
        sizePx: Int,
        action: () -> Unit
    ): ImageButton {
        return ImageButton(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginEnd = dp(ctx.resources.displayMetrics, 6)
            }
            background = GradientDrawable().apply {
                cornerRadius = sizePx / 2f
                setColor(Color.argb(110, 60, 60, 60))
            }
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener {
                if (uiPrefs.getBoolean("haptic_trackpad_nav_press", true)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                action()
            }
        }.also { trackpadHeaderButtons.add(it) }
    }

    // attachNavButtonsToSecondary.
    private fun attachNavButtonsToSecondary(
        ctx: Context,
        metrics: DisplayMetrics
    ) {
        val wm = padWm ?: return
        if (dragToggleView != null || backView != null || homeView != null || recentsView != null || closeView != null) {
            return
        }

        val sizePx = getButtonSizePx(metrics)
        val gapPx = dp(metrics, 8)
        val baseX = dp(metrics, 16)
        val baseY = dp(metrics, 16)
        val defaultColor = Color.argb(110, 60, 60, 60)
        val closeColor = Color.argb(130, 90, 50, 50)
        val dragOffColor = defaultColor

        var navButtonsEnabled = uiPrefs.getBoolean("nav_buttons_enabled", true)
        val navToggleVisible = uiPrefs.getBoolean("show_nav_buttons", true)
        if (!navToggleVisible && !navButtonsEnabled) {
            navButtonsEnabled = true
            uiPrefs.edit { putBoolean("nav_buttons_enabled", true) }
        }
        showNavButtons = navButtonsEnabled
        if (navButtonsEnabled) {
            showNavButtonCluster(ctx, wm, sizePx, gapPx, baseX, baseY, defaultColor, closeColor)
        }

        dragModeEnabled = uiPrefs.getBoolean("drag_mode_enabled", false)
        dragEnabled = dragModeEnabled
        if (uiPrefs.getBoolean("show_drag_btn", true)) {
            dragToggleLp = createButtonLayoutParams(
                "nav_drag",
                sizePx,
                baseX + 4 * (sizePx + gapPx),
                baseY
            )
            dragToggleView = createFloatingButton(ctx, R.drawable.ic_drag, sizePx, dragOffColor) {
                dragModeEnabled = !dragModeEnabled
                dragEnabled = dragModeEnabled
                uiPrefs.edit { putBoolean("drag_mode_enabled", dragModeEnabled) }
                updateDragToggleAppearance()
                updateDragModeVisuals()
            }
            dragToggleView?.setOnTouchListener(
                FloatingButtonDragTouchListener(
                    "nav_drag",
                    { dragToggleLp },
                    { lp ->
                        dragToggleLp = lp
                        wm.updateViewLayout(dragToggleView, lp)
                    },
                    allowTapWhenDragEnabled = true
                )
            )
            updateDragToggleAppearance()
            safeAddView(wm, dragToggleView, dragToggleLp)
        }

        if (uiPrefs.getBoolean("show_hide_btn", true)) {
            hideToggleLp = createButtonLayoutParams(
                "nav_hide",
                sizePx,
                baseX + 6 * (sizePx + gapPx),
                baseY
            )
            hideToggleView = createFloatingButton(ctx, R.drawable.ic_eye_open, sizePx, defaultColor) {
                if (!dragModeEnabled) {
                    toggleHideOverlays()
                }
            }
            hideToggleView?.setOnLongClickListener {
                if (!dragModeEnabled) {
                    toggleClickThrough()
                }
                true
            }
            hideToggleView?.setOnTouchListener(
                FloatingButtonDragTouchListener(
                    "nav_hide",
                    { hideToggleLp },
                    { lp ->
                        hideToggleLp = lp
                        wm.updateViewLayout(hideToggleView, lp)
                    },
                    allowTapWhenDragEnabled = false
                )
            )
            safeAddView(wm, hideToggleView, hideToggleLp)
        }

        if (uiPrefs.getBoolean("show_swap_btn", true)) {
            swapLp = createButtonLayoutParams(
                "nav_swap",
                sizePx,
                baseX + 7 * (sizePx + gapPx),
                baseY
            )
            swapView = createFloatingButton(ctx, R.drawable.ic_swap, sizePx, defaultColor) {}
            swapView?.setOnTouchListener(
                FloatingButtonDragTouchListener(
                    "nav_swap",
                    { swapLp },
                    { lp ->
                        swapLp = lp
                        wm.updateViewLayout(swapView, lp)
                    }
                )
            )
            safeAddView(wm, swapView, swapLp)
        }
    }

    // applyUiConfig.
    private fun applyUiConfig() {
        if (mirrorActive) return
        val wm = padWm ?: return
        val ctx = padViewA?.context ?: padViewB?.context ?: return
        val metrics = ctx.resources.displayMetrics

        var navButtonsEnabled = uiPrefs.getBoolean("nav_buttons_enabled", true)
        val navToggleVisible = uiPrefs.getBoolean("show_nav_buttons", true)
        if (!navToggleVisible && !navButtonsEnabled) {
            navButtonsEnabled = true
            uiPrefs.edit { putBoolean("nav_buttons_enabled", true) }
        }
        showNavButtons = navButtonsEnabled
        val wantBack = uiPrefs.getBoolean("show_back_btn", true) && navButtonsEnabled
        val wantHome = uiPrefs.getBoolean("show_home_btn", true) && navButtonsEnabled
        val wantRecents = uiPrefs.getBoolean("show_recents_btn", true) && navButtonsEnabled
        val wantStop = uiPrefs.getBoolean("show_stop_btn", true) && navButtonsEnabled
        val wantDrag = uiPrefs.getBoolean("show_drag_btn", true)
        val wantHide = uiPrefs.getBoolean("show_hide_btn", true)
        val wantSwap = uiPrefs.getBoolean("show_swap_btn", true)
        val wantNavToggle = navToggleVisible
        val wantLight = uiPrefs.getBoolean("show_light_btn", true)
        val wantMirror = uiPrefs.getBoolean("show_mirror_btn", true)
        val wantClick = uiPrefs.getBoolean("show_click_btn", true)
        val wantRightClick = uiPrefs.getBoolean("show_right_click_btn", true)
        val showPadLeft = uiPrefs.getBoolean("show_trackpad_left", true)
        val showPadRight = uiPrefs.getBoolean("show_trackpad_right", true)
        buttonOpacity = uiPrefs.getInt("button_opacity", 100).coerceIn(0, 100)
        trackpadOpacity = uiPrefs.getInt("trackpad_opacity", 100).coerceIn(0, 100)
        lightOverlayEnabled = uiPrefs.getBoolean("light_overlay_enabled", false)
        lightOffKeepControls = uiPrefs.getBoolean("light_off_keep_controls", false)
        lightOffPrimaryButton = uiPrefs.getBoolean("light_off_primary_button", false)
        lightOffHideBottomButton = uiPrefs.getBoolean("light_off_hide_bottom_button", false)
        if (!lightOffHideBottomButton && lightOffPrimaryButton) {
            lightOffPrimaryButton = false
            uiPrefs.edit { putBoolean("light_off_primary_button", false) }
        }

        Log.d("PointerService", "buttonOpacity $buttonOpacity")

        updateCursorAppearance()

        dragModeEnabled = uiPrefs.getBoolean("drag_mode_enabled", false)
        dragEnabled = dragModeEnabled
        updateFloatingButtonState(
            wantBack,
            backView,
            backLp,
            "nav_back",
            R.drawable.ic_back,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            { PointerAccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_BACK) },
            wm,
            ctx,
            metrics
        )
        updateFloatingButtonState(
            wantHome,
            homeView,
            homeLp,
            "nav_home",
            R.drawable.ic_home,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            { PointerAccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_HOME) },
            wm,
            ctx,
            metrics
        )
        updateFloatingButtonState(
            wantRecents,
            recentsView,
            recentsLp,
            "nav_recents",
            R.drawable.ic_menu,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            { PointerAccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) },
            wm,
            ctx,
            metrics
        )
        updateFloatingButtonState(
            wantStop,
            closeView,
            closeLp,
            "nav_close",
            android.R.drawable.ic_menu_close_clear_cancel,
            Color.argb(buttonOpacity * 255 / 100, 90, 50, 50),
            {
                stopOverlayAndSelf()
            },
            wm,
            ctx,
            metrics
        )

        updateFloatingButtonState(
            wantDrag,
            dragToggleView,
            dragToggleLp,
            "nav_drag",
            R.drawable.ic_drag,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {
                dragModeEnabled = !dragModeEnabled
                dragEnabled = dragModeEnabled
                uiPrefs.edit { putBoolean("drag_mode_enabled", dragModeEnabled) }
                updateDragToggleAppearance()
                updateDragModeVisuals()
            },
            wm,
            ctx,
            metrics,
            allowTapWhenDragEnabled = true
        )

        updateFloatingButtonState(
            wantHide,
            hideToggleView,
            hideToggleLp,
            "nav_hide",
            R.drawable.ic_eye_open,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {
                if (!dragModeEnabled) {
                    toggleHideOverlays()
                }
            },
            wm,
            ctx,
            metrics,
            allowTapWhenDragEnabled = false,
            onLongPress = {
                if (!dragModeEnabled) {
                    toggleClickThrough()
                }
                true
            }
        )

        updateFloatingButtonState(
            wantNavToggle,
            navToggleView,
            navToggleLp,
            "nav_toggle",
            android.R.drawable.ic_menu_manage,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {
                if (!dragModeEnabled) {
                    toggleNavButtons()
                }
            },
            wm,
            ctx,
            metrics
        )

        updateFloatingButtonState(
            wantSwap,
            swapView,
            swapLp,
            "nav_swap",
            R.drawable.ic_swap,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {},
            wm,
            ctx,
            metrics
        )
        updateFloatingButtonState(
            wantLight,
            lightToggleView,
            lightToggleLp,
            "nav_light",
            R.drawable.ic_light_bulb,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {
                toggleLightOverlay()
            },
            wm,
            ctx,
            metrics
        )
        updateOverlayPosition("nav_light", lightToggleView, lightToggleLp, wm)
        updateFloatingButtonState(
            wantMirror,
            mirrorToggleView,
            mirrorToggleLp,
            "nav_mirror",
            R.drawable.ic_mirror,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {
                if (mirrorActive) {
                    stopMirrorMode()
                } else {
                    openMirrorPermission()
                }
            },
            wm,
            ctx,
            metrics
        )

        updateFloatingButtonState(
            wantClick,
            clickView,
            clickLp,
            "nav_click",
            R.drawable.ic_trackpad_click,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {
                val s = PointerBus.get()
                PointerAccessibilityService.instance?.clickAt(s.x, s.y)
            },
            wm,
            ctx,
            metrics,
            allowTapWhenDragEnabled = false,
            onLongPress = {
                if (uiPrefs.getBoolean("click_hold_right_click", true)) {
                    val s = PointerBus.get()
                    PointerAccessibilityService.instance?.rightClickAt(s.x, s.y)
                }
                true
            }
        )

        updateFloatingButtonState(
            wantRightClick,
            rightClickView,
            rightClickLp,
            "nav_right_click",
            R.drawable.ic_trackpad_right_click,
            Color.argb(buttonOpacity * 255 / 100, 60, 60, 60),
            {
                val s = PointerBus.get()
                PointerAccessibilityService.instance?.rightClickAt(s.x, s.y)
            },
            wm,
            ctx,
            metrics,
            allowTapWhenDragEnabled = false
        )

        bringFloatingButtonsToFront()
        updateDragToggleAppearance()
        updateDragModeVisuals()
        updateHideOverlaysVisuals()
        updateClickThrough()
        updateButtonOpacity(buttonOpacity)
        updateTrackpadOpacity(trackpadOpacity)
        updateLightToggleAppearance()
        updateLightOverlayVisibility()
        updateLightPrimaryButtonVisibility()
        updateHideOverlaysVisuals()
        updateFloatingButtonSizes(wm, metrics)
        updateLightPrimaryButtonSize()
        applyTrackpadSizes(wm, metrics)
        updateTrackpadHeaderVisibility(!navButtonsEnabled)
        updateTrackpadVisibility(padViewA, padLpA, showPadRight, wm)
        updateTrackpadVisibility(padViewB, padLpB, showPadLeft, wm)
    }

    private fun updateCursorAppearance() {
        val baseColor = readCursorColor()
        val indicatorColor = scaleColor(baseColor, 0.85f)
        val opacityPct = uiPrefs.getInt(
            "cursor_opacity",
            UiConstants.Sliders.CURSOR_OPACITY_DEFAULT
        ).coerceIn(UiConstants.Sliders.CURSOR_OPACITY_MIN, UiConstants.Sliders.CURSOR_OPACITY_MAX)
        cursorBaseAlpha = PointerConstants.Alpha.CURSOR * (opacityPct / 100f)
        (cursorView as? CursorDotView)?.setColors(baseColor, indicatorColor)
        cursorView?.alpha = cursorBaseAlpha
        val ghostBase = toGray(baseColor)
        val ghostIndicator = scaleColor(ghostBase, 0.85f)
        (ghostCursorView as? CursorDotView)?.setColors(ghostBase, ghostIndicator)
        ghostCursorView?.alpha = cursorBaseAlpha
    }

    private fun readCursorColor(): Int {
        val r = uiPrefs.getInt(
            "cursor_color_r",
            UiConstants.Sliders.CURSOR_COLOR_R_DEFAULT
        ).coerceIn(UiConstants.Sliders.CURSOR_COLOR_MIN, UiConstants.Sliders.CURSOR_COLOR_MAX)
        val g = uiPrefs.getInt(
            "cursor_color_g",
            UiConstants.Sliders.CURSOR_COLOR_G_DEFAULT
        ).coerceIn(UiConstants.Sliders.CURSOR_COLOR_MIN, UiConstants.Sliders.CURSOR_COLOR_MAX)
        val b = uiPrefs.getInt(
            "cursor_color_b",
            UiConstants.Sliders.CURSOR_COLOR_B_DEFAULT
        ).coerceIn(UiConstants.Sliders.CURSOR_COLOR_MIN, UiConstants.Sliders.CURSOR_COLOR_MAX)
        return Color.rgb(r, g, b)
    }

    private fun scaleColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun toGray(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val gray = (r * 0.299f + g * 0.587f + b * 0.114f).toInt().coerceIn(0, 255)
        return Color.rgb(gray, gray, gray)
    }

    // updateTrackpadHeaderVisibility.
    private fun updateTrackpadHeaderVisibility(showHeader: Boolean) {
        val visible = showHeader && !hideOverlays
        for (header in trackpadHeaderRows) {
            header.visibility = if (visible) View.VISIBLE else View.GONE
            header.alpha = if (visible) 1f else 0f
        }
    }

    // applyOverlayPositions.
    private fun applyOverlayPositions() {
        val wm = padWm ?: return
        updateOverlayPosition("pad_a", padViewA, padLpA, wm)
        updateOverlayPosition("pad_b", padViewB, padLpB, wm)
        updateOverlayPosition("nav_back", backView, backLp, wm)
        updateOverlayPosition("nav_home", homeView, homeLp, wm)
        updateOverlayPosition("nav_recents", recentsView, recentsLp, wm)
        updateOverlayPosition("nav_close", closeView, closeLp, wm)
        updateOverlayPosition("nav_drag", dragToggleView, dragToggleLp, wm)
        updateOverlayPosition("nav_toggle", navToggleView, navToggleLp, wm)
        updateOverlayPosition("nav_hide", hideToggleView, hideToggleLp, wm)
        updateOverlayPosition("nav_swap", swapView, swapLp, wm)
        updateOverlayPosition("nav_light", lightToggleView, lightToggleLp, wm)
        updateOverlayPosition("nav_mirror", mirrorToggleView, mirrorToggleLp, wm)
        updateOverlayPosition("nav_click", clickView, clickLp, wm)
        updateOverlayPosition("nav_right_click", rightClickView, rightClickLp, wm)
    }

    // updateOverlayPosition.
    private fun updateOverlayPosition(
        key: String,
        view: View?,
        lp: WindowManager.LayoutParams?,
        wm: WindowManager
    ) {
        if (view == null || lp == null) return
        val (rawX, rawY) = loadPosition(key, lp.x, lp.y)
        val (maxX, maxY) = getDragBounds(view, lp)
        val newX = rawX.coerceIn(0, maxX.coerceAtLeast(0))
        val newY = rawY.coerceIn(0, maxY.coerceAtLeast(0))
        if (newX != lp.x || newY != lp.y) {
            lp.x = newX
            lp.y = newY
            try {
                wm.updateViewLayout(view, lp)
            } catch (_: Throwable) {
            }
        }
    }

    // updateTrackpadVisibility.
    private fun updateTrackpadVisibility(
        view: View?,
        lp: WindowManager.LayoutParams?,
        show: Boolean,
        wm: WindowManager
    ) {
        if (view == null || lp == null) return
        view.alpha = if (show) 1f else 0f
        view.visibility = if (show) View.VISIBLE else View.GONE
        val newFlags = if (show) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (newFlags != lp.flags) {
            lp.flags = newFlags
            try {
                wm.updateViewLayout(view, lp)
            } catch (_: Throwable) {
            }
        }
    }

    private fun isTrackpadCardEnabled(card: View): Boolean {
        return when (card) {
            padViewA -> uiPrefs.getBoolean("show_trackpad_right", true)
            padViewB -> uiPrefs.getBoolean("show_trackpad_left", true)
            else -> true
        }
    }

    // updateButtonOpacity.
    private fun updateButtonOpacity(buttonOpacity: Int) {
        val alpha = (buttonOpacity.coerceIn(0, 100) * 255 / 100)
        for (view in floatingButtons) {
            val bg = view.background as? GradientDrawable ?: continue
            bg.alpha = alpha
            if (view is ImageButton) {
                view.imageAlpha = alpha
            }
        }
        for (view in trackpadHeaderButtons) {
            val bg = view.background as? GradientDrawable ?: continue
            bg.alpha = alpha
            view.imageAlpha = alpha
        }
    }

    // updateTrackpadOpacity.
    private fun updateTrackpadOpacity(trackpadOpacity: Int) {
        val alpha = (trackpadOpacity.coerceIn(0, 100) / 100f)
        for (card in trackpadCards) {
            card.alpha = alpha
        }
        for (pad in trackpadSurfaces) {
            pad.alpha = alpha
        }
    }

    // updateFloatingButtonState.
    private fun updateFloatingButtonState(
        enabled: Boolean,
        currentView: ImageButton?,
        currentLp: WindowManager.LayoutParams?,
        key: String,
        icon: Int,
        bgColor: Int,
        action: () -> Unit,
        wm: WindowManager,
        ctx: Context,
        metrics: DisplayMetrics,
        allowTapWhenDragEnabled: Boolean = false,
        onLongPress: (() -> Boolean)? = null
    ) {
        if (enabled) {
            if (currentView != null && currentLp != null && currentView.parent != null) return
            val sizePx = getButtonSizePx(metrics)
        val gapPx = dp(metrics, 8)
        val baseX = dp(metrics, 16)
        val baseY = dp(metrics, 16)
            val indexOffset = when (key) {
                "nav_back" -> 0
                "nav_home" -> 1
                "nav_recents" -> 2
                "nav_close" -> 3
                "nav_drag" -> 4
                "nav_toggle" -> 5
                "nav_hide" -> 6
                "nav_swap" -> 7
                "nav_mirror" -> 8
                "nav_click" -> 9
                "nav_right_click" -> 10
                 "nav_light" -> 11
                else -> 0
            }
            val lp = createButtonLayoutParams(
                key,
                sizePx,
                baseX + indexOffset * (sizePx + gapPx),
                baseY
            )
            val view = createFloatingButton(ctx, icon, sizePx, bgColor, action)
            view.imageAlpha = (Color.alpha(bgColor)).coerceIn(0, 255)
            view.setOnTouchListener(
                FloatingButtonDragTouchListener(
                    key,
                    { lp },
                    { updated ->
                        lp.x = updated.x
                        lp.y = updated.y
                        wm.updateViewLayout(view, lp)
                    },
                    allowTapWhenDragEnabled = allowTapWhenDragEnabled
                )
            )
            onLongPress?.let { view.setOnLongClickListener { it() } }
            safeAddView(wm, view, lp)

            when (key) {
                "nav_back" -> { backView = view; backLp = lp }
                "nav_home" -> { homeView = view; homeLp = lp }
                "nav_recents" -> { recentsView = view; recentsLp = lp }
                "nav_close" -> { closeView = view; closeLp = lp }
                "nav_drag" -> { dragToggleView = view; dragToggleLp = lp }
                "nav_toggle" -> { navToggleView = view; navToggleLp = lp }
                "nav_hide" -> { hideToggleView = view; hideToggleLp = lp }
                "nav_swap" -> { swapView = view; swapLp = lp }
                "nav_light" -> { lightToggleView = view; lightToggleLp = lp }
                "nav_mirror" -> { mirrorToggleView = view; mirrorToggleLp = lp }
                "nav_click" -> { clickView = view; clickLp = lp }
                "nav_right_click" -> { rightClickView = view; rightClickLp = lp }
            }
        } else {
            when (key) {
                "nav_back" -> { removeFloatingButton(wm, currentView); backView = null; backLp = null }
                "nav_home" -> { removeFloatingButton(wm, currentView); homeView = null; homeLp = null }
                "nav_recents" -> { removeFloatingButton(wm, currentView); recentsView = null; recentsLp = null }
                "nav_close" -> { removeFloatingButton(wm, currentView); closeView = null; closeLp = null }
                "nav_drag" -> { removeFloatingButton(wm, currentView); dragToggleView = null; dragToggleLp = null }
                "nav_toggle" -> { removeFloatingButton(wm, currentView); navToggleView = null; navToggleLp = null }
                "nav_hide" -> { removeFloatingButton(wm, currentView); hideToggleView = null; hideToggleLp = null }
                "nav_swap" -> { removeFloatingButton(wm, currentView); swapView = null; swapLp = null }
                "nav_light" -> { removeFloatingButton(wm, currentView); lightToggleView = null; lightToggleLp = null }
                "nav_mirror" -> { removeFloatingButton(wm, currentView); mirrorToggleView = null; mirrorToggleLp = null }
                "nav_click" -> { removeFloatingButton(wm, currentView); clickView = null; clickLp = null }
                "nav_right_click" -> { removeFloatingButton(wm, currentView); rightClickView = null; rightClickLp = null }
            }
        }
    }

    // removeFloatingButton.
    private fun removeFloatingButton(wm: WindowManager, view: View?) {
        if (view == null) return
        try {
            safeRemoveView(wm, view)
        } catch (_: Throwable) {
        }
    }

    // detachTrackpadOverlay.
    private fun detachTrackpadOverlay() {
        padWm = null
    }

    // registerScreenReceiver.
    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            // onReceive.
            override fun onReceive(context: Context?, intent: Intent?) {
                updateOverlaysForLockState()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
        updateOverlaysForLockState()
    }

    // unregisterScreenReceiver.
    private fun unregisterScreenReceiver() {
        val receiver = screenReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (_: Throwable) {
        }
        screenReceiver = null
    }

    // updateOverlaysForLockState.
    private fun updateOverlaysForLockState() {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val locked = km.isKeyguardLocked
        if (locked) {
            detachTrackpad()
        } else {
            if (!mirrorActive) {
                attachTrackpadOverlayToSecondary()
            }
        }
    }

    private inner class FloatingButtonDragTouchListener(
        private val key: String,
        private val getLp: () -> WindowManager.LayoutParams?,
        private val update: (WindowManager.LayoutParams) -> Unit,
        private val allowTapWhenDragEnabled: Boolean = false
    ) : View.OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var touchSlop = 0

        // onTouch.
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = getLp() ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX
                    lastY = e.rawY
                    moved = false
                    if (touchSlop == 0) {
                        touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop
                    }
                    return dragModeEnabled
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragModeEnabled) return false
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY
                    lastX = e.rawX
                    lastY = e.rawY

                    if (!moved &&
                        (abs(dx) >= touchSlop || abs(dy) >= touchSlop)) {
                        moved = true
                    }

                    if (moved) {
                        val (maxX, maxY) = getDragBounds(v, lp)
                        Log.d("PointerService","floating button bound: $maxX $maxY")
                        val newX = (lp.x + dx).toInt().coerceIn(0, maxX.coerceAtLeast(0))
                        val newY = (lp.y + dy).toInt().coerceIn(0, maxY.coerceAtLeast(0))
                        lp.x = newX
                        lp.y = newY
                        update(lp)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragModeEnabled) {
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
                        savePosition(key, lp.x, lp.y)
                    }
                    return true
                }
            }
            return false
        }
    }

    private inner class TrackpadDragTouchListener(
        private val key: String,
        private val sizeKey: String,
        private val resizeCorners: Set<ResizeCorner>,
        private val getLp: () -> WindowManager.LayoutParams?,
        private val update: (WindowManager.LayoutParams) -> Unit
    ) : View.OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var touchSlop = 0
        private var resizing = false
        private var activeCorner: ResizeCorner? = null
        private var resizeHandlePx = 0
        private var minSizePx = 0
        private var startRawX = 0f
        private var startRawY = 0f
        private var startX = 0
        private var startY = 0
        private var startW = 0
        private var startH = 0

        // onTouch.
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = getLp() ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!dragModeEnabled) return false
                    lastX = e.rawX
                    lastY = e.rawY
                    moved = false
                    val metrics = v.resources.displayMetrics
                    if (touchSlop == 0) {
                        touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop
                    }
                    if (resizeHandlePx == 0) {
                        resizeHandlePx = dp(metrics, 40)
                    }
                    if (minSizePx == 0) {
                        minSizePx = dp(metrics, 120)
                    }
                    val inTopLeft = e.x <= resizeHandlePx && e.y <= resizeHandlePx
                    val inTopRight = e.x >= v.width - resizeHandlePx && e.y <= resizeHandlePx
                    val inBottomLeft = e.x <= resizeHandlePx && e.y >= v.height - resizeHandlePx
                    val inBottomRight =
                        e.x >= v.width - resizeHandlePx && e.y >= v.height - resizeHandlePx
                    activeCorner = when {
                        inTopLeft && resizeCorners.contains(ResizeCorner.TOP_LEFT) ->
                            ResizeCorner.TOP_LEFT
                        inTopRight && resizeCorners.contains(ResizeCorner.TOP_RIGHT) ->
                            ResizeCorner.TOP_RIGHT
                        inBottomLeft && resizeCorners.contains(ResizeCorner.BOTTOM_LEFT) ->
                            ResizeCorner.BOTTOM_LEFT
                        inBottomRight && resizeCorners.contains(ResizeCorner.BOTTOM_RIGHT) ->
                            ResizeCorner.BOTTOM_RIGHT
                        else -> null
                    }
                    resizing = activeCorner != null
                    startRawX = e.rawX
                    startRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    startW = lp.width
                    startH = lp.height
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragModeEnabled) return false
                    if (resizing) {
                        val dx = e.rawX - startRawX
                        val dy = e.rawY - startRawY
                        if (!moved &&
                            (abs(dx) >= touchSlop || abs(dy) >= touchSlop)) {
                            moved = true
                        }
                        if (moved) {
                            val (displayW, displayH) = getDisplaySize(v)
                            val startLeft = startX
                            val startTop = startY
                            val startRight = startX + startW
                            val startBottom = startY + startH
                            var newLeft = startLeft
                            var newTop = startTop
                            var newRight = startRight
                            var newBottom = startBottom
                            when (activeCorner) {
                                ResizeCorner.TOP_LEFT -> {
                                    newLeft = (startLeft + dx).toInt()
                                        .coerceIn(0, startRight - minSizePx)
                                    newTop = (startTop + dy).toInt()
                                        .coerceIn(0, startBottom - minSizePx)
                                }
                                ResizeCorner.BOTTOM_RIGHT -> {
                                    newRight = (startRight + dx).toInt()
                                        .coerceIn(startLeft + minSizePx, displayW)
                                    newBottom = (startBottom + dy).toInt()
                                        .coerceIn(startTop + minSizePx, displayH)
                                }
                                ResizeCorner.TOP_RIGHT -> {
                                    newRight = (startRight + dx).toInt()
                                        .coerceIn(startLeft + minSizePx, displayW)
                                    newTop = (startTop + dy).toInt()
                                        .coerceIn(0, startBottom - minSizePx)
                                }
                                ResizeCorner.BOTTOM_LEFT -> {
                                    newLeft = (startLeft + dx).toInt()
                                        .coerceIn(0, startRight - minSizePx)
                                    newBottom = (startBottom + dy).toInt()
                                        .coerceIn(startTop + minSizePx, displayH)
                                }
                                null -> {}
                            }
                            lp.x = newLeft
                            lp.y = newTop
                            lp.width = (newRight - newLeft).coerceAtLeast(minSizePx)
                            lp.height = (newBottom - newTop).coerceAtLeast(minSizePx)
                            update(lp)
                        }
                        return true
                    }
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY
                    lastX = e.rawX
                    lastY = e.rawY

                    if (!moved &&
                        (abs(dx) >= touchSlop || abs(dy) >= touchSlop)) {
                        moved = true
                    }

                    if (moved) {
                        val (maxX, maxY) = getDragBounds(v, lp)
                        val rawX = (lp.x + dx).toInt()
                        val rawY = (lp.y + dy).toInt()
                        lp.x = rawX.coerceIn(0, maxX.coerceAtLeast(0))
                        lp.y = rawY.coerceIn(0, maxY.coerceAtLeast(0))
                        update(lp)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) {
                        v.performClick()
                        resizing = false
                        activeCorner = null
                        return true
                    }
                    savePosition(key, lp.x, lp.y)
                    if (resizing) {
                        saveTrackpadSize(sizeKey, lp.width, lp.height)
                    }
                    resizing = false
                    activeCorner = null
                    return true
                }
            }
            return false
        }
    }

    private enum class ResizeCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    // dp.
    private fun dp(metrics: DisplayMetrics, dp: Int): Int =
        (dp * metrics.density).roundToInt()

    // getButtonSizePx.
    private fun getButtonSizePx(metrics: DisplayMetrics): Int {
        val minPx = dp(metrics, 24)
        val maxPx = dp(metrics, 120)
        val defaultPx = dp(metrics, 44)
        return uiPrefs.getInt("button_size", defaultPx).coerceIn(minPx, maxPx)
    }

    // updateFloatingButtonSizes.
    private fun updateFloatingButtonSizes(
        wm: WindowManager,
        metrics: DisplayMetrics
    ) {
        val sizePx = getButtonSizePx(metrics)
        updateButtonSizeFor(wm, backView, backLp, sizePx)
        updateButtonSizeFor(wm, homeView, homeLp, sizePx)
        updateButtonSizeFor(wm, recentsView, recentsLp, sizePx)
        updateButtonSizeFor(wm, closeView, closeLp, sizePx)
        updateButtonSizeFor(wm, dragToggleView, dragToggleLp, sizePx)
        updateButtonSizeFor(wm, navToggleView, navToggleLp, sizePx)
        updateButtonSizeFor(wm, hideToggleView, hideToggleLp, sizePx)
        updateButtonSizeFor(wm, swapView, swapLp, sizePx)
        updateButtonSizeFor(wm, lightToggleView, lightToggleLp, sizePx)
        updateButtonSizeFor(wm, mirrorToggleView, mirrorToggleLp, sizePx)
        updateButtonSizeFor(wm, clickView, clickLp, sizePx)
        updateButtonSizeFor(wm, rightClickView, rightClickLp, sizePx)
    }

    // updateButtonSizeFor.
    private fun updateButtonSizeFor(
        wm: WindowManager,
        view: View?,
        lp: WindowManager.LayoutParams?,
        sizePx: Int
    ) {
        if (view == null || lp == null) return
        if (lp.width == sizePx && lp.height == sizePx) return
        lp.width = sizePx
        lp.height = sizePx
        val bg = view.background as? GradientDrawable
        bg?.cornerRadius = sizePx / 2f
        try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
    }

    // createButtonLayoutParams.
    private fun createButtonLayoutParams(
        key: String,
        sizePx: Int,
        defaultX: Int,
        defaultY: Int
    ): WindowManager.LayoutParams {
        val (x, y) = loadPosition(key, defaultX, defaultY)
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    // updateDragToggleAppearance.
    private fun updateDragToggleAppearance() {
        val button = dragToggleView ?: return
        val bg = button.background as? GradientDrawable ?: return
        if (dragModeEnabled) {
            bg.setColor(Color.argb(160, 120, 190, 255))
        } else {
            bg.setColor(Color.argb(110, 60, 60, 60))
        }
    }

    // updateDragModeVisuals.
    private fun updateDragModeVisuals() {
        if (shouldHideControlsForLightOff()) {
            applyHideOverlaysVisuals(true, getLightOffKeepVisibleButton())
            return
        }
        if (hideOverlays) return
        val baseButtonAlpha = (buttonOpacity.coerceIn(0, 100) * 255 / 100)
        val buttonAlpha = if (dragModeEnabled) 255 else baseButtonAlpha
        val closeAlpha = if (dragModeEnabled) 255 else baseButtonAlpha
        val cardAlpha = if (dragModeEnabled) 1f else (trackpadOpacity.coerceIn(0, 100) / 100f)
        val padAlpha = if (dragModeEnabled) 1f else (trackpadOpacity.coerceIn(0, 100) / 100f)

        for (view in floatingButtons) {
            val bg = view.background as? GradientDrawable ?: continue
            val useAlpha = if (view === closeView) closeAlpha else buttonAlpha
            bg.alpha = useAlpha
            if (view is ImageButton) {
                view.imageAlpha = useAlpha
            }
        }
        for (view in trackpadHeaderButtons) {
            val bg = view.background as? GradientDrawable ?: continue
            bg.alpha = buttonAlpha
            view.imageAlpha = buttonAlpha
        }

        for (card in trackpadCards) {
            if (!isTrackpadCardEnabled(card)) {
                card.alpha = 0f
                card.visibility = View.GONE
                continue
            }
            val bg = card.background as? GradientDrawable ?: continue
            bg.alpha = (cardAlpha * 255).toInt().coerceIn(0, 255)
            card.alpha = cardAlpha
            card.visibility = View.VISIBLE
        }

        for (pad in trackpadSurfaces) {
            val bg = pad.background as? GradientDrawable ?: continue
            bg.alpha = (padAlpha * 255).toInt().coerceIn(0, 255)
            pad.alpha = padAlpha
        }

        val handleVisible = dragModeEnabled
        for (handle in trackpadResizeHandles) {
            handle.visibility = if (handleVisible) View.VISIBLE else View.GONE
            handle.alpha = if (handleVisible) 1f else 0f
        }
        for (label in trackpadLabels) {
            label.visibility = if (handleVisible) View.VISIBLE else View.GONE
            label.alpha = if (handleVisible) 1f else 0f
        }

        updateTrackpadHeaderVisibility(!showNavButtons)
    }

    // toggleHideOverlays.
    private fun toggleHideOverlays() {
        hideOverlays = !hideOverlays
        updateHideOverlaysVisuals()
    }

    // toggleLightOverlay.
    private fun toggleLightOverlay() {
        lightOverlayEnabled = !lightOverlayEnabled
        uiPrefs.edit { putBoolean("light_overlay_enabled", lightOverlayEnabled) }
        updateLightToggleAppearance()
        updateLightOverlayVisibility()
        updateLightPrimaryButtonVisibility()
        updateHideOverlaysVisuals()
    }

    // ensureLightOverlay.
    private fun ensureLightOverlay(ctx: Context) {
        if (lightOverlayView != null) return
        lightOverlayView = View(ctx).apply {
            setBackgroundColor(Color.argb(255, 0, 0, 0))
            alpha = 1f
            visibility = View.GONE
        }
        lightOverlayLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        safeAddView(padWm, lightOverlayView, lightOverlayLp)
    }

    // updateLightOverlayVisibility.
    private fun updateLightOverlayVisibility() {
        val view = lightOverlayView ?: return
        val shouldShow = lightOverlayEnabled
        view.visibility = if (shouldShow) View.VISIBLE else View.GONE
        view.alpha = if (shouldShow) 1f else 0f
    }

    // Light Off Mode control-visibility logic removed for later redesign.

    private fun stopOverlayAndSelf() {
        uiPrefs.edit { putBoolean("overlay_running", false) }
        detachTrackpad()
        detachTrackpadOverlay()
        detachCursor()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // updateHideOverlaysVisuals.
    private fun updateHideOverlaysVisuals() {
        if (shouldHideControlsForLightOff()) {
            applyHideOverlaysVisuals(true, getLightOffKeepVisibleButton())
            return
        }
        applyHideOverlaysVisuals(hideOverlays, hideToggleView)
        updateHideToggleIcon()
    }

    // applyHideOverlaysVisuals.
    private fun applyHideOverlaysVisuals(hide: Boolean, keepVisible: View?) {
        val baseButtonAlpha = (buttonOpacity.coerceIn(0, 100) * 255 / 100)
        val basePadAlpha = (trackpadOpacity.coerceIn(0, 100) / 100f)
        val buttonAlpha = if (hide) 0 else baseButtonAlpha
        val closeAlpha = if (hide) 0 else baseButtonAlpha
        val cardAlpha = if (hide) 0f else basePadAlpha
        val padAlpha = if (hide) 0f else basePadAlpha

        for (view in floatingButtons) {
            if (view === keepVisible) {
                val bg = view.background as? GradientDrawable
                bg?.alpha = baseButtonAlpha
                if (view is ImageButton) {
                    view.imageAlpha = baseButtonAlpha
                }
                view.alpha = 1f
                view.visibility = View.VISIBLE
                continue
            }
            val bg = view.background as? GradientDrawable ?: continue
            val useAlpha = if (view === closeView) closeAlpha else buttonAlpha
            bg.alpha = useAlpha
            if (view is ImageButton) {
                view.imageAlpha = useAlpha
            }
            view.alpha = if (hide) 0f else 1f
            view.visibility = View.VISIBLE
        }

        for (card in trackpadCards) {
            if (!isTrackpadCardEnabled(card)) {
                card.alpha = 0f
                card.visibility = View.GONE
                continue
            }
            val bg = card.background as? GradientDrawable ?: continue
            bg.alpha = (cardAlpha * 255).toInt().coerceIn(0, 255)
            card.alpha = cardAlpha
            card.visibility = View.VISIBLE
        }

        for (pad in trackpadSurfaces) {
            val bg = pad.background as? GradientDrawable ?: continue
            bg.alpha = (padAlpha * 255).toInt().coerceIn(0, 255)
            pad.alpha = padAlpha
            pad.visibility = View.VISIBLE
        }

        val handleVisible = !hide && dragModeEnabled
        for (handle in trackpadResizeHandles) {
            handle.visibility = if (handleVisible) View.VISIBLE else View.GONE
            handle.alpha = if (handleVisible) 1f else 0f
        }
        for (label in trackpadLabels) {
            label.visibility = if (handleVisible) View.VISIBLE else View.GONE
            label.alpha = if (handleVisible) 1f else 0f
        }

        val headerVisible = !hide && !showNavButtons
        for (header in trackpadHeaderRows) {
            header.visibility = if (headerVisible) View.VISIBLE else View.GONE
            header.alpha = if (headerVisible) 1f else 0f
        }

        dragToggleView?.visibility = if (hide) View.GONE else View.VISIBLE
        navToggleView?.visibility = if (hide) View.GONE else View.VISIBLE
        dragToggleView?.isEnabled = !hide
        navToggleView?.isEnabled = !hide
        closeView?.isEnabled = !hide
        closeView?.isClickable = !hide
    }

    // toggleClickThrough.
    private fun toggleClickThrough() {
        clickThroughEnabled = !clickThroughEnabled
        updateClickThrough()
    }

    // updateClickThrough.
    private fun updateClickThrough() {
        val wm = padWm ?: return
        updateClickThroughFor(wm, padViewA, padLpA, clickThroughEnabled)
        updateClickThroughFor(wm, padViewB, padLpB, clickThroughEnabled)
        updateClickThroughFor(wm, backView, backLp, clickThroughEnabled)
        updateClickThroughFor(wm, homeView, homeLp, clickThroughEnabled)
        updateClickThroughFor(wm, recentsView, recentsLp, clickThroughEnabled)
        updateClickThroughFor(wm, closeView, closeLp, clickThroughEnabled)
        updateClickThroughFor(wm, dragToggleView, dragToggleLp, clickThroughEnabled)
        updateClickThroughFor(wm, navToggleView, navToggleLp, clickThroughEnabled)
        updateClickThroughFor(wm, swapView, swapLp, clickThroughEnabled)
        updateClickThroughFor(wm, lightToggleView, lightToggleLp, clickThroughEnabled)
        updateClickThroughFor(wm, mirrorToggleView, mirrorToggleLp, clickThroughEnabled)
        updateClickThroughFor(wm, clickView, clickLp, clickThroughEnabled)
        updateClickThroughFor(wm, rightClickView, rightClickLp, clickThroughEnabled)
        // Keep hideToggleView clickable so it can be turned back on.
    }

    // updateClickThroughFor.
    private fun updateClickThroughFor(
        wm: WindowManager,
        view: View?,
        lp: WindowManager.LayoutParams?,
        enable: Boolean
    ) {
        if (view == null || lp == null) return
        val newFlags = if (enable) {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        if (newFlags == lp.flags) return
        lp.flags = newFlags
        try {
            wm.updateViewLayout(view, lp)
        } catch (_: Throwable) {
        }
    }

    // bringFloatingButtonsToFront.
    private fun bringFloatingButtonsToFront() {
        val wm = padWm ?: return
        readdFloatingButton(wm, backView, backLp)
        readdFloatingButton(wm, homeView, homeLp)
        readdFloatingButton(wm, recentsView, recentsLp)
        readdFloatingButton(wm, closeView, closeLp)
        readdFloatingButton(wm, dragToggleView, dragToggleLp)
        readdFloatingButton(wm, navToggleView, navToggleLp)
        readdFloatingButton(wm, hideToggleView, hideToggleLp)
        readdFloatingButton(wm, swapView, swapLp)
        readdFloatingButton(wm, lightToggleView, lightToggleLp)
        readdFloatingButton(wm, mirrorToggleView, mirrorToggleLp)
        readdFloatingButton(wm, clickView, clickLp)
        readdFloatingButton(wm, rightClickView, rightClickLp)
    }

    // readdFloatingButton.
    private fun readdFloatingButton(
        wm: WindowManager,
        view: View?,
        lp: WindowManager.LayoutParams?
    ) {
        if (view == null || lp == null) return
        try {
            if (view.parent != null) {
                safeRemoveView(wm, view)
            }
        } catch (_: Throwable) {
        }
        try {
            safeAddView(wm, view, lp)
        } catch (_: Throwable) {
        }
    }

    // updateHideToggleIcon.
    private fun updateHideToggleIcon() {
        val icon = if (hideOverlays) R.drawable.ic_eye_closed else R.drawable.ic_eye_open
        hideToggleView?.setImageResource(icon)
    }

    // updateLightToggleAppearance.
    private fun updateLightToggleAppearance() {
        val button = lightToggleView ?: return
        val bg = button.background as? GradientDrawable ?: return
        bg.setColor(Color.argb(110, 60, 60, 60))
        if (lightOverlayEnabled) {
            button.setImageResource(R.drawable.ic_light_bulb)
        } else {
            button.setImageResource(R.drawable.ic_light_bulb_off)
        }
    }

    // updateLightPrimaryButtonAppearance.
    private fun updateLightPrimaryButtonAppearance() {
        val button = lightPrimaryView ?: return
        val bg = button.background as? GradientDrawable ?: return
        bg.setColor(Color.argb(110, 60, 60, 60))
        if (lightOverlayEnabled) {
            button.setImageResource(R.drawable.ic_light_bulb)
        } else {
            button.setImageResource(R.drawable.ic_light_bulb_off)
        }
    }

    // ensureLightPrimaryButton.
    private fun ensureLightPrimaryButton(ctx: Context) {
        if (lightPrimaryView != null) return
        val metrics = ctx.resources.displayMetrics
        val sizePx = getButtonSizePx(metrics)
        lightPrimaryView = ImageButton(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = (sizePx / 2f)
                setColor(Color.argb(110, 60, 60, 60))
            }
            setImageResource(R.drawable.ic_light_bulb)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener {
                if (uiPrefs.getBoolean("haptic_button_press", true)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                toggleLightOverlay()
            }
        }
        lightPrimaryView?.setOnTouchListener(
            PrimaryLightDragTouchListener(
                { lightPrimaryLp },
                { lp ->
                    lightPrimaryLp = lp
                    try { lightPrimaryWm?.updateViewLayout(lightPrimaryView, lp) } catch (_: Throwable) {}
                }
            )
        )
        lightPrimaryLp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val (savedX, savedY) = loadPosition("light_primary", dp(metrics, 16), dp(metrics, 16))
            x = savedX
            y = savedY
        }
        lightPrimaryWm = ctx.getSystemService(WINDOW_SERVICE) as WindowManager
        updateLightPrimaryButtonAppearance()
    }

    private inner class PrimaryLightDragTouchListener(
        private val getLp: () -> WindowManager.LayoutParams?,
        private val update: (WindowManager.LayoutParams) -> Unit
    ) : View.OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var touchSlop = 0

        // onTouch.
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = getLp() ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX
                    lastY = e.rawY
                    moved = false
                    if (touchSlop == 0) {
                        touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY
                    lastX = e.rawX
                    lastY = e.rawY

                    if (!moved &&
                        (abs(dx) >= touchSlop || abs(dy) >= touchSlop)) {
                        moved = true
                    }

                    if (moved) {
                        val (maxX, maxY) = getDragBounds(v, lp)
                        val newX = (lp.x + dx).toInt().coerceIn(0, maxX.coerceAtLeast(0))
                        val newY = (lp.y + dy).toInt().coerceIn(0, maxY.coerceAtLeast(0))
                        lp.x = newX
                        lp.y = newY
                        update(lp)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) {
                        v.performClick()
                    } else {
                        savePosition("light_primary", lp.x, lp.y)
                    }
                    return true
                }
            }
            return false
        }
    }

    // detachLightPrimaryButton.
    private fun detachLightPrimaryButton() {
        val wm = lightPrimaryWm
        val view = lightPrimaryView
        if (wm != null && view != null) {
            try {
                if (view.parent != null) {
                    safeRemoveViewImmediate(wm, view)
                }
            } catch (_: Throwable) {
            }
        }
        lightPrimaryView = null
        lightPrimaryLp = null
        lightPrimaryWm = null
    }

    // updateLightPrimaryButtonVisibility.
    private fun updateLightPrimaryButtonVisibility() {
        val shouldShow = lightOverlayEnabled && lightOffHideBottomButton && lightOffPrimaryButton
        if (!shouldShow) {
            val view = lightPrimaryView
            if (view != null && view.parent != null) {
                safeRemoveView(lightPrimaryWm, view)
            }
            return
        }
        val wm = lightPrimaryWm ?: return
        val view = lightPrimaryView ?: return
        val lp = lightPrimaryLp ?: return
        if (view.parent == null) {
            safeAddView(wm, view, lp)
        } else {
            try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
        }
        updateLightPrimaryButtonAppearance()
    }

    // updateLightPrimaryButtonSize.
    private fun updateLightPrimaryButtonSize() {
        val view = lightPrimaryView ?: return
        val lp = lightPrimaryLp ?: return
        val metrics = view.resources.displayMetrics
        val sizePx = getButtonSizePx(metrics)
        if (lp.width == sizePx && lp.height == sizePx) return
        lp.width = sizePx
        lp.height = sizePx
        val bg = view.background as? GradientDrawable
        bg?.cornerRadius = sizePx / 2f
        if (view.parent != null) {
            try { lightPrimaryWm?.updateViewLayout(view, lp) } catch (_: Throwable) {}
        }
    }

    // getDragBounds.
    private fun getDragBounds(v: View, lp: WindowManager.LayoutParams): Pair<Int, Int> {
        val display = v.display
        val metrics = DisplayMetrics()
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
        } else {
            metrics.setTo(v.resources.displayMetrics)
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val maxX = width - lp.width
        val maxY = height - lp.height
        return maxX to maxY
    }

    // getDisplaySize.
    private fun getDisplaySize(v: View): Pair<Int, Int> {
        val display = v.display
        val metrics = DisplayMetrics()
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
        } else {
            metrics.setTo(v.resources.displayMetrics)
        }
        return metrics.widthPixels to metrics.heightPixels
    }

    // applyTrackpadSizes.
    private fun applyTrackpadSizes(
        wm: WindowManager,
        metrics: DisplayMetrics
    ) {
        updateTrackpadSize(padViewA, padLpA, "trackpad_right", wm, metrics)
        updateTrackpadSize(padViewB, padLpB, "trackpad_left", wm, metrics)
    }

    // updateTrackpadSize.
    private fun updateTrackpadSize(
        view: View?,
        lp: WindowManager.LayoutParams?,
        sizeKey: String,
        wm: WindowManager,
        metrics: DisplayMetrics
    ) {
        if (view == null || lp == null) return
        val defaultW = dp(metrics, 200)
        val defaultH = dp(metrics, 200)
        val (rawW, rawH) = loadTrackpadSize(sizeKey, defaultW, defaultH)
        val (coercedW, coercedH) = clampTrackpadSize(view, rawW, rawH)
        if (coercedW != rawW || coercedH != rawH) {
            saveTrackpadSize(sizeKey, coercedW, coercedH)
        }
        if (lp.width != coercedW || lp.height != coercedH) {
            lp.width = coercedW
            lp.height = coercedH
            try {
                wm.updateViewLayout(view, lp)
                applyOverlayPositions()
            } catch (_: Throwable) {
            }
        }
    }

    // clampTrackpadSize.
    private fun clampTrackpadSize(view: View, width: Int, height: Int): Pair<Int, Int> {
        val (displayW, displayH) = getDisplaySize(view)
        val minSize = dp(view.resources.displayMetrics, 120)
        val maxW = displayW.coerceAtLeast(minSize)
        val maxH = displayH.coerceAtLeast(minSize)
        val w = width.coerceIn(minSize, maxW)
        val h = height.coerceIn(minSize, maxH)
        return w to h
    }

    // clampTrackpadSize.
    private fun clampTrackpadSize(
        metrics: DisplayMetrics,
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        val minSize = dp(metrics, 120)
        val maxW = metrics.widthPixels.coerceAtLeast(minSize)
        val maxH = metrics.heightPixels.coerceAtLeast(minSize)
        val w = width.coerceIn(minSize, maxW)
        val h = height.coerceIn(minSize, maxH)
        return w to h
    }

    // toggleNavButtons.
    private fun toggleNavButtons() {
        val current = uiPrefs.getBoolean("nav_buttons_enabled", true)
        uiPrefs.edit { putBoolean("nav_buttons_enabled", !current) }
    }

    // showNavButtonCluster.
    private fun showNavButtonCluster(
        ctx: Context,
        wm: WindowManager,
        sizePx: Int,
        gapPx: Int,
        baseX: Int,
        baseY: Int,
        defaultColor: Int,
        closeColor: Int
    ) {
        if (backView != null || homeView != null || recentsView != null || closeView != null) return

        val showBack = uiPrefs.getBoolean("show_back_btn", true)
        val showHome = uiPrefs.getBoolean("show_home_btn", true)
        val showRecents = uiPrefs.getBoolean("show_recents_btn", true)
        val showStop = uiPrefs.getBoolean("show_stop_btn", true)

        var slot = 0
        if (showBack) {
            backLp = createButtonLayoutParams("nav_back", sizePx, baseX + slot * (sizePx + gapPx), baseY)
            backView = createFloatingButton(ctx, R.drawable.ic_back, sizePx, defaultColor) {
                PointerAccessibilityService.instance
                    ?.performGlobalAction(GLOBAL_ACTION_BACK)
            }
            backView?.setOnTouchListener(
                FloatingButtonDragTouchListener("nav_back", { backLp }, { lp ->
                    backLp = lp
                    wm.updateViewLayout(backView, lp)
                })
            )
        safeAddView(wm, backView, backLp)
            slot++
        }
        if (showHome) {
            homeLp = createButtonLayoutParams("nav_home", sizePx, baseX + slot * (sizePx + gapPx), baseY)
            homeView = createFloatingButton(ctx, R.drawable.ic_home, sizePx, defaultColor) {
                PointerAccessibilityService.instance
                    ?.performGlobalAction(GLOBAL_ACTION_HOME)
            }
            homeView?.setOnTouchListener(
                FloatingButtonDragTouchListener("nav_home", { homeLp }, { lp ->
                    homeLp = lp
                    wm.updateViewLayout(homeView, lp)
                })
            )
        safeAddView(wm, homeView, homeLp)
            slot++
        }
        if (showRecents) {
            recentsLp = createButtonLayoutParams("nav_recents", sizePx, baseX + slot * (sizePx + gapPx), baseY)
            recentsView = createFloatingButton(ctx, R.drawable.ic_menu, sizePx, defaultColor) {
                PointerAccessibilityService.instance
                    ?.performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            recentsView?.setOnTouchListener(
                FloatingButtonDragTouchListener("nav_recents", { recentsLp }, { lp ->
                    recentsLp = lp
                    wm.updateViewLayout(recentsView, lp)
                })
            )
        safeAddView(wm, recentsView, recentsLp)
            slot++
        }
        if (showStop) {
            closeLp = createButtonLayoutParams("nav_close", sizePx, baseX + slot * (sizePx + gapPx), baseY)
            closeView = createFloatingButton(ctx, android.R.drawable.ic_menu_close_clear_cancel, sizePx, closeColor) {
                // Close only the trackpad overlay, keep cursor/service alive
                detachTrackpad()
                detachTrackpadOverlay()
                detachCursor()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            closeView?.setOnTouchListener(
                FloatingButtonDragTouchListener("nav_close", { closeLp }, { lp ->
                    closeLp = lp
                    wm.updateViewLayout(closeView, lp)
                })
            )
            safeAddView(wm, closeView, closeLp)
        }

        updateClickThrough()
    }

    // loadPosition.
    private fun loadPosition(key: String, defaultX: Int, defaultY: Int): Pair<Int, Int> {
        val prefs = getSharedPreferences("floating_positions", MODE_PRIVATE)
        val x = prefs.getInt("${key}_x", defaultX)
        val y = prefs.getInt("${key}_y", defaultY)
        return x to y
    }

    // loadTrackpadSize.
    private fun loadTrackpadSize(key: String, defaultW: Int, defaultH: Int): Pair<Int, Int> {
        val w = uiPrefs.getInt("${key}_width", -1)
        val h = uiPrefs.getInt("${key}_height", -1)
        if (w > 0 && h > 0) {
            return w to h
        }
        val legacyPrefs = getSharedPreferences("floating_sizes", MODE_PRIVATE)
        val legacyW = legacyPrefs.getInt("pad_size_w", defaultW)
        val legacyH = legacyPrefs.getInt("pad_size_h", defaultH)
        return legacyW to legacyH
    }

    // savePosition.
    private fun savePosition(key: String, x: Int, y: Int) {
        val prefs = getSharedPreferences("floating_positions", MODE_PRIVATE)
        prefs.edit {
            putInt("${key}_x", x)
                .putInt("${key}_y", y)
        }
    }

    // saveTrackpadSize.
    private fun saveTrackpadSize(key: String, width: Int, height: Int) {
        uiPrefs.edit {
            putInt("${key}_width", width)
                .putInt("${key}_height", height)
        }
    }

    // getPrimaryDisplayContext.
    private fun getPrimaryDisplayContext(): Context {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val primary = dm.getDisplay(Display.DEFAULT_DISPLAY)
        return if (primary != null) {
            createDisplayContext(primary)
        } else {
            this
        }
    }

    // getRealBoundsOrFallback.
    private fun getRealBoundsOrFallback(
        primary: Display?,
        displayCtx: Context
    ): Pair<Int, Int> {
        // Try WindowManager.currentWindowMetrics via reflection to avoid SDK checks.
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val currentWindowMetrics = wm.javaClass.getMethod("getCurrentWindowMetrics").invoke(wm)
            val bounds = currentWindowMetrics.javaClass.getMethod("getBounds").invoke(currentWindowMetrics)
            val width = bounds.javaClass.getMethod("width").invoke(bounds) as Int
            val height = bounds.javaClass.getMethod("height").invoke(bounds) as Int
            return width to height
        } catch (_: Throwable) {
        }

        if (primary != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            primary.getRealMetrics(metrics)
            return metrics.widthPixels to metrics.heightPixels
        }

        val metrics = displayCtx.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    // safeAddView.
    private fun safeAddView(
        wm: WindowManager?,
        view: View?,
        lp: WindowManager.LayoutParams?
    ) {
        if (wm == null || view == null || lp == null) return
        try {
            wm.addView(view, lp)
        } catch (_: Throwable) {
        }
    }

    // safeUpdateViewLayout.
    private fun safeUpdateViewLayout(
        wm: WindowManager?,
        view: View?,
        lp: WindowManager.LayoutParams?
    ) {
        if (wm == null || view == null || lp == null) return
        try {
            wm.updateViewLayout(view, lp)
        } catch (_: Throwable) {
        }
    }

    // safeRemoveView.
    private fun safeRemoveView(
        wm: WindowManager?,
        view: View?
    ) {
        if (wm == null || view == null) return
        try {
            wm.removeView(view)
        } catch (_: Throwable) {
        }
    }

    // safeRemoveViewImmediate.
    private fun safeRemoveViewImmediate(
        wm: WindowManager?,
        view: View?
    ) {
        if (wm == null || view == null) return
        try {
            wm.removeViewImmediate(view)
        } catch (_: Throwable) {
        }
    }

    // Keep your existing notification builder here
    private fun buildNotification(): Notification {
        val channelId = PointerConstants.Notification.CHANNEL_ID
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val ch = NotificationChannel(
            channelId,
            PointerConstants.Notification.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(ch)

        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (PendingIntent.FLAG_IMMUTABLE)
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // MUST be set
            .setContentTitle(PointerConstants.Notification.TITLE)
            .setContentText("Overlay + accessibility enabled")
            .setContentIntent(pi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // shouldHideControlsForLightOff.
    private fun shouldHideControlsForLightOff(): Boolean {
        return lightOverlayEnabled && lightOffKeepControls
    }

    // getLightOffKeepVisibleButton.
    private fun getLightOffKeepVisibleButton(): View? {
        return if (lightOffHideBottomButton) null else lightToggleView
    }
}
