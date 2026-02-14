package com.minxf1.ayn_thor_bottom_screen_power_tool

import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MirrorPermissionActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Only proceed when MediaProjection permission is granted and data is present.
        if (result.resultCode == RESULT_OK && result.data != null) {
            startMirrorFlow(result.resultCode, result.data!!)
        }
        finish()
    }

    private companion object {
        const val ACTION_START_MIRROR = "START_MIRROR"
        const val EXTRA_PROJECTION_RESULT_CODE = "projection_result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
    }

    // Requests MediaProjection permission on launch.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    // Starts the mirror service and launches the mirror activity on a secondary display if available.
    private fun startMirrorFlow(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, PointerService::class.java).apply {
            action = ACTION_START_MIRROR
            putExtra(EXTRA_PROJECTION_RESULT_CODE, resultCode)
            putExtra(EXTRA_PROJECTION_DATA, data)
        }
        startForegroundService(serviceIntent)

        val mirrorIntent = Intent(this, MirrorActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val dm = getSystemService(DisplayManager::class.java)
        val secondary = dm.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY
        }
        // Prefer secondary display when present; otherwise fall back to the primary display.
        if (secondary != null) {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = secondary.displayId
            }
            startActivity(mirrorIntent, options.toBundle())
        } else {
            startActivity(mirrorIntent)
        }
    }
}
