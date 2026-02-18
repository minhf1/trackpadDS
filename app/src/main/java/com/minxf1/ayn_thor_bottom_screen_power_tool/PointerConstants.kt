package com.minxf1.ayn_thor_bottom_screen_power_tool

object PointerConstants {
    object Actions {
        const val STOP_OVERLAY = "STOP_OVERLAY"
        const val START_MIRROR = "START_MIRROR"
        const val STOP_MIRROR = "STOP_MIRROR"
    }

    object Extras {
        const val PROJECTION_RESULT_CODE = "projection_result_code"
        const val PROJECTION_DATA = "projection_data"
    }

    object Timing {
        const val CURSOR_TIMER_PERIOD_MS = 16L
        const val SCROLL_INDICATOR_WINDOW_MS = 300L
        const val CURSOR_FADE_IN_MS = 120L
        const val CURSOR_FADE_OUT_MS = 200L
        const val MIRROR_TOUCH_HIDE_ACTIVE_MS = 150L
        const val MIRROR_TOUCH_HIDE_INACTIVE_MS = 60L
    }

    object Sizes {
        const val CURSOR_DP = 36f
        const val MIRROR_TOUCH_DP = 20f
    }

    object Alpha {
        const val CURSOR = 1f
        const val MIRROR_TOUCH = 0.8f
    }

    object Notification {
        const val CHANNEL_ID = "pointer_fgs"
        const val CHANNEL_NAME = "Pointer Service"
        const val TITLE = "Trackpad running"
        const val ID = 1
    }
}
