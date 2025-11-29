package com.example.cleanertool.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.cleanertool.MainActivity
import com.example.cleanertool.R
import com.example.cleanertool.utils.OverlayPermission

/**
 * Lightweight overlay that shows a small widget on top of other apps
 * after a call has ended (similar to Truecaller).
 */
class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val primaryText = intent?.getStringExtra(EXTRA_PRIMARY_TEXT)
        val secondaryText = intent?.getStringExtra(EXTRA_SECONDARY_TEXT)
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_CALL

        // Check overlay permission before showing
        if (!OverlayPermission.hasOverlayPermission(this)) {
            Log.w(TAG, "Overlay permission not granted. Cannot show popup.")
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay(primaryText, secondaryText, mode)
        return START_NOT_STICKY
    }

    private fun showOverlay(primaryText: String?, secondaryText: String?, mode: String) {
        try {
            // Remove existing overlay if any
            if (overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing existing overlay", e)
                }
                overlayView = null
            }

            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.view_call_overlay, null)

            updateText(primaryText, secondaryText, mode)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Center the overlay on screen for better visibility
                gravity = Gravity.CENTER
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                // Make it more prominent
                alpha = 0.95f
            }

            val closeButton = overlayView?.findViewById<Button>(R.id.btn_close)
            val primaryButton = overlayView?.findViewById<Button>(R.id.btn_scan)

            closeButton?.setOnClickListener {
                Log.d(TAG, "Close button clicked")
                stopSelf()
            }

            if (mode == MODE_UNINSTALL) {
                primaryButton?.text = "Clean"
                primaryButton?.setOnClickListener {
                    Log.d(TAG, "Clean button clicked for uninstalled app")
                    val launchIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(launchIntent)
                    stopSelf()
                }
            } else {
                primaryButton?.text = "Scan"
                primaryButton?.setOnClickListener {
                    Log.d(TAG, "Scan button clicked for call")
                    // TODO: Deep-link to number scan screen when implemented
                    stopSelf()
                }
            }

            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay shown successfully. Mode: $mode, Primary: $primaryText, Secondary: $secondaryText")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
            stopSelf()
        }
    }

    private fun updateText(primaryText: String?, secondaryText: String?, mode: String) {
        val titleView = overlayView?.findViewById<TextView>(R.id.txt_title)
        val numberView = overlayView?.findViewById<TextView>(R.id.txt_number)

        if (mode == MODE_UNINSTALL) {
            titleView?.text = "App uninstalled"
            numberView?.text = secondaryText ?: primaryText ?: "Unknown app"
        } else {
            titleView?.text = secondaryText ?: "Recent call"
            numberView?.text = primaryText ?: "Unknown number"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                Log.d(TAG, "Overlay removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay in onDestroy", e)
        }
    }

    companion object {
        private const val TAG = "CallOverlayService"
        
        const val EXTRA_PRIMARY_TEXT = "extra_primary_text"
        const val EXTRA_SECONDARY_TEXT = "extra_secondary_text"
        const val EXTRA_MODE = "extra_mode"

        const val MODE_CALL = "mode_call"
        const val MODE_UNINSTALL = "mode_uninstall"
    }
}


