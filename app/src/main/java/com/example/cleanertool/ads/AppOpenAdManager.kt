package com.example.cleanertool.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

class AppOpenAdManager(private val application: Application) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {
    
    companion object {
        @Volatile
        private var INSTANCE: AppOpenAdManager? = null
        
        fun getInstance(application: Application): AppOpenAdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppOpenAdManager(application).also { INSTANCE = it }
            }
        }
    }
    
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var currentActivity: Activity? = null
    
    private val AD_UNIT_ID = AdConstants.getAppOpenAdUnitId() // Use test ad in debug mode
    
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        application.registerActivityLifecycleCallbacks(this)
        loadAd()
    }
    
    fun loadAd() {
        if (isLoadingAd || isAdAvailable()) {
            return
        }
        
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        
        AppOpenAd.load(
            application,
            AD_UNIT_ID,
            request,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d("AppOpenAd", "Ad was loaded.")
                    appOpenAd = ad
                    isLoadingAd = false
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d("AppOpenAd", "Ad failed to load: ${loadAdError.message}")
                    isLoadingAd = false
                }
            }
        )
    }
    
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null
    }
    
    fun showAdIfAvailable(activity: Activity) {
        if (isShowingAd) {
            Log.d("AppOpenAd", "The app open ad is already showing.")
            return
        }
        
        if (!isAdAvailable()) {
            Log.d("AppOpenAd", "The app open ad is not ready yet.")
            loadAd()
            return
        }
        
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }
        }
        
        appOpenAd?.show(activity)
    }
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    
    override fun onActivityPaused(activity: Activity) {}
    
    override fun onActivityStopped(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
    
    // Track if app was in background
    private var wasInBackground = false
    private var isFirstStart = true
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Show app open ad when app comes to foreground or on first start
        currentActivity?.let { 
            Log.d("AppOpenAd", "App started, currentActivity: ${it.javaClass.simpleName}, isFirstStart: $isFirstStart, wasInBackground: $wasInBackground")
            if (isFirstStart || wasInBackground) {
                isFirstStart = false
                wasInBackground = false
                // Small delay to ensure activity is fully ready
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showAdIfAvailable(it)
                }, 500)
            }
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        wasInBackground = true
        Log.d("AppOpenAd", "App stopped, wasInBackground = true")
    }
}

