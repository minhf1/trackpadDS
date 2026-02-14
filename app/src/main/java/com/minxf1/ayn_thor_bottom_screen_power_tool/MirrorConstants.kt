package com.minxf1.ayn_thor_bottom_screen_power_tool

object MirrorConstants {
    // Intent actions used by mirror-related services.
    object Actions {
        const val STOP_MIRROR = "STOP_MIRROR"
    }

    // SharedPreferences names and keys used by mirror UI.
    object Prefs {
        const val UI = "ui_config"
        const val MIRROR_POSITIONS = "mirror_positions"
        const val DRAG_ENABLED = "drag_enabled"
        const val BUTTON_SIZE = "button_size"
        const val RENDER_CLICK = "mirror_render_click"
        const val HAPTIC_BUTTONS = "haptic_mirror_buttons"
    }

    // View tags/keys for mirror control buttons.
    object Keys {
        const val MIRROR_TOGGLE = "mirror_toggle"
        const val MIRROR_BACK = "mirror_back"
        const val MIRROR_HOME = "mirror_home"
        const val MIRROR_RECENTS = "mirror_recents"
        const val MIRROR_DRAG = "mirror_drag"
    }

    // Layout spacing for mirror-specific UI elements (dp).
    object Layout {
        const val CAST_BORDER_DP = 2
        const val GESTURE_EXCLUSION_WIDTH_DP = 32
        const val BUTTON_GAP_DP = 8
        const val BUTTON_MARGIN_DP = 16
    }

    // Default ordering for mirror control buttons.
    object ButtonIndex {
        const val TOGGLE = 0
        const val BACK = 1
        const val HOME = 2
        const val RECENTS = 3
        const val DRAG = 4
    }
}
