package com.example.ayn_thor_bottom_screen_power_tool

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class MirrorPermissionActivity : Activity() {
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(projectionManager.createScreenCaptureIntent(), 1002)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002 && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, PointerService::class.java).apply {
                action = "START_MIRROR"
                putExtra("projection_result_code", resultCode)
                putExtra("projection_data", data)
            }
            startForegroundService(serviceIntent)

            val mirrorIntent = Intent(this, MirrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val dm = getSystemService(DisplayManager::class.java)
            val secondary = dm.displays.firstOrNull {
                it.displayId != android.view.Display.DEFAULT_DISPLAY
            }
            if (secondary != null) {
                val options = ActivityOptions.makeBasic().apply {
                    launchDisplayId = secondary.displayId
                }
                startActivity(mirrorIntent, options.toBundle())
            } else {
                startActivity(mirrorIntent)
            }
        }
        finish()
    }
}
