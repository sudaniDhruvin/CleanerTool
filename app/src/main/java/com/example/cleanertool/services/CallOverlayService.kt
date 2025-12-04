package com.example.cleanertool.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.cleanertool.MainActivity
import com.example.cleanertool.R
import com.example.cleanertool.utils.OverlayPermission
import android.provider.ContactsContract
import android.Manifest
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.*

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
        Log.d(TAG, "CallOverlayService onCreate")
        
        // Start as foreground service to prevent system from killing it
        createNotificationChannel()
        startForeground(1, createNotification())
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_service_channel",
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for displaying call overlay"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "overlay_service_channel")
            .setContentTitle("Call Overlay Active")
            .setContentText("Overlay is displayed")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_CALL
        
        // For call mode, get contact name and phone number separately
        val contactName = if (mode == MODE_CALL) {
            intent?.getStringExtra(EXTRA_CONTACT_NAME)
        } else {
            null
        }
        
        val phoneNumber = if (mode == MODE_CALL) {
            intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        } else {
            intent?.getStringExtra(EXTRA_PRIMARY_TEXT) // For uninstall, use primary text
        }
        
        val callDirection = if (mode == MODE_CALL) {
            intent?.getStringExtra(EXTRA_CALL_DIRECTION)
        } else {
            null
        }
        
        val secondaryText = intent?.getStringExtra(EXTRA_SECONDARY_TEXT)

        // Check overlay permission before showing
        if (!OverlayPermission.hasOverlayPermission(this)) {
            Log.w(TAG, "Overlay permission not granted. Cannot show popup.")
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay(contactName, phoneNumber, secondaryText, callDirection, mode)
        // Return START_STICKY to keep service running until user closes overlay
        return START_STICKY
    }

    private fun showOverlay(contactName: String?, phoneNumber: String?, secondaryText: String?, callDirection: String?, mode: String) {
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
            
            // Don't set touch listener on root - let child views handle their own touches
            // This ensures buttons work properly

            updateText(contactName, phoneNumber, secondaryText, callDirection, mode)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                // FLAG_NOT_FOCUSABLE: Don't steal keyboard focus, but still receive touch events
                // FLAG_NOT_TOUCH_MODAL: Touches outside pass through to underlying apps
                // FLAG_LAYOUT_IN_SCREEN: Position overlay properly on screen
                // FLAG_HARDWARE_ACCELERATED: Better rendering performance
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                // Center the overlay on screen
                gravity = Gravity.CENTER
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                x = 0
                y = 0
                // Make it more prominent
                alpha = 1.0f
            }

            setupClickListeners(phoneNumber, mode)
            
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay shown successfully. Mode: $mode, Name: $contactName, Number: $phoneNumber")
            Log.d(TAG, "Overlay will stay visible until user clicks close button (X icon)")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
            stopSelf()
        }
    }

    private fun updateText(contactName: String?, phoneNumber: String?, secondaryText: String?, callDirection: String?, mode: String) {
        // Banner title
        val bannerTitle = overlayView?.findViewById<TextView>(R.id.txt_banner_title)
        if (mode == MODE_UNINSTALL) {
            bannerTitle?.text = "App uninstalled less than 1 minute ago"
        } else {
            val directionText = when (callDirection) {
                "INCOMING" -> "Incoming call"
                "OUTGOING" -> "Outgoing call"
                else -> "Call"
            }
            bannerTitle?.text = "$directionText ended less than 1 minute ago"
        }

        // Avatar initial - use contact name if available, otherwise phone number
        val avatarText = overlayView?.findViewById<TextView>(R.id.txt_avatar)
        val initial = when {
            mode == MODE_UNINSTALL -> {
                phoneNumber?.takeIf { it.isNotBlank() }?.firstOrNull()?.uppercaseChar() ?: 'A'
            }
            else -> {
                // Use first letter of contact name if available, otherwise first digit of number
                contactName?.takeIf { it.isNotBlank() }?.firstOrNull()?.uppercaseChar()
                    ?: phoneNumber?.takeIf { it.isNotBlank() }?.firstOrNull()?.uppercaseChar()
                    ?: 'U'
            }
        }
        avatarText?.text = initial.toString()

        // Primary info - Show contact name for calls, app name for uninstall
        val primaryInfo = overlayView?.findViewById<TextView>(R.id.txt_primary_info)
        if (mode == MODE_UNINSTALL) {
            primaryInfo?.text = phoneNumber ?: "Unknown app"
        } else {
            // For calls: show contact name if available, otherwise show phone number
            primaryInfo?.text = contactName ?: phoneNumber ?: "Unknown number"
        }

        // Secondary info - Show phone number for calls, description for uninstall
        val secondaryInfo = overlayView?.findViewById<TextView>(R.id.txt_secondary_info)
        if (mode == MODE_UNINSTALL) {
            secondaryInfo?.text = secondaryText ?: "Tap to clean leftovers"
        } else {
            // For calls: show phone number if we have a contact name, otherwise show carrier/direction
            if (!contactName.isNullOrBlank() && !phoneNumber.isNullOrBlank()) {
                secondaryInfo?.text = phoneNumber
            } else {
                secondaryInfo?.text = callDirection?.let { 
                    when (it) {
                        "INCOMING" -> "Incoming call"
                        "OUTGOING" -> "Outgoing call"
                        else -> phoneNumber ?: "Unknown"
                    }
                } ?: phoneNumber ?: "Unknown"
            }
        }

        // Location (hide for uninstall, show for calls if available)
        val locationText = overlayView?.findViewById<TextView>(R.id.txt_location)
        if (mode == MODE_UNINSTALL) {
            locationText?.visibility = View.GONE
        } else {
            // For calls, you could add location detection here
            locationText?.visibility = View.GONE
        }

        // Primary action button
        val primaryActionBtn = overlayView?.findViewById<Button>(R.id.btn_primary_action)
        if (mode == MODE_UNINSTALL) {
            primaryActionBtn?.text = "Clean"
        } else {
            primaryActionBtn?.text = "View"
        }
    }

    private fun setupClickListeners(primaryText: String?, mode: String) {
        // Close button in banner - ONLY this button closes the overlay
        val closeBanner = overlayView?.findViewById<ImageView>(R.id.btn_close_banner)
        
        // Ensure it's clickable and can receive touch events
        closeBanner?.isClickable = true
        closeBanner?.isFocusable = true
        closeBanner?.isEnabled = true
        
        // Primary click listener - this is the main way to close
        closeBanner?.setOnClickListener { view ->
            Log.d(TAG, "Close button onClick - user explicitly closing overlay")
            removeOverlay()
        }
        
        // Touch listener for visual feedback only - don't consume events
        closeBanner?.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.7f
                    false // Don't consume - let click listener handle it
                }
                android.view.MotionEvent.ACTION_UP -> {
                    view.alpha = 1.0f
                    false // Don't consume - let click listener handle it
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1.0f
                    false
                }
                else -> false
            }
        }

        // Primary action button (View/Clean) - Opens action and closes overlay
        val primaryActionBtn = overlayView?.findViewById<Button>(R.id.btn_primary_action)
        primaryActionBtn?.setOnClickListener {
            if (mode == MODE_UNINSTALL) {
                Log.d(TAG, "Clean button clicked for uninstalled app")
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(launchIntent)
                removeOverlay()
            } else {
                Log.d(TAG, "View button clicked for call")
                // TODO: Open call details or scan screen
                removeOverlay()
            }
        }

        // Call action button - Initiates phone call
        val callAction = overlayView?.findViewById<LinearLayout>(R.id.btn_action_call)
        callAction?.setOnClickListener {
            if (mode == MODE_CALL && !primaryText.isNullOrBlank()) {
                Log.d(TAG, "Call action clicked: $primaryText")
                
                // Check if CALL_PHONE permission is granted
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) 
                    == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted - directly initiate the call
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        data = android.net.Uri.parse("tel:$primaryText")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(callIntent)
                        removeOverlay()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error making call", e)
                        // Fallback to dialer on any error
                        openDialer(primaryText)
                    }
                } else {
                    // Permission not granted - open dialer instead
                    Log.w(TAG, "CALL_PHONE permission not granted, opening dialer")
                    openDialer(primaryText)
                }
            }
        }

        // Message action button - Opens contact message thread and closes overlay
        val messageAction = overlayView?.findViewById<LinearLayout>(R.id.btn_action_message)
        messageAction?.setOnClickListener {
            if (mode == MODE_CALL && !primaryText.isNullOrBlank()) {
                Log.d(TAG, "Message action clicked: $primaryText")
                openContactMessageScreen(primaryText)
                removeOverlay()
            }
        }

        // Edit action button - Opens contact edit screen and closes overlay
        val editAction = overlayView?.findViewById<LinearLayout>(R.id.btn_action_edit)
        editAction?.setOnClickListener {
            if (mode == MODE_CALL && !primaryText.isNullOrBlank()) {
                Log.d(TAG, "Edit action clicked: $primaryText")
                openContactEditScreen(primaryText)
                removeOverlay()
            }
        }
    }

    /**
     * Open the contact edit screen for the given phone number
     */
    private fun openContactEditScreen(phoneNumber: String) {
        try {
            // First, try to find the contact by phone number
            val contactId = getContactIdFromNumber(phoneNumber)
            
            if (contactId != null) {
                // Contact exists, open edit screen
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    data = ContactsContract.Contacts.getLookupUri(contactId, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(editIntent)
                    Log.d(TAG, "Opened contact edit screen for contact ID: $contactId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening contact edit screen", e)
                    // Fallback: try with raw contact URI
                    try {
                        val rawContactUri = android.net.Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_URI,
                            contactId.toString()
                        )
                        val fallbackIntent = Intent(Intent.ACTION_EDIT).apply {
                            data = rawContactUri
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error with fallback contact edit", e2)
                    }
                }
            } else {
                // Contact doesn't exist, create a new contact with this number
                val insertIntent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(insertIntent)
                    Log.d(TAG, "Opening new contact screen for number: $phoneNumber")
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening new contact screen", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in openContactEditScreen", e)
        }
    }
    
    /**
     * Open the dialer with the given phone number
     */
    private fun openDialer(phoneNumber: String) {
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(dialIntent)
            removeOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting dialer", e)
        }
    }
    
    /**
     * Open the message thread for the given phone number
     * If contact exists, opens the conversation thread; otherwise opens new SMS
     */
    private fun openContactMessageScreen(phoneNumber: String) {
        try {
            // Check if contact exists
            val contactId = getContactIdFromNumber(phoneNumber)
            
            // Use standard SMS intent - it will open existing conversation if available
            // or create a new one if not
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("smsto:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // If contact exists, try to add contact info to the intent
            if (contactId != null) {
                try {
                    // Try to get contact lookup key for better integration
                    val lookupUri = ContactsContract.Contacts.getLookupUri(contactId, null)
                    if (lookupUri != null) {
                        // Some messaging apps support contact lookup
                        smsIntent.putExtra("contact_id", contactId)
                        Log.d(TAG, "Opening message thread for contact ID: $contactId")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Could not add contact info to SMS intent", e)
                }
            }
            
            startActivity(smsIntent)
            Log.d(TAG, "Opened message screen for number: $phoneNumber")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in openContactMessageScreen", e)
            // Fallback: try basic SMS
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("smsto:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(smsIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error in SMS fallback", e2)
            }
        }
    }
    
    /**
     * Get contact ID from phone number
     */
    private fun getContactIdFromNumber(phoneNumber: String): Long? {
        try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            val lookupUri = android.net.Uri.withAppendedPath(uri, android.net.Uri.encode(phoneNumber))
            
            val projection = arrayOf(ContactsContract.PhoneLookup._ID)
            
            contentResolver.query(
                lookupUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    if (idIndex >= 0) {
                        val contactId = cursor.getLong(idIndex)
                        Log.d(TAG, "Found contact ID: $contactId for number: $phoneNumber")
                        return contactId
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact ID from number: $phoneNumber", e)
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallOverlayService onDestroy called - service is being destroyed")
        try {
            if (overlayView != null) {
                Log.d(TAG, "Removing overlay view in onDestroy")
                try {
                    windowManager?.removeView(overlayView)
                    Log.d(TAG, "Overlay removed successfully in onDestroy")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay view in onDestroy", e)
                }
                overlayView = null
            } else {
                Log.d(TAG, "Overlay view already null in onDestroy")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
    
    private fun removeOverlay() {
        Log.d(TAG, "removeOverlay called - user explicitly closing overlay")
        try {
            if (overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                    Log.d(TAG, "Overlay view removed from window manager")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay view from window manager", e)
                }
                overlayView = null
                Log.d(TAG, "Overlay removed successfully - stopping service")
            } else {
                Log.w(TAG, "removeOverlay called but overlayView is null")
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error in removeOverlay", e)
            try {
                stopForeground(true)
                stopSelf()
            } catch (e2: Exception) {
                Log.e(TAG, "Error stopping service", e2)
            }
        }
    }

    companion object {
        private const val TAG = "CallOverlayService"
        
        const val EXTRA_PRIMARY_TEXT = "extra_primary_text"
        const val EXTRA_SECONDARY_TEXT = "extra_secondary_text"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_DIRECTION = "extra_call_direction"

        const val MODE_CALL = "mode_call"
        const val MODE_UNINSTALL = "mode_uninstall"
    }
}


