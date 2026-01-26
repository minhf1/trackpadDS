package com.example.ayn_thor_bottom_screen_power_tool

import android.view.Surface

object RotationUtil {
    /**
     * Maps a movement delta (dx, dy) from the rotated screen's coordinate space
     * into the "natural" orientation coordinate space.
     *
     * If your desired behavior is "finger right moves cursor right on the top screen"
     * regardless of how the bottom screen is rotated, apply this mapping.
     */
    fun mapDeltaForRotation(rotation: Int, dx: Float, dy: Float): Pair<Float, Float> {
        return when (rotation) {
            Surface.ROTATION_0 -> dx to dy
            Surface.ROTATION_90 -> dy to -dx
            Surface.ROTATION_180 -> -dx to -dy
            Surface.ROTATION_270 -> -dy to dx
            else -> dx to dy
        }
    }
}
