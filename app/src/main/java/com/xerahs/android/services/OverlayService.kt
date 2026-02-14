package com.xerahs.android.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlay()
    }

    private fun createOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        val button = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xCC1565C0.toInt())
            alpha = 0.9f
        }

        // Handle drag and click
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                    }
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(button, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Trigger capture
                        val captureIntent = ScreenCaptureService.captureIntent(this@OverlayService)
                        startService(captureIntent)
                    }
                    true
                }
                else -> false
            }
        }

        overlayView = button
        windowManager?.addView(button, params)
    }

    override fun onDestroy() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        fun startIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java)
    }
}
