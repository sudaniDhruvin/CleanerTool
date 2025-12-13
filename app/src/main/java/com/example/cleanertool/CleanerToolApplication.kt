package com.example.cleanertool

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener

class CleanerToolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
            }
        })
        
        // Request test ads on emulator
        MobileAds.setRequestConfiguration(
            com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR))
                .build()
        )
    }
}

