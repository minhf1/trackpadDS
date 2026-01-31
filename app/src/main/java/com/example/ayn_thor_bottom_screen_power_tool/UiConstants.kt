package com.example.ayn_thor_bottom_screen_power_tool

object UiConstants {
    object Spacing {
        const val SCREEN_PADDING = 32
        const val SECTION_GAP = 16
        const val CARD_PADDING = 16
        const val OPTIONS_TOP = 8
        const val SUBGROUP_TOP = 6
        const val ROW_PADDING = 12
        const val ROW_END_GAP = 4
        const val SMALL_GAP = 8
        const val TINY_GAP = 6
        const val ICON_MARGIN = 12
        const val BANNER_ICON_MARGIN = 12
        const val ICON_TOP_MARGIN = 2
    }

    object Text {
        const val TITLE = 18f
        const val SECTION = 16f
        const val BODY = 15f
        const val BANNER = 14f
        const val SUBTITLE = 13f
        const val CAPTION = 12f
    }

    object Colors {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SURFACE_DARK = 0xFF0A0A0A.toInt()
        const val SURFACE = 0xFF1A1A1A.toInt()
        const val SURFACE_ALT = 0xFF121212.toInt()
        const val INPUT_BG = 0xFF151515.toInt()
        const val TEXT_PRIMARY = 0xFFE0E0E0.toInt()
        const val TEXT_SECONDARY = 0xFF9A9A9A.toInt()
        const val TEXT_EMPHASIS = 0xFFE8E8E8.toInt()
        const val BANNER_BG = 0xFF141414.toInt()
    }

    object Sizes {
        const val ICON_SMALL = 20
        const val ICON = 24
        const val BANNER_WIDTH = 120
        const val BANNER_HEIGHT = 28
        const val INPUT_WIDTH = 120

        const val TRACKPAD_MIN = 120
        const val BUTTON_MIN = 24
        const val BUTTON_MAX = 120
        const val DEFAULT_TRACKPAD_SIZE = 200
    }

    object Sliders {
        const val OPACITY_MAX = 100
        const val OPACITY_DEFAULT = 100
        const val STEPS = 100

        const val CURSOR_SENSITIVITY_MIN = 0.5f
        const val CURSOR_SENSITIVITY_MAX = 6f
        const val CURSOR_SENSITIVITY_DEFAULT = 4.5f

        const val SCROLL_SENSITIVITY_MIN = 1
        const val SCROLL_SENSITIVITY_MAX = 100
        const val SCROLL_SENSITIVITY_DEFAULT = 40

        const val CURSOR_FADE_MIN_MS = 250
        const val CURSOR_FADE_MAX_MS = 5000
        const val CURSOR_FADE_DEFAULT_MS = 1000
    }

    object Backup {
        const val VERSION = 1
    }
}
