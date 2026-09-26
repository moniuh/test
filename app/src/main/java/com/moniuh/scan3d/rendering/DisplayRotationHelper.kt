package com.moniuh.scan3d.rendering

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import com.google.ar.core.Session

/** Keeps the ARCore session's display geometry in sync with the surface size and rotation. */
class DisplayRotationHelper(private val activity: Activity) : DisplayManager.DisplayListener {

    private val displayManager = activity.getSystemService(DisplayManager::class.java)

    @Volatile
    private var viewportChanged = false
    private var width = 0
    private var height = 0

    private val display: Display
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display!!
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

    fun onResume() = displayManager.registerDisplayListener(this, null)

    fun onPause() = displayManager.unregisterDisplayListener(this)

    fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        viewportChanged = true
    }

    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged) {
            session.setDisplayGeometry(display.rotation, width, height)
            viewportChanged = false
        }
    }

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayRemoved(displayId: Int) = Unit

    override fun onDisplayChanged(displayId: Int) {
        viewportChanged = true
    }
}
