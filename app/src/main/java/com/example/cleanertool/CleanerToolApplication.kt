package com.example.cleanertool

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener

class CleanerToolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Request test devices (set before initialization so it applies to init)
        MobileAds.setRequestConfiguration(
            com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR))
                .build()
        )

        // Initialize AdMob
        MobileAds.initialize(this, object : OnInitializationCompleteListener {
            override fun onInitializationComplete(initializationStatus: InitializationStatus) {
                val statusMap = initializationStatus.adapterStatusMap
                Log.d("AdMob", "AdMob initialization complete")
                for (adapterClass in statusMap.keys) {
                    val status = statusMap[adapterClass]
                    Log.d(
                        "AdMob",
                        "Adapter: $adapterClass, Status: ${status?.initializationState}, " +
                                "Description: ${status?.description}"
                    )
                }
                
                // Load app open ad after MobileAds is initialized
                try {
                    val appOpenAdManager = com.example.cleanertool.ads.AppOpenAdManager.getInstance(this@CleanerToolApplication)
                    Log.d("AdMob", "MobileAds initialized, loading app open ad")
                    appOpenAdManager.loadAd()
                } catch (e: Exception) {
                    Log.e("AdMob", "Error loading app open ad: ${e.message}", e)
                }
            }
        })
        
        // Note: request configuration is set above before initialization
    }
}

