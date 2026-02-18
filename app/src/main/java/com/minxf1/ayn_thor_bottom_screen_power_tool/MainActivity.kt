package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.text.InputType
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import kotlin.math.roundToInt
import androidx.core.content.edit
import com.example.ayn_thor_bottom_screen_power_tool.R
import kotlin.collections.get
import kotlin.collections.iterator

class MainActivity : ComponentActivity() {
    private var pendingStart = false
    private val sizeInputs = mutableMapOf<String, EditText>()
    private var sizePrefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var startBtn: Button
    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportSettingsToUri(uri)
        }
    }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importSettingsFromUri(uri)
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (pendingStart) {
            proceedStartFlow()
        }
    }

    // onCreate.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDefaultsFromAsset()

        // Root container and global padding/theme colors.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiConstants.Spacing.SCREEN_PADDING,
                UiConstants.Spacing.SCREEN_PADDING,
                UiConstants.Spacing.SCREEN_PADDING,
                UiConstants.Spacing.SCREEN_PADDING
            )
            setBackgroundColor(UiConstants.Colors.BLACK)
        }

        root.addView(buildKofiBanner())
        root.addView(space(UiConstants.Spacing.SMALL_GAP))

        buildPermissionNotice()?.let { notice ->
            root.addView(notice)
            root.addView(space(UiConstants.Spacing.SMALL_GAP))
        }

        startBtn = Button(this)
        updateOverlayButtonState()

        root.addView(startBtn)
        root.addView(space(UiConstants.Spacing.SMALL_GAP))
        root.addView(buildButtonsMenu())

        // Scroll container for the entire settings UI.
        val scroll = ScrollView(this).apply {
            setBackgroundColor(UiConstants.Colors.BLACK)
            addView(root)
        }

        setContentView(scroll)

        val sizePrefs = getSharedPreferences("ui_config", MODE_PRIVATE)
        sizePrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val edit = sizeInputs[key] ?: return@OnSharedPreferenceChangeListener
            if (edit.hasFocus()) return@OnSharedPreferenceChangeListener
            val value = sizePrefs.getInt(key, edit.text.toString().toIntOrNull() ?: 0)
            if (edit.text.toString() != value.toString()) {
                edit.setText(value.toString())
            }
        }
        sizePrefs.registerOnSharedPreferenceChangeListener(sizePrefListener)
    }

    // onResume.
    override fun onResume() {
        super.onResume()
        updateOverlayButtonState()
        if (pendingStart) {
            proceedStartFlow()
        }
    }

    // onDestroy.
    override fun onDestroy() {
        sizePrefListener?.let {
            getSharedPreferences("ui_config", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        sizePrefListener = null
        super.onDestroy()
    }

    // requestOverlayPermission.
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }
    }

    // openAccessibilitySettings.
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // isPointerA11yEnabled.
    private fun isPointerA11yEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name.contains("PointerAccessibilityService") }
    }

    // buildPermissionNotice.
    private fun buildPermissionNotice(): LinearLayout? {
        val overlayMissing = !Settings.canDrawOverlays(this)
        val a11yMissing = !isPointerA11yEnabled()
        val notifMissing = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (!overlayMissing && !a11yMissing && !notifMissing) return null

        // Simple permission explainer card shown only when something is missing.
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)
        }

        val title = TextView(this).apply {
            text = "Read before using the app, there are a few permissions needed and here is how the app use them"
            textSize = UiConstants.Text.SECTION
            setTextColor(UiConstants.Colors.WHITE)
        }

        val bullets = mutableListOf<String>().apply {
            if (overlayMissing) {
                add("Overlay permission to draw the floating controls.")
            }
            if (a11yMissing) {
                add("Accessibility service to send back/home/recents actions for the cursor.")
            }
            if (notifMissing) {
                add("Notification permission to show the foreground service status and also keep the app service stable.")
            }
        }

        container.addView(title)
        container.addView(space(UiConstants.Spacing.TINY_GAP))
        for (item in bullets) {
            val line = TextView(this).apply {
                text = "- $item"
                textSize = UiConstants.Text.SUBTITLE
                setTextColor(UiConstants.Colors.TEXT_SECONDARY)
            }
            container.addView(line)
        }
        return container
    }

    // proceedStartFlow.
    private fun proceedStartFlow() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        if (!isPointerA11yEnabled()) {
            openAccessibilitySettings()
            return
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // Start cursor overlay service
        startForegroundService(Intent(this, PointerService::class.java))
        getSharedPreferences("ui_config", MODE_PRIVATE)
            .edit {
                putBoolean("overlay_running", true)
            }
        updateOverlayButtonState()
        pendingStart = false

        // Launch trackpad on secondary display if available; otherwise launch on primary
//        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
//        val secondary = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

//        val intent = Intent(this, TrackpadActivity::class.java).apply {
//            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        }

//        if (secondary != null) {
//            val opts = ActivityOptions.makeBasic().apply {
//                launchDisplayId = secondary.displayId
//            }
//            startActivity(intent, opts.toBundle())
//        } else {
//            startActivity(intent)
//        }
    }

    // ensureDefaultsFromAsset.
    private fun ensureDefaultsFromAsset() {
        val uiPrefs = getSharedPreferences("ui_config", MODE_PRIVATE)
        if (uiPrefs.getBoolean("defaults_loaded", false)) return
        if (uiPrefs.all.isNotEmpty()) {
            uiPrefs.edit { putBoolean("defaults_loaded", true) }
            return
        }
        try {
            val json = assets.open("default_config.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            root.optJSONObject("ui_config")?.let { applyJsonToPrefs(it, uiPrefs) }
            root.optJSONObject("floating_positions")?.let {
                applyJsonToPrefs(it, getSharedPreferences("floating_positions", MODE_PRIVATE))
            }
            root.optJSONObject("mirror_positions")?.let {
                applyJsonToPrefs(it, getSharedPreferences("mirror_positions", MODE_PRIVATE))
            }
            uiPrefs.edit().putBoolean("defaults_loaded", true).apply()
        } catch (_: Throwable) {
        }
    }

    // updateOverlayButtonState.
    private fun updateOverlayButtonState() {
        val running = getSharedPreferences("ui_config", MODE_PRIVATE)
            .getBoolean("overlay_running", false)
        if (running) {
            startBtn.text = "Stop Overlay"
            startBtn.setOnClickListener {
                pendingStart = false
                getSharedPreferences("ui_config", MODE_PRIVATE)
                    .edit()
                    .putBoolean("overlay_running", false)
                    .apply()
                val intent = Intent(this, PointerService::class.java)
                    .setAction("STOP_OVERLAY")
                startService(intent)
                updateOverlayButtonState()
            }
        } else {
            startBtn.text = "Start Overlay"
            startBtn.setOnClickListener {
                pendingStart = true
                proceedStartFlow()
            }
        }
    }

    // buildButtonsMenu.
    private fun buildButtonsMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Top-level section and nested subgroups for button visibility.
        val sectionHeader = buildSectionHeader(
            title = "Buttons",
            subtitle = "Floating controls visibility and actions"
        )

        val options = buildOptionsContainer()

        val trackpadHeader = buildSubgroupHeaderRow(
            title = "Trackpad mode",
            subtitle = "Overlays for the trackpad controls",
            icon = R.drawable.trackpad_right,
            flipIcon = true
        )
        val trackpadOptions = buildToggleSection(trackpadHeader, listOf(
            ToggleSpec("show_nav_buttons", "Floating navigation toggle button", android.R.drawable.ic_menu_manage),
            ToggleSpec("show_back_btn", "Back button", R.drawable.ic_back),
            ToggleSpec("show_home_btn", "Home button", R.drawable.ic_home),
            ToggleSpec("show_recents_btn", "Recents button", R.drawable.ic_menu),
            ToggleSpec("show_drag_btn", "Drag toggle button", R.drawable.ic_drag),
            ToggleSpec("show_stop_btn", "Stop overlay button", android.R.drawable.ic_menu_close_clear_cancel),
            ToggleSpec("show_hide_btn", "Show/Hide toggle button", R.drawable.ic_eye_open),
            ToggleSpec("show_swap_btn", "Screen swap button - Experimental use at your own risk to be fixed in future", R.drawable.ic_swap),
            ToggleSpec("show_light_btn", "Light overlay toggle button", R.drawable.ic_light_bulb),
            ToggleSpec("show_mirror_btn", "Mirror mode toggle button", R.drawable.ic_mirror),
            ToggleSpec("show_click_btn", "Click button", R.drawable.ic_trackpad_click),
            ToggleSpec("show_right_click_btn", "Right-click button", R.drawable.ic_trackpad_right_click),
            ToggleSpec("show_trackpad_left", "Left trackpad", R.drawable.trackpad_right, flipIcon = true),
            ToggleSpec("show_trackpad_right", "Right trackpad", R.drawable.trackpad_right)
        ), prefs)
        options.addView(trackpadHeader)
        options.addView(trackpadOptions)

        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        val mirrorHeader = buildSubgroupHeaderRow(
            title = "Screen mirror mode",
            subtitle = "Buttons visible in mirror mode",
            icon = R.drawable.ic_mirror,
            flipIcon = false
        )
        val mirrorOptions = buildToggleSection(mirrorHeader, listOf(
            ToggleSpec("mirror_show_toggle", "Toggle mirror mode", R.drawable.ic_mirror),
            ToggleSpec("mirror_show_recents", "Recents button", R.drawable.ic_menu),
            ToggleSpec("mirror_show_back", "Back button", R.drawable.ic_back),
            ToggleSpec("mirror_show_home", "Home button", R.drawable.ic_home),
            ToggleSpec("mirror_show_drag", "Drag toggle button", R.drawable.ic_drag)
        ), prefs, paddingTopDp = UiConstants.Spacing.SUBGROUP_TOP)
        options.addView(mirrorHeader)
        options.addView(mirrorOptions)

        toggleVisibilityOnClick(sectionHeader, options)

        container.addView(sectionHeader)
        container.addView(options)
        container.addView(space(UiConstants.Spacing.SECTION_GAP))
        container.addView(buildOpacityMenu())
        container.addView(space(UiConstants.Spacing.SECTION_GAP))
        container.addView(buildBehaviorMenu())
        container.addView(space(UiConstants.Spacing.SECTION_GAP))
        container.addView(buildTrackpadSizeMenu())
        container.addView(space(UiConstants.Spacing.SECTION_GAP))
        container.addView(buildBackupImportMenu())
        return container
    }

    // buildKofiBanner.
    private fun buildKofiBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING
            )
            setBackgroundColor(UiConstants.Colors.BANNER_BG)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                val uri = Uri.parse("https://ko-fi.com/minxf1")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

            // Banner text and image.
            val textView = TextView(context).apply {
                text = "Buy me coffee if you love the app"
                textSize = UiConstants.Text.BANNER
                setTextColor(UiConstants.Colors.TEXT_PRIMARY)
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            addView(textView)

            val iconView = ImageView(context).apply {
                setImageResource(R.drawable.support_me_on_kofi_dark)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    dp(UiConstants.Sizes.BANNER_WIDTH),
                    dp(UiConstants.Sizes.BANNER_HEIGHT)
                ).apply {
                    marginStart = dp(UiConstants.Spacing.BANNER_ICON_MARGIN)
                }
            }
            addView(iconView)
        }
    }

    // buildSectionHeader.
    private fun buildSectionHeader(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING,
                UiConstants.Spacing.CARD_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE_DARK)
            // Title + subtitle stack for a section header.
            addView(TextView(context).apply {
                text = title
                textSize = UiConstants.Text.TITLE
                setTextColor(UiConstants.Colors.WHITE)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = UiConstants.Text.SUBTITLE
                setTextColor(UiConstants.Colors.TEXT_SECONDARY)
            })
        }
    }

    // buildOpacityMenu.
    private fun buildOpacityMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Opacity sliders are percentage-based.
        val sectionHeader = buildSectionHeader(
            title = "Opacity",
            subtitle = "Default transparency for overlays"
        )

        val options = buildOptionsContainer()

        options.addView(buildSliderRow("Button opacity", "button_opacity", prefs))
        options.addView(buildSliderRow("Trackpad opacity", "trackpad_opacity", prefs))

        toggleVisibilityOnClick(sectionHeader, options)

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    // buildTrackpadSizeMenu.
    private fun buildTrackpadSizeMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", MODE_PRIVATE)
        val bounds = getTrackpadSizeBounds()
        val buttonBounds = getButtonSizeBounds()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sectionHeader = buildSectionHeader(
            title = "Trackpad/button size",
            subtitle = "Set width and height for each trackpad"
        )

        val options = buildOptionsContainer()

        options.addView(buildGroupHeaderRow("Left trackpad", R.drawable.trackpad_right, flipIcon = true))
        options.addView(buildNumberInputRow("Width", "trackpad_left_width", prefs, bounds.minPx, bounds.maxWidthPx))
        options.addView(buildNumberInputRow("Height", "trackpad_left_height", prefs, bounds.minPx, bounds.maxHeightPx))

        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(buildGroupHeaderRow("Right trackpad", R.drawable.trackpad_right, flipIcon = false))
        options.addView(buildNumberInputRow("Width", "trackpad_right_width", prefs, bounds.minPx, bounds.maxWidthPx))
        options.addView(buildNumberInputRow("Height", "trackpad_right_height", prefs, bounds.minPx, bounds.maxHeightPx))

        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(buildSizeCopyButtons(prefs))

        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(buildGroupHeaderRow("Buttons", R.drawable.ic_menu, flipIcon = false))
        options.addView(buildNumberInputRow("Button size", "button_size", prefs, buttonBounds.minPx, buttonBounds.maxPx))

        toggleVisibilityOnClick(sectionHeader, options)

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    // buildBehaviorMenu.
    private fun buildBehaviorMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Grouped behavior controls (trackpad, light-off, haptics, mirror).
        val sectionHeader = buildSectionHeader(
            title = "Behavior",
            subtitle = "Focus and cursor tuning"
        )

        val options = buildOptionsContainer()

        options.addView(buildToggleRow(
            label = "Auto focus primary screen on touch",
            key = "auto_focus_primary_on_touch",
            icon = android.R.drawable.ic_menu_view,
            prefs = prefs
        ))
        val trackpadHeader = buildSubgroupHeaderRow(
            title = "Trackpad",
            subtitle = "Cursor and scroll tuning",
            icon = R.drawable.trackpad_right,
            flipIcon = true
        )
        val trackpadOptions = buildOptionsContainer(paddingTopDp = UiConstants.Spacing.SUBGROUP_TOP)
        toggleVisibilityOnClick(trackpadHeader, trackpadOptions)
        trackpadOptions.addView(buildFloatSliderRow(
            label = "Cursor sensitivity",
            key = "cursor_sensitivity",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_mylocation,
            minValue = UiConstants.Sliders.CURSOR_SENSITIVITY_MIN,
            maxValue = UiConstants.Sliders.CURSOR_SENSITIVITY_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_SENSITIVITY_DEFAULT
        ))
        trackpadOptions.addView(buildFloatSliderRow(
            label = "Scroll sensitivity",
            key = "scroll_sensitivity",
            prefs = prefs,
            icon = R.drawable.ic_scroll_sensitivity,
            minValue = UiConstants.Sliders.SCROLL_SENSITIVITY_MIN,
            maxValue = UiConstants.Sliders.SCROLL_SENSITIVITY_MAX,
            defaultValue = UiConstants.Sliders.SCROLL_SENSITIVITY_DEFAULT
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor fade delay (ms)",
            key = "cursor_fade_timeout_ms",
            prefs = prefs,
            icon = R.drawable.ic_cursor_fade_timeout,
            minValue = UiConstants.Sliders.CURSOR_FADE_MIN_MS,
            maxValue = UiConstants.Sliders.CURSOR_FADE_MAX_MS,
            defaultValue = UiConstants.Sliders.CURSOR_FADE_DEFAULT_MS
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor opacity (%)",
            key = "cursor_opacity",
            prefs = prefs,
            icon = R.drawable.ic_cursor_fade_timeout,
            minValue = UiConstants.Sliders.CURSOR_OPACITY_MIN,
            maxValue = UiConstants.Sliders.CURSOR_OPACITY_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_OPACITY_DEFAULT
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor color - Red",
            key = "cursor_color_r",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_edit,
            minValue = UiConstants.Sliders.CURSOR_COLOR_MIN,
            maxValue = UiConstants.Sliders.CURSOR_COLOR_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_COLOR_R_DEFAULT
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor color - Green",
            key = "cursor_color_g",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_edit,
            minValue = UiConstants.Sliders.CURSOR_COLOR_MIN,
            maxValue = UiConstants.Sliders.CURSOR_COLOR_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_COLOR_G_DEFAULT
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor color - Blue",
            key = "cursor_color_b",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_edit,
            minValue = UiConstants.Sliders.CURSOR_COLOR_MIN,
            maxValue = UiConstants.Sliders.CURSOR_COLOR_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_COLOR_B_DEFAULT
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor edge thickness (dp)",
            key = "cursor_edge_dp",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_crop,
            minValue = UiConstants.Sliders.CURSOR_EDGE_MIN_DP,
            maxValue = UiConstants.Sliders.CURSOR_EDGE_MAX_DP,
            defaultValue = UiConstants.Sliders.CURSOR_EDGE_DEFAULT_DP
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor edge color - Red",
            key = "cursor_edge_color_r",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_edit,
            minValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_MIN,
            maxValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_R_DEFAULT
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor edge color - Green",
            key = "cursor_edge_color_g",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_edit,
            minValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_MIN,
            maxValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_G_DEFAULT
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor edge color - Blue",
            key = "cursor_edge_color_b",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_edit,
            minValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_MIN,
            maxValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_MAX,
            defaultValue = UiConstants.Sliders.CURSOR_EDGE_COLOR_B_DEFAULT
        ))

        val trackpadModeHeader = buildSubgroupHeaderRow(
            title = "Trackpad mode",
            subtitle = "Behavior while using the trackpad",
            icon = R.drawable.ic_trackpad_click,
            flipIcon = false
        )
        val trackpadModeOptions = buildOptionsContainer(paddingTopDp = UiConstants.Spacing.SUBGROUP_TOP)
        toggleVisibilityOnClick(trackpadModeHeader, trackpadModeOptions)
        trackpadModeOptions.addView(buildToggleRow(
            label = "Click button hold for a right-click",
            key = "click_hold_right_click",
            icon = R.drawable.ic_trackpad_right_click,
            prefs = prefs
        ))
        trackpadModeOptions.addView(buildToggleRow(
            label = "Trackpad click muti-touch support",
            key = "trackpad_click_multitouch",
            icon = R.drawable.ic_trackpad_click,
            prefs = prefs
        ))

        val trackpadClickHeader = buildSubgroupHeaderRow(
            title = "Trackpad click sensitivity",
            subtitle = "Tap timing and distance thresholds",
            icon = R.drawable.ic_trackpad_click,
            flipIcon = false
        )
        val trackpadClickOptions =
            buildOptionsContainer(paddingTopDp = UiConstants.Spacing.SUBGROUP_TOP)
        toggleVisibilityOnClick(trackpadClickHeader, trackpadClickOptions)
        trackpadClickOptions.addView(buildIntSliderRow(
            label = "Tap timeout (ms)",
            key = "trackpad_click_timeout_ms",
            prefs = prefs,
            icon = R.drawable.ic_trackpad_click,
            minValue = UiConstants.Sliders.TRACKPAD_CLICK_TIMEOUT_MIN_MS,
            maxValue = UiConstants.Sliders.TRACKPAD_CLICK_TIMEOUT_MAX_MS,
            defaultValue = UiConstants.Sliders.TRACKPAD_CLICK_TIMEOUT_DEFAULT_MS
        ))
        trackpadClickOptions.addView(buildIntSliderRow(
            label = "Tap distance (px)",
            key = "trackpad_click_distance_px",
            prefs = prefs,
            icon = R.drawable.ic_trackpad_click,
            minValue = UiConstants.Sliders.TRACKPAD_CLICK_DISTANCE_MIN_PX,
            maxValue = UiConstants.Sliders.TRACKPAD_CLICK_DISTANCE_MAX_PX,
            defaultValue = UiConstants.Sliders.TRACKPAD_CLICK_DISTANCE_DEFAULT_PX
        ))

        val lightOffHeader = buildSubgroupHeaderRow(
            title = "Light Off Mode",
            subtitle = "Behavior when the light overlay is enabled",
            icon = R.drawable.ic_light_bulb,
            flipIcon = false
        )
        val lightOffOptions = buildOptionsContainer(paddingTopDp = UiConstants.Spacing.SUBGROUP_TOP)
        toggleVisibilityOnClick(lightOffHeader, lightOffOptions)
        lightOffOptions.addView(buildToggleRow(
            label = "Keep controller element ON when Light Off",
            key = "light_off_keep_controls",
            icon = R.drawable.ic_light_bulb,
            prefs = prefs
        ))
        var syncPrimaryEnabled: (() -> Unit)? = null
        val hideBottomRow = buildToggleRowWithSwitch(
            label = "Hide bottom screen light bulb button when light off",
            key = "light_off_hide_bottom_button",
            icon = R.drawable.ic_light_bulb,
            prefs = prefs,
            onCheckedChange = { syncPrimaryEnabled?.invoke() }
        )
        val showPrimaryRow = buildToggleRowWithSwitch(
            label = "Show light bulb button on primary screen when light off",
            key = "light_off_primary_button",
            icon = R.drawable.ic_light_bulb,
            prefs = prefs
        )
        lightOffOptions.addView(hideBottomRow.row)
        lightOffOptions.addView(showPrimaryRow.row)
        val hideSwitch = hideBottomRow.toggle
        val primarySwitch = showPrimaryRow.toggle
        syncPrimaryEnabled = {
            val hideBottom = hideSwitch.isChecked
            primarySwitch.isEnabled = hideBottom
            if (!hideBottom && primarySwitch.isChecked) {
                primarySwitch.isChecked = false
                prefs.edit().putBoolean("light_off_primary_button", false).apply()
            }
        }
        syncPrimaryEnabled.invoke()

        val hapticHeader = buildSubgroupHeaderRow(
            title = "Trackpad haptic feedback",
            subtitle = "Trackpad and overlay vibration controls",
            icon = R.drawable.ic_haptic_feedback,
            flipIcon = false
        )
        val hapticOptions = buildOptionsContainer(paddingTopDp = UiConstants.Spacing.SUBGROUP_TOP)
        toggleVisibilityOnClick(hapticHeader, hapticOptions)
        hapticOptions.addView(buildToggleRow(
            label = "Button haptic feedback",
            key = "haptic_button_press",
            icon = R.drawable.ic_haptic_feedback,
            prefs = prefs
        ))
        hapticOptions.addView(buildToggleRow(
            label = "Trackpad press haptic feedback",
            key = "haptic_trackpad_press",
            icon = R.drawable.ic_haptic_feedback,
            prefs = prefs
        ))
        hapticOptions.addView(buildToggleRow(
            label = "Trackpad nav button haptic feedback",
            key = "haptic_trackpad_nav_press",
            icon = R.drawable.ic_haptic_feedback,
            prefs = prefs
        ))

        val mirrorHeader = buildSubgroupHeaderRow(
            title = "Mirror mode",
            subtitle = "Mirror-only feedback controls",
            icon = R.drawable.ic_mirror,
            flipIcon = false
        )
        val mirrorOptions = buildOptionsContainer(paddingTopDp = UiConstants.Spacing.SUBGROUP_TOP)
        toggleVisibilityOnClick(mirrorHeader, mirrorOptions)
        mirrorOptions.addView(buildToggleRow(
            label = "Button haptic feedback",
            key = "haptic_mirror_buttons",
            icon = R.drawable.ic_haptic_feedback,
            prefs = prefs
        ))
        mirrorOptions.addView(buildToggleRow(
            label = "Render click",
            key = "mirror_render_click",
            icon = R.drawable.ic_mirror,
            prefs = prefs
        ))

        options.addView(trackpadHeader)
        options.addView(trackpadOptions)
        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(trackpadModeHeader)
        options.addView(trackpadModeOptions)
        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(trackpadClickHeader)
        options.addView(trackpadClickOptions)
        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(lightOffHeader)
        options.addView(lightOffOptions)
        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(hapticHeader)
        options.addView(hapticOptions)
        options.addView(space(UiConstants.Spacing.SMALL_GAP))
        options.addView(mirrorHeader)
        options.addView(mirrorOptions)

        toggleVisibilityOnClick(sectionHeader, options)

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    // buildBackupImportMenu.
    private fun buildBackupImportMenu(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Backup/import uses JSON document picker.
        val sectionHeader = buildSectionHeader(
            title = "Backup and import settings",
            subtitle = "Save or restore overlay configuration"
        )

        val options = buildOptionsContainer()

        val backupBtn = Button(this).apply {
            text = "Backup settings"
            setOnClickListener {
                backupLauncher.launch("overlay_settings.json")
            }
        }

        val importBtn = Button(this).apply {
            text = "Import settings"
            setOnClickListener {
                importLauncher.launch(arrayOf("application/json", "text/plain"))
            }
        }

        options.addView(backupBtn)
        options.addView(space(UiConstants.Spacing.TINY_GAP))
        options.addView(importBtn)

        toggleVisibilityOnClick(sectionHeader, options)

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    // buildGroupHeaderRow.
    private fun buildGroupHeaderRow(
        label: String,
        icon: Int,
        flipIcon: Boolean
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)

            // Single-line subgroup label with icon.
            val iconView = ImageView(this@MainActivity).apply {
                setImageResource(icon)
                setColorFilter(UiConstants.Colors.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(
                    dp(UiConstants.Sizes.ICON),
                    dp(UiConstants.Sizes.ICON)
                ).apply {
                    marginEnd = dp(UiConstants.Spacing.ICON_MARGIN)
                }
                if (flipIcon) {
                    scaleX = -1f
                }
            }

            val text = TextView(this@MainActivity).apply {
                textSize = UiConstants.Text.BODY
                text = label
                setTextColor(UiConstants.Colors.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            addView(iconView)
            addView(text)
        }
    }

    // buildNumberInputRow.
    private fun buildNumberInputRow(
        label: String,
        key: String,
        prefs: SharedPreferences,
        minValue: Int,
        maxValue: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)
        }

        // Numeric input with clamped commit on blur or IME action.
        val text = TextView(this).apply {
            textSize = UiConstants.Text.BODY
            text = label
            setTextColor(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(UiConstants.Colors.TEXT_PRIMARY)
            setBackgroundColor(UiConstants.Colors.INPUT_BG)
            setPadding(
                dp(UiConstants.Spacing.SMALL_GAP),
                dp(UiConstants.Spacing.TINY_GAP),
                dp(UiConstants.Spacing.SMALL_GAP),
                dp(UiConstants.Spacing.TINY_GAP)
            )
            val defaultValue = prefs.getInt(key, dp(UiConstants.Sizes.DEFAULT_TRACKPAD_SIZE))
            setText(defaultValue.toString())
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    commitSizeInput(this, key, minValue, maxValue, prefs)
                }
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitSizeInput(this, key, minValue, maxValue, prefs)
                    clearFocus()
                    true
                } else {
                    false
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                dp(UiConstants.Sizes.INPUT_WIDTH),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        sizeInputs[key] = input

        row.addView(text)
        row.addView(input)
        return row
    }

    // buildSubgroupHeaderRow.
    private fun buildSubgroupHeaderRow(
        title: String,
        subtitle: String,
        icon: Int,
        flipIcon: Boolean
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE_ALT)

            // Subgroup header for collapsible sections.
            val iconView = ImageView(this@MainActivity).apply {
                setImageResource(icon)
                setColorFilter(UiConstants.Colors.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(
                    dp(UiConstants.Sizes.ICON),
                    dp(UiConstants.Sizes.ICON)
                ).apply {
                    marginEnd = dp(UiConstants.Spacing.ICON_MARGIN)
                }
                if (flipIcon) {
                    scaleX = -1f
                }
            }

            val textColumn = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val titleView = TextView(this@MainActivity).apply {
                textSize = UiConstants.Text.SECTION
                text = title
                setTextColor(UiConstants.Colors.TEXT_EMPHASIS)
                setTypeface(typeface, Typeface.BOLD)
            }

            val subtitleView = TextView(this@MainActivity).apply {
                textSize = UiConstants.Text.CAPTION
                text = subtitle
                setTextColor(UiConstants.Colors.TEXT_SECONDARY)
            }

            textColumn.addView(titleView)
            textColumn.addView(subtitleView)

            addView(iconView)
            addView(textColumn)
        }
    }

    // buildSizeCopyButtons.
    private fun buildSizeCopyButtons(
        prefs: SharedPreferences
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)
        }

        // Convenience actions to mirror left/right trackpad sizes.
        val leftToRight = Button(this).apply {
            text = "Apply left trackpad size to right"
            setOnClickListener {
                copyTrackpadSize(
                    fromPrefix = "trackpad_left",
                    toPrefix = "trackpad_right",
                    prefs = prefs
                )
            }
        }

        val rightToLeft = Button(this).apply {
            text = "Apply right trackpad size to left"
            setOnClickListener {
                copyTrackpadSize(
                    fromPrefix = "trackpad_right",
                    toPrefix = "trackpad_left",
                    prefs = prefs
                )
            }
        }

        row.addView(leftToRight)
        row.addView(space(UiConstants.Spacing.TINY_GAP))
        row.addView(rightToLeft)
        return row
    }

    // copyTrackpadSize.
    private fun copyTrackpadSize(
        fromPrefix: String,
        toPrefix: String,
        prefs: SharedPreferences
    ) {
        val bounds = getTrackpadSizeBounds()
        val width = prefs.getInt("${fromPrefix}_width", dp(UiConstants.Sizes.DEFAULT_TRACKPAD_SIZE))
        val height = prefs.getInt("${fromPrefix}_height", dp(UiConstants.Sizes.DEFAULT_TRACKPAD_SIZE))
        val clampedWidth = width.coerceIn(bounds.minPx, bounds.maxWidthPx)
        val clampedHeight = height.coerceIn(bounds.minPx, bounds.maxHeightPx)
        prefs.edit()
            .putInt("${toPrefix}_width", clampedWidth)
            .putInt("${toPrefix}_height", clampedHeight)
            .apply()
        sizeInputs["${toPrefix}_width"]?.setText(clampedWidth.toString())
        sizeInputs["${toPrefix}_height"]?.setText(clampedHeight.toString())
    }

    // commitSizeInput.
    private fun commitSizeInput(
        editText: EditText,
        key: String,
        minValue: Int,
        maxValue: Int,
        prefs: SharedPreferences
    ) {
        val raw = editText.text.toString().toIntOrNull()
        val clamped = (raw ?: minValue).coerceIn(minValue, maxValue)
        if (editText.text.toString() != clamped.toString()) {
            editText.setText(clamped.toString())
        }
        prefs.edit().putInt(key, clamped).apply()
    }

    private data class ToggleSpec(
        val key: String,
        val label: String,
        val icon: Int,
        val flipIcon: Boolean? = null
    )

    private data class ToggleRow(
        val row: LinearLayout,
        val toggle: Switch
    )

    private data class TrackpadSizeBounds(
        val minPx: Int,
        val maxWidthPx: Int,
        val maxHeightPx: Int
    )

    private data class ButtonSizeBounds(
        val minPx: Int,
        val maxPx: Int
    )

    // getTrackpadSizeBounds.
    private fun getTrackpadSizeBounds(): TrackpadSizeBounds {
        val minPx = dp(UiConstants.Sizes.TRACKPAD_MIN)
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val secondary = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        val metrics = DisplayMetrics()
        if (secondary != null) {
            @Suppress("DEPRECATION")
            secondary.getRealMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        val maxWidth = metrics.widthPixels.coerceAtLeast(minPx)
        val maxHeight = metrics.heightPixels.coerceAtLeast(minPx)
        return TrackpadSizeBounds(minPx, maxWidth, maxHeight)
    }

    // getButtonSizeBounds.
    private fun getButtonSizeBounds(): ButtonSizeBounds {
        val minPx = dp(UiConstants.Sizes.BUTTON_MIN)
        val maxPx = dp(UiConstants.Sizes.BUTTON_MAX)
        return ButtonSizeBounds(minPx, maxPx)
    }

    // exportSettingsToUri.
    private fun exportSettingsToUri(uri: Uri) {
        val uiPrefs = getSharedPreferences("ui_config", MODE_PRIVATE)
        val posPrefs = getSharedPreferences("floating_positions", MODE_PRIVATE)
        val mirrorPrefs = getSharedPreferences("mirror_positions", MODE_PRIVATE)
        val root = JSONObject().apply {
            put("version", UiConstants.Backup.VERSION)
            put("ui_config", prefsToJson(uiPrefs))
            put("floating_positions", prefsToJson(posPrefs))
            put("mirror_positions", prefsToJson(mirrorPrefs))
        }
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Settings backed up", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Failed to backup settings", Toast.LENGTH_SHORT).show()
        }
    }

    // importSettingsFromUri.
    private fun importSettingsFromUri(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: return
            val root = JSONObject(content)
            val uiPrefs = getSharedPreferences("ui_config", MODE_PRIVATE)
            val posPrefs = getSharedPreferences("floating_positions", MODE_PRIVATE)
            val mirrorPrefs = getSharedPreferences("mirror_positions", MODE_PRIVATE)
            if (root.has("ui_config")) {
                applyJsonToPrefs(root.getJSONObject("ui_config"), uiPrefs)
            }
            if (root.has("floating_positions")) {
                applyJsonToPrefs(root.getJSONObject("floating_positions"), posPrefs)
            }
            if (root.has("mirror_positions")) {
                applyJsonToPrefs(root.getJSONObject("mirror_positions"), mirrorPrefs)
            }
            Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show()
            updateOverlayButtonState()
            recreate()
        } catch (_: Throwable) {
            Toast.makeText(this, "Failed to import settings", Toast.LENGTH_SHORT).show()
        }
    }

    // prefsToJson.
    private fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val obj = JSONObject()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> obj.put(key, value)
                is Int -> obj.put(key, value)
                is Long -> obj.put(key, value)
                is Float -> obj.put(key, value.toDouble())
                is String -> obj.put(key, value)
            }
        }
        return obj
    }

    // applyJsonToPrefs.
    private fun applyJsonToPrefs(
        obj: JSONObject,
        prefs: SharedPreferences
    ) {
        val editor = prefs.edit()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = obj.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> {
                    val asInt = value.toInt()
                    if (value == asInt.toDouble()) {
                        editor.putInt(key, asInt)
                    } else {
                        editor.putFloat(key, value.toFloat())
                    }
                }
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }

    // buildOptionsContainer.
    private fun buildOptionsContainer(paddingTopDp: Int = UiConstants.Spacing.OPTIONS_TOP): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, paddingTopDp, 0, 0)
        }
    }

    // toggleVisibilityOnClick.
    private fun toggleVisibilityOnClick(header: View, options: View) {
        header.setOnClickListener {
            options.visibility =
                if (options.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
    }

    // buildToggleSection.
    private fun buildToggleSection(
        header: LinearLayout,
        items: List<ToggleSpec>,
        prefs: SharedPreferences,
        paddingTopDp: Int = UiConstants.Spacing.SUBGROUP_TOP
    ): LinearLayout {
        // Collapsible list of toggle rows bound to preference keys.
        val options = buildOptionsContainer(paddingTopDp)
        for (item in items) {
            options.addView(buildToggleRow(item.label, item.key, item.icon, prefs, flipIcon = item.flipIcon))
        }
        toggleVisibilityOnClick(header, options)
        return options
    }

    // buildToggleRow.
    private fun buildToggleRow(
        label: String,
        key: String,
        icon: Int,
        prefs: SharedPreferences,
        onCheckedChange: ((Boolean) -> Unit)? = null,
        flipIcon: Boolean? = null
    ): LinearLayout {
        return buildToggleRowWithSwitch(
            label = label,
            key = key,
            icon = icon,
            prefs = prefs,
            onCheckedChange = onCheckedChange,
            flipIcon = flipIcon
        ).row
    }

    // buildToggleRowWithSwitch.
    private fun buildToggleRowWithSwitch(
        label: String,
        key: String,
        icon: Int,
        prefs: SharedPreferences,
        onCheckedChange: ((Boolean) -> Unit)? = null,
        flipIcon: Boolean? = null
    ): ToggleRow {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)
        }

        // Row with icon, label, and switch tied to a preference key.
        val iconView = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                dp(UiConstants.Sizes.ICON),
                dp(UiConstants.Sizes.ICON)
            ).apply {
                marginEnd = dp(UiConstants.Spacing.ICON_MARGIN)
            }
        }
        val shouldFlip = flipIcon ?: (key == "show_trackpad_left")
        if (shouldFlip) {
            iconView.scaleX = -1f
        }

        val text = TextView(this).apply {
            textSize = UiConstants.Text.BODY
            text = label
            setTextColor(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val toggle = Switch(this).apply {
            isChecked = prefs.getBoolean(key, true)
            val enabledColor = ColorStateList.valueOf(UiConstants.Colors.TEXT_SECONDARY)
            val disabledColor = ColorStateList.valueOf(UiConstants.Colors.BLACK)
            thumbTintList = if (isChecked) enabledColor else disabledColor
            trackTintList = if (isChecked) enabledColor else disabledColor
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(key, isChecked).apply()
                val color = if (isChecked) enabledColor else disabledColor
                thumbTintList = color
                trackTintList = color
                onCheckedChange?.invoke(isChecked)
            }
        }

        row.setOnClickListener {
            if (toggle.isEnabled) {
                toggle.isChecked = !toggle.isChecked
            }
        }

        row.addView(iconView)
        row.addView(text)
        row.addView(toggle)
        row.addView(space(UiConstants.Spacing.ROW_END_GAP))
        return ToggleRow(row, toggle)
    }

    // buildSliderRow.
    private fun buildSliderRow(
        label: String,
        key: String,
        prefs: SharedPreferences
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)
        }

        // Percentage slider row.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val text = TextView(this).apply {
            textSize = UiConstants.Text.BODY
            text = label
            setTextColor(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val value = TextView(this).apply {
            textSize = UiConstants.Text.SUBTITLE
            setTextColor(UiConstants.Colors.TEXT_SECONDARY)
        }

        val slider = SeekBar(this).apply {
            max = UiConstants.Sliders.OPACITY_MAX
            progress = prefs.getInt(key, UiConstants.Sliders.OPACITY_DEFAULT)
            value.text = "${progress}%"
            val gray = ColorStateList.valueOf(UiConstants.Colors.TEXT_SECONDARY)
            progressTintList = gray
            thumbTintList = gray
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                // onProgressChanged.
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    prefs.edit().putInt(key, progress).apply()
                    value.text = "$progress%"
                }
                // onStartTrackingTouch.
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                // onStopTrackingTouch.
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        top.addView(text)
        top.addView(value)
        row.addView(top)
        row.addView(slider)
        return row
    }

    // buildFloatSliderRow.
    private fun buildFloatSliderRow(
        label: String,
        key: String,
        prefs: SharedPreferences,
        icon: Int,
        minValue: Float,
        maxValue: Float,
        defaultValue: Float
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)
        }

        // Float slider with fixed step count.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val text = TextView(this).apply {
            textSize = UiConstants.Text.BODY
            text = label
            setTextColor(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val iconView = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                dp(UiConstants.Sizes.ICON_SMALL),
                dp(UiConstants.Sizes.ICON_SMALL)
            ).apply {
                marginEnd = dp(UiConstants.Spacing.SMALL_GAP)
                topMargin = dp(UiConstants.Spacing.ICON_TOP_MARGIN)
            }
        }

        val value = TextView(this).apply {
            textSize = UiConstants.Text.SUBTITLE
            setTextColor(UiConstants.Colors.TEXT_SECONDARY)
        }

        val steps = UiConstants.Sliders.STEPS
        val slider = SeekBar(this).apply {
            max = steps
            val stored = prefs.getFloat(key, defaultValue).coerceIn(minValue, maxValue)
            val progressValue = ((stored - minValue) / (maxValue - minValue) * steps).roundToInt()
            progress = progressValue
            value.text = String.format("%.1f", stored)
            val gray = ColorStateList.valueOf(UiConstants.Colors.TEXT_SECONDARY)
            progressTintList = gray
            thumbTintList = gray
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                // onProgressChanged.
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val fraction = progress / steps.toFloat()
                    val v = (minValue + (maxValue - minValue) * fraction).coerceIn(minValue, maxValue)
                    prefs.edit().putFloat(key, v).apply()
                    value.text = String.format("%.1f", v)
                }
                // onStartTrackingTouch.
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                // onStopTrackingTouch.
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        top.addView(iconView)
        top.addView(text)
        top.addView(value)
        row.addView(top)
        row.addView(slider)
        return row
    }

    // buildIntSliderRow.
    private fun buildIntSliderRow(
        label: String,
        key: String,
        prefs: SharedPreferences,
        icon: Int,
        minValue: Int,
        maxValue: Int,
        defaultValue: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING,
                UiConstants.Spacing.ROW_PADDING
            )
            setBackgroundColor(UiConstants.Colors.SURFACE)
        }

        // Int slider with min/max and display value.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val text = TextView(this).apply {
            textSize = UiConstants.Text.BODY
            text = label
            setTextColor(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val iconView = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(UiConstants.Colors.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                dp(UiConstants.Sizes.ICON_SMALL),
                dp(UiConstants.Sizes.ICON_SMALL)
            ).apply {
                marginEnd = dp(UiConstants.Spacing.SMALL_GAP)
                topMargin = dp(UiConstants.Spacing.ICON_TOP_MARGIN)
            }
        }

        val value = TextView(this).apply {
            textSize = UiConstants.Text.SUBTITLE
            setTextColor(UiConstants.Colors.TEXT_SECONDARY)
        }

        val slider = SeekBar(this).apply {
            max = maxValue - minValue
            val stored = prefs.getInt(key, defaultValue).coerceIn(minValue, maxValue)
            progress = stored - minValue
            value.text = stored.toString()
            val gray = ColorStateList.valueOf(UiConstants.Colors.TEXT_SECONDARY)
            progressTintList = gray
            thumbTintList = gray
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                // onProgressChanged.
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = (minValue + progress).coerceIn(minValue, maxValue)
                    prefs.edit().putInt(key, v).apply()
                    value.text = v.toString()
                }
                // onStartTrackingTouch.
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                // onStopTrackingTouch.
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        top.addView(iconView)
        top.addView(text)
        top.addView(value)
        row.addView(top)
        row.addView(slider)
        return row
    }

    // space.
    private fun space(dp: Int) = TextView(this).apply {
        height = dp
    }

    // dp.
    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
