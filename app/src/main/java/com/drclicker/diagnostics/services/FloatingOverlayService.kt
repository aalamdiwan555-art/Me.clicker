package com.drclicker.diagnostics.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class FloatingOverlayService : Service() {
    private val tag = "FloatingOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Overlay service started")
        createOverlay()
        return START_STICKY
    }

    private fun createOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // Create overlay view
            overlayView = LinearLayout(this).apply {
                setBackgroundColor(0x00000000)  // Transparent
                layoutParams = LinearLayout.LayoutParams(
                    150,
                    80
                ).apply {
                    gravity = Gravity.CENTER
                }
            }

            // Add status text
            val statusText = TextView(this).apply {
                text = "Dr. Clicker"
                setTextColor(0xFFFFD700.toInt())
                textSize = 10f
            }
            overlayView!!.addView(statusText)

            // Window parameters: touch pass-through
            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = 150
                height = 80
                x = 0
                y = 100
                gravity = Gravity.TOP or Gravity.START
            }

            windowManager?.addView(overlayView, params)
            Log.d(tag, "Overlay created successfully")

        } catch (e: Exception) {
            Log.e(tag, "Failed to create overlay: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error removing overlay: ${e.message}")
        }
        Log.d(tag, "Overlay service destroyed")
    }
}
