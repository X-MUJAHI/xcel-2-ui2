package com.example.utils

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.example.R
import java.util.Locale

/**
 * Manages a small draggable floating HUD timer showing session active time / game elapsed time.
 * If SYSTEM_ALERT_WINDOW permission is granted, attaches as a system overlay.
 * Also provides an in-app floating mode inside MainActivity if system overlay permission is not granted.
 */
object FloatingTimerManager {

    private const val TAG = "FloatingTimerManager"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var inAppContainer: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var startTimeMillis = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val elapsed = System.currentTimeMillis() - startTimeMillis
            val seconds = (elapsed / 1000) % 60
            val minutes = (elapsed / (1000 * 60)) % 60
            val hours = elapsed / (1000 * 60 * 60)

            val timeText = if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }

            overlayView?.findViewById<TextView>(R.id.tv_floating_timer_text)?.text = timeText
            inAppContainer?.findViewById<TextView>(R.id.tv_floating_timer_text)?.text = timeText

            handler.postDelayed(this, 1000L)
        }
    }

    fun start(context: Context, inAppRootView: View? = null) {
        val prefs = context.getSharedPreferences(com.example.MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        startTimeMillis = prefs.getLong("session_start_time", System.currentTimeMillis())
        isRunning = true
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)

        // Try System Overlay if permission granted
        if (Settings.canDrawOverlays(context)) {
            showSystemOverlay(context)
        } else if (inAppRootView != null) {
            showInAppFloatingTimer(inAppRootView)
        }
    }

    fun stop(context: Context? = null) {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        hideSystemOverlay()
        hideInAppFloatingTimer()
    }

    private fun showSystemOverlay(context: Context) {
        if (overlayView != null) return
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.layout_floating_timer, null)
            overlayView = view

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 60
                y = 180
            }

            // Draggable touch listener
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, params)
                        true
                    }
                    else -> false
                }
            }

            view.findViewById<ImageView>(R.id.iv_floating_timer_close)?.setOnClickListener {
                stop()
            }

            windowManager?.addView(view, params)
        } catch (e: Exception) {
            overlayView = null
        }
    }

    private fun hideSystemOverlay() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (ignored: Exception) {}
        overlayView = null
    }

    private fun showInAppFloatingTimer(rootView: View) {
        val timerView = rootView.findViewById<View>(R.id.floating_inapp_timer) ?: return
        inAppContainer = timerView
        timerView.visibility = View.VISIBLE

        var dX = 0f
        var dY = 0f

        timerView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                    true
                }
                else -> false
            }
        }

        timerView.findViewById<ImageView>(R.id.iv_floating_timer_close)?.setOnClickListener {
            stop()
        }
    }

    private fun hideInAppFloatingTimer() {
        inAppContainer?.visibility = View.GONE
        inAppContainer = null
    }
}
