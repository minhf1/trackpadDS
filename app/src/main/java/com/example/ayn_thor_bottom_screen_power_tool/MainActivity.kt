package com.example.ayn_thor_bottom_screen_power_tool

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
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
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var pendingStart = false
    private val sizeInputs = mutableMapOf<String, EditText>()
    private var sizePrefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDefaultsFromAsset()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF000000.toInt())
        }

        root.addView(buildKofiBanner())
        root.addView(space(12))

        buildPermissionNotice()?.let { notice ->
            root.addView(notice)
            root.addView(space(12))
        }

        startBtn = Button(this)
        updateOverlayButtonState()

        root.addView(startBtn)
        root.addView(space(12))
        root.addView(buildButtonsMenu())

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(root)
        }

        setContentView(scroll)

        val sizePrefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        sizePrefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val edit = sizeInputs[key] ?: return@OnSharedPreferenceChangeListener
            if (edit.hasFocus()) return@OnSharedPreferenceChangeListener
            val value = sizePrefs.getInt(key, edit.text.toString().toIntOrNull() ?: 0)
            if (edit.text.toString() != value.toString()) {
                edit.setText(value.toString())
            }
        }
        sizePrefs.registerOnSharedPreferenceChangeListener(sizePrefListener)
    }

    override fun onResume() {
        super.onResume()
        updateOverlayButtonState()
        if (pendingStart) {
            proceedStartFlow()
        }
    }

    override fun onDestroy() {
        sizePrefListener?.let {
            getSharedPreferences("ui_config", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        sizePrefListener = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && pendingStart) {
            proceedStartFlow()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isPointerA11yEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name.contains("PointerAccessibilityService") }
    }

    private fun buildPermissionNotice(): LinearLayout? {
        val overlayMissing = !Settings.canDrawOverlays(this)
        val a11yMissing = !isPointerA11yEnabled()
        val notifMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (!overlayMissing && !a11yMissing && !notifMissing) return null

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val title = TextView(this).apply {
            text = "Read before using the app, there are a few permissions needed and here is how the app use them"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
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
        container.addView(space(6))
        for (item in bullets) {
            val line = TextView(this).apply {
                text = "- $item"
                textSize = 13f
                setTextColor(0xFF9A9A9A.toInt())
            }
            container.addView(line)
        }
        return container
    }

    private fun proceedStartFlow() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        if (!isPointerA11yEnabled()) {
            openAccessibilitySettings()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                return
            }
        }

        // Start cursor overlay service
        startForegroundService(Intent(this, PointerService::class.java))
        getSharedPreferences("ui_config", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("overlay_running", true)
            .apply()
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

    private fun ensureDefaultsFromAsset() {
        val uiPrefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        if (uiPrefs.getBoolean("defaults_loaded", false)) return
        if (uiPrefs.all.isNotEmpty()) {
            uiPrefs.edit().putBoolean("defaults_loaded", true).apply()
            return
        }
        try {
            val json = assets.open("default_config.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            root.optJSONObject("ui_config")?.let { applyJsonToPrefs(it, uiPrefs) }
            root.optJSONObject("floating_positions")?.let {
                applyJsonToPrefs(it, getSharedPreferences("floating_positions", Context.MODE_PRIVATE))
            }
            root.optJSONObject("mirror_positions")?.let {
                applyJsonToPrefs(it, getSharedPreferences("mirror_positions", Context.MODE_PRIVATE))
            }
            uiPrefs.edit().putBoolean("defaults_loaded", true).apply()
        } catch (_: Throwable) {
        }
    }

    private fun updateOverlayButtonState() {
        val running = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
            .getBoolean("overlay_running", false)
        if (running) {
            startBtn.text = "Stop Overlay"
            startBtn.setOnClickListener {
                pendingStart = false
                getSharedPreferences("ui_config", Context.MODE_PRIVATE)
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

    private fun buildButtonsMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sectionHeader = buildSectionHeader(
            title = "Buttons",
            subtitle = "Floating controls visibility and actions"
        )

        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 8, 0, 0)
        }

        val trackpadHeader = buildSubgroupHeaderRow(
            title = "Trackpad mode",
            subtitle = "Overlays for the trackpad controls",
            icon = R.drawable.trackpad_right,
            flipIcon = true
        )
        val trackpadOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 6, 0, 0)
        }
        trackpadHeader.setOnClickListener {
            trackpadOptions.visibility =
                if (trackpadOptions.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
        options.addView(trackpadHeader)
        val trackpadItems = listOf(
            Triple("show_nav_buttons", "Floating navigation toggle button", android.R.drawable.ic_menu_manage),
            Triple("show_back_btn", "Back button", R.drawable.ic_back),
            Triple("show_home_btn", "Home button", R.drawable.ic_home),
            Triple("show_recents_btn", "Recents button", R.drawable.ic_menu),
            Triple("show_drag_btn", "Drag toggle button", R.drawable.ic_drag),
            Triple("show_stop_btn", "Stop overlay button", android.R.drawable.ic_menu_close_clear_cancel),
            Triple("show_hide_btn", "Show/Hide toggle button", R.drawable.ic_eye_open),
            Triple("show_swap_btn", "Screen swap button - Experimental use at your own risk to be fixed in future", R.drawable.ic_swap),
            Triple("show_light_btn", "Light overlay toggle button", R.drawable.ic_light_bulb),
            Triple("show_mirror_btn", "Mirror mode toggle button", R.drawable.ic_mirror),
            Triple("show_click_btn", "Click button", R.drawable.ic_trackpad_click),
            Triple("show_right_click_btn", "Right-click button", R.drawable.ic_trackpad_right_click),
            Triple("show_trackpad_left", "Left trackpad", R.drawable.trackpad_right),
            Triple("show_trackpad_right", "Right trackpad", R.drawable.trackpad_right)
        )
        for ((key, label, icon) in trackpadItems) {
            trackpadOptions.addView(buildToggleRow(label, key, icon, prefs))
        }
        options.addView(trackpadOptions)

        options.addView(space(8))
        val mirrorHeader = buildSubgroupHeaderRow(
            title = "Screen mirror mode",
            subtitle = "Buttons visible in mirror mode",
            icon = R.drawable.ic_mirror,
            flipIcon = false
        )
        val mirrorOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 6, 0, 0)
        }
        mirrorHeader.setOnClickListener {
            mirrorOptions.visibility =
                if (mirrorOptions.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
        options.addView(mirrorHeader)
        val mirrorItems = listOf(
            Triple("mirror_show_toggle", "Toggle mirror mode", R.drawable.ic_mirror),
            Triple("mirror_show_recents", "Recents button", R.drawable.ic_menu),
            Triple("mirror_show_back", "Back button", R.drawable.ic_back),
            Triple("mirror_show_home", "Home button", R.drawable.ic_home),
            Triple("mirror_show_drag", "Drag toggle button", R.drawable.ic_drag)
        )
        for ((key, label, icon) in mirrorItems) {
            mirrorOptions.addView(buildToggleRow(label, key, icon, prefs))
        }
        options.addView(mirrorOptions)

        sectionHeader.setOnClickListener {
            options.visibility =
                if (options.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        container.addView(sectionHeader)
        container.addView(options)
        container.addView(space(16))
        container.addView(buildOpacityMenu())
        container.addView(space(16))
        container.addView(buildBehaviorMenu())
        container.addView(space(16))
        container.addView(buildTrackpadSizeMenu())
        container.addView(space(16))
        container.addView(buildBackupImportMenu())
        return container
    }

    private fun buildKofiBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF141414.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
            setOnClickListener {
                val uri = Uri.parse("https://ko-fi.com/minxf1")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

            val textView = TextView(context).apply {
                text = "Buy me coffee if you love the app"
                textSize = 14f
                setTextColor(0xFFE0E0E0.toInt())
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTypeface(typeface, android.graphics.Typeface.BOLD)
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
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(28)).apply {
                    marginStart = dp(12)
                }
            }
            addView(iconView)
        }
    }

    private fun buildSectionHeader(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF0A0A0A.toInt())
            addView(TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(0xFF9A9A9A.toInt())
            })
        }
    }

    private fun buildOpacityMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sectionHeader = buildSectionHeader(
            title = "Opacity",
            subtitle = "Default transparency for overlays"
        )

        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 8, 0, 0)
        }

        options.addView(buildSliderRow("Button opacity", "button_opacity", prefs))
        options.addView(buildSliderRow("Trackpad opacity", "trackpad_opacity", prefs))

        sectionHeader.setOnClickListener {
            options.visibility =
                if (options.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    private fun buildTrackpadSizeMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        val bounds = getTrackpadSizeBounds()
        val buttonBounds = getButtonSizeBounds()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sectionHeader = buildSectionHeader(
            title = "Trackpad/button size",
            subtitle = "Set width and height for each trackpad"
        )

        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 8, 0, 0)
        }

        options.addView(buildGroupHeaderRow("Left trackpad", R.drawable.trackpad_right, flipIcon = true))
        options.addView(buildNumberInputRow("Width", "trackpad_left_width", prefs, bounds.minPx, bounds.maxWidthPx))
        options.addView(buildNumberInputRow("Height", "trackpad_left_height", prefs, bounds.minPx, bounds.maxHeightPx))

        options.addView(space(8))
        options.addView(buildGroupHeaderRow("Right trackpad", R.drawable.trackpad_right, flipIcon = false))
        options.addView(buildNumberInputRow("Width", "trackpad_right_width", prefs, bounds.minPx, bounds.maxWidthPx))
        options.addView(buildNumberInputRow("Height", "trackpad_right_height", prefs, bounds.minPx, bounds.maxHeightPx))

        options.addView(space(8))
        options.addView(buildSizeCopyButtons(prefs))

        options.addView(space(8))
        options.addView(buildGroupHeaderRow("Buttons", R.drawable.ic_menu, flipIcon = false))
        options.addView(buildNumberInputRow("Button size", "button_size", prefs, buttonBounds.minPx, buttonBounds.maxPx))

        sectionHeader.setOnClickListener {
            options.visibility =
                if (options.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    private fun buildBehaviorMenu(): LinearLayout {
        val prefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sectionHeader = buildSectionHeader(
            title = "Behavior",
            subtitle = "Focus and cursor tuning"
        )

        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 8, 0, 0)
        }

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
        val trackpadOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 6, 0, 0)
        }
        trackpadHeader.setOnClickListener {
            trackpadOptions.visibility =
                if (trackpadOptions.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
        trackpadOptions.addView(buildFloatSliderRow(
            label = "Cursor sensitivity",
            key = "cursor_sensitivity",
            prefs = prefs,
            icon = android.R.drawable.ic_menu_mylocation,
            minValue = 0.5f,
            maxValue = 6f,
            defaultValue = 4.5f
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Scroll sensitivity",
            key = "scroll_sensitivity",
            prefs = prefs,
            icon = R.drawable.ic_scroll_sensitivity,
            minValue = 1,
            maxValue = 100,
            defaultValue = 40
        ))
        trackpadOptions.addView(buildIntSliderRow(
            label = "Cursor fade delay (ms)",
            key = "cursor_fade_timeout_ms",
            prefs = prefs,
            icon = R.drawable.ic_cursor_fade_timeout,
            minValue = 250,
            maxValue = 5000,
            defaultValue = 1000
        ))

        val trackpadModeHeader = buildSubgroupHeaderRow(
            title = "Trackpad mode",
            subtitle = "Behavior while using the trackpad",
            icon = R.drawable.ic_trackpad_click,
            flipIcon = false
        )
        val trackpadModeOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 6, 0, 0)
        }
        trackpadModeHeader.setOnClickListener {
            trackpadModeOptions.visibility =
                if (trackpadModeOptions.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
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

        val lightOffHeader = buildSubgroupHeaderRow(
            title = "Light Off Mode",
            subtitle = "Behavior when the light overlay is enabled",
            icon = R.drawable.ic_light_bulb,
            flipIcon = false
        )
        val lightOffOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 6, 0, 0)
        }
        lightOffHeader.setOnClickListener {
            lightOffOptions.visibility =
                if (lightOffOptions.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
        lightOffOptions.addView(buildToggleRow(
            label = "Keep controller element ON when Light Off",
            key = "light_off_keep_controls",
            icon = R.drawable.ic_light_bulb,
            prefs = prefs
        ))
        var syncPrimaryEnabled: (() -> Unit)? = null
        val hideBottomRow = buildToggleRow(
            label = "Hide bottom screen light bulb button when light off",
            key = "light_off_hide_bottom_button",
            icon = R.drawable.ic_light_bulb,
            prefs = prefs,
            onCheckedChange = { syncPrimaryEnabled?.invoke() }
        )
        val showPrimaryRow = buildToggleRow(
            label = "Show light bulb button on primary screen when light off",
            key = "light_off_primary_button",
            icon = R.drawable.ic_light_bulb,
            prefs = prefs
        )
        lightOffOptions.addView(hideBottomRow)
        lightOffOptions.addView(showPrimaryRow)
        val hideSwitch = hideBottomRow.getChildAt(2) as? Switch
        val primarySwitch = showPrimaryRow.getChildAt(2) as? Switch
        syncPrimaryEnabled = {
            val hideBottom = hideSwitch?.isChecked == true
            primarySwitch?.isEnabled = hideBottom
            if (!hideBottom && primarySwitch?.isChecked == true) {
                primarySwitch.isChecked = false
                prefs.edit().putBoolean("light_off_primary_button", false).apply()
            }
        }
        syncPrimaryEnabled?.invoke()

        val hapticHeader = buildSubgroupHeaderRow(
            title = "Trackpad haptic feedback",
            subtitle = "Trackpad and overlay vibration controls",
            icon = R.drawable.ic_haptic_feedback,
            flipIcon = false
        )
        val hapticOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 6, 0, 0)
        }
        hapticHeader.setOnClickListener {
            hapticOptions.visibility =
                if (hapticOptions.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
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
        val mirrorOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 6, 0, 0)
        }
        mirrorHeader.setOnClickListener {
            mirrorOptions.visibility =
                if (mirrorOptions.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }
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
        options.addView(space(8))
        options.addView(trackpadModeHeader)
        options.addView(trackpadModeOptions)
        options.addView(space(8))
        options.addView(lightOffHeader)
        options.addView(lightOffOptions)
        options.addView(space(8))
        options.addView(hapticHeader)
        options.addView(hapticOptions)
        options.addView(space(8))
        options.addView(mirrorHeader)
        options.addView(mirrorOptions)

        sectionHeader.setOnClickListener {
            options.visibility =
                if (options.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    private fun buildBackupImportMenu(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sectionHeader = buildSectionHeader(
            title = "Backup and import settings",
            subtitle = "Save or restore overlay configuration"
        )

        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
            setPadding(0, 8, 0, 0)
        }

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
        options.addView(space(6))
        options.addView(importBtn)

        sectionHeader.setOnClickListener {
            options.visibility =
                if (options.visibility == LinearLayout.VISIBLE) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        container.addView(sectionHeader)
        container.addView(options)
        return container
    }

    private fun buildGroupHeaderRow(
        label: String,
        icon: Int,
        flipIcon: Boolean
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1A1A1A.toInt())

            val iconView = ImageView(this@MainActivity).apply {
                setImageResource(icon)
                setColorFilter(0xFFE0E0E0.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(12)
                }
                if (flipIcon) {
                    scaleX = -1f
                }
            }

            val text = TextView(this@MainActivity).apply {
                textSize = 15f
                text = label
                setTextColor(0xFFE0E0E0.toInt())
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

    private fun buildNumberInputRow(
        label: String,
        key: String,
        prefs: android.content.SharedPreferences,
        minValue: Int,
        maxValue: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val text = TextView(this).apply {
            textSize = 15f
            text = label
            setTextColor(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(0xFFE0E0E0.toInt())
            setBackgroundColor(0xFF151515.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
            val defaultValue = prefs.getInt(key, dp(200))
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
            layoutParams = LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        sizeInputs[key] = input

        row.addView(text)
        row.addView(input)
        return row
    }

    private fun buildSubgroupHeaderRow(
        title: String,
        subtitle: String,
        icon: Int,
        flipIcon: Boolean
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF121212.toInt())

            val iconView = ImageView(this@MainActivity).apply {
                setImageResource(icon)
                setColorFilter(0xFFE0E0E0.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(12)
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
                textSize = 16f
                text = title
                setTextColor(0xFFE8E8E8.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val subtitleView = TextView(this@MainActivity).apply {
                textSize = 12f
                text = subtitle
                setTextColor(0xFF9A9A9A.toInt())
            }

            textColumn.addView(titleView)
            textColumn.addView(subtitleView)

            addView(iconView)
            addView(textColumn)
        }
    }

    private fun buildSizeCopyButtons(
        prefs: android.content.SharedPreferences
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

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
        row.addView(space(6))
        row.addView(rightToLeft)
        return row
    }

    private fun copyTrackpadSize(
        fromPrefix: String,
        toPrefix: String,
        prefs: android.content.SharedPreferences
    ) {
        val bounds = getTrackpadSizeBounds()
        val width = prefs.getInt("${fromPrefix}_width", dp(200))
        val height = prefs.getInt("${fromPrefix}_height", dp(200))
        val clampedWidth = width.coerceIn(bounds.minPx, bounds.maxWidthPx)
        val clampedHeight = height.coerceIn(bounds.minPx, bounds.maxHeightPx)
        prefs.edit()
            .putInt("${toPrefix}_width", clampedWidth)
            .putInt("${toPrefix}_height", clampedHeight)
            .apply()
        sizeInputs["${toPrefix}_width"]?.setText(clampedWidth.toString())
        sizeInputs["${toPrefix}_height"]?.setText(clampedHeight.toString())
    }

    private fun commitSizeInput(
        editText: EditText,
        key: String,
        minValue: Int,
        maxValue: Int,
        prefs: android.content.SharedPreferences
    ) {
        val raw = editText.text.toString().toIntOrNull()
        val clamped = (raw ?: minValue).coerceIn(minValue, maxValue)
        if (editText.text.toString() != clamped.toString()) {
            editText.setText(clamped.toString())
        }
        prefs.edit().putInt(key, clamped).apply()
    }

    private data class TrackpadSizeBounds(
        val minPx: Int,
        val maxWidthPx: Int,
        val maxHeightPx: Int
    )

    private data class ButtonSizeBounds(
        val minPx: Int,
        val maxPx: Int
    )

    private fun getTrackpadSizeBounds(): TrackpadSizeBounds {
        val minPx = dp(120)
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondary = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        val metrics = android.util.DisplayMetrics()
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

    private fun getButtonSizeBounds(): ButtonSizeBounds {
        val minPx = dp(24)
        val maxPx = dp(120)
        return ButtonSizeBounds(minPx, maxPx)
    }

    private fun exportSettingsToUri(uri: Uri) {
        val uiPrefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        val posPrefs = getSharedPreferences("floating_positions", Context.MODE_PRIVATE)
        val mirrorPrefs = getSharedPreferences("mirror_positions", Context.MODE_PRIVATE)
        val root = JSONObject().apply {
            put("version", 1)
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

    private fun importSettingsFromUri(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: return
            val root = JSONObject(content)
            val uiPrefs = getSharedPreferences("ui_config", Context.MODE_PRIVATE)
            val posPrefs = getSharedPreferences("floating_positions", Context.MODE_PRIVATE)
            val mirrorPrefs = getSharedPreferences("mirror_positions", Context.MODE_PRIVATE)
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

    private fun prefsToJson(prefs: android.content.SharedPreferences): JSONObject {
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

    private fun applyJsonToPrefs(
        obj: JSONObject,
        prefs: android.content.SharedPreferences
    ) {
        val editor = prefs.edit()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            when (value) {
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

    private fun buildToggleRow(
        label: String,
        key: String,
        icon: Int,
        prefs: android.content.SharedPreferences,
        onCheckedChange: ((Boolean) -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val iconView = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginEnd = dp(12)
            }
        }
        if (key == "show_trackpad_left") {
            iconView.scaleX = -1f
        }

        val text = TextView(this).apply {
            textSize = 15f
            text = label
            setTextColor(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val toggle = Switch(this).apply {
            isChecked = prefs.getBoolean(key, true)
            val enabledColor = android.content.res.ColorStateList.valueOf(0xFF9A9A9A.toInt())
            val disabledColor = android.content.res.ColorStateList.valueOf(0xFF000000.toInt())
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
        row.addView(space(4))
        return row
    }

    private fun buildSliderRow(
        label: String,
        key: String,
        prefs: android.content.SharedPreferences
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val text = TextView(this).apply {
            textSize = 15f
            text = label
            setTextColor(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val value = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF9A9A9A.toInt())
        }

        val slider = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt(key, 100)
            value.text = "${progress}%"
            val gray = android.content.res.ColorStateList.valueOf(0xFF9A9A9A.toInt())
            progressTintList = gray
            thumbTintList = gray
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    prefs.edit().putInt(key, progress).apply()
                    value.text = "$progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        top.addView(text)
        top.addView(value)
        row.addView(top)
        row.addView(slider)
        return row
    }

    private fun buildFloatSliderRow(
        label: String,
        key: String,
        prefs: android.content.SharedPreferences,
        icon: Int,
        minValue: Float,
        maxValue: Float,
        defaultValue: Float
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val text = TextView(this).apply {
            textSize = 15f
            text = label
            setTextColor(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val iconView = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(8)
                topMargin = dp(2)
            }
        }

        val value = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF9A9A9A.toInt())
        }

        val steps = 100
        val slider = SeekBar(this).apply {
            max = steps
            val stored = prefs.getFloat(key, defaultValue).coerceIn(minValue, maxValue)
            val progressValue = ((stored - minValue) / (maxValue - minValue) * steps).roundToInt()
            progress = progressValue
            value.text = String.format("%.1f", stored)
            val gray = android.content.res.ColorStateList.valueOf(0xFF9A9A9A.toInt())
            progressTintList = gray
            thumbTintList = gray
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val fraction = progress / steps.toFloat()
                    val v = (minValue + (maxValue - minValue) * fraction).coerceIn(minValue, maxValue)
                    prefs.edit().putFloat(key, v).apply()
                    value.text = String.format("%.1f", v)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
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

    private fun buildIntSliderRow(
        label: String,
        key: String,
        prefs: android.content.SharedPreferences,
        icon: Int,
        minValue: Int,
        maxValue: Int,
        defaultValue: Int
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val text = TextView(this).apply {
            textSize = 15f
            text = label
            setTextColor(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val iconView = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(8)
                topMargin = dp(2)
            }
        }

        val value = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF9A9A9A.toInt())
        }

        val slider = SeekBar(this).apply {
            max = maxValue - minValue
            val stored = prefs.getInt(key, defaultValue).coerceIn(minValue, maxValue)
            progress = stored - minValue
            value.text = stored.toString()
            val gray = android.content.res.ColorStateList.valueOf(0xFF9A9A9A.toInt())
            progressTintList = gray
            thumbTintList = gray
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = (minValue + progress).coerceIn(minValue, maxValue)
                    prefs.edit().putInt(key, v).apply()
                    value.text = v.toString()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
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

    private fun space(dp: Int) = TextView(this).apply {
        height = dp
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}
