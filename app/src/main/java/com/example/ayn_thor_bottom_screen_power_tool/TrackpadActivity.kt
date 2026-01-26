package com.example.ayn_thor_bottom_screen_power_tool

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import kotlin.math.abs

class TrackpadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set a fixed initial size (in dp)
        val dm = resources.displayMetrics
        val w = (320 * dm.density).toInt()
        val h = (220 * dm.density).toInt()

        window.setLayout(w, h)
        setContentView(FloatingTrackpadLayout(this))
    }

}
