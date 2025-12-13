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
            Log.d("AppOpenAd", "Skipping load: isLoadingAd=$isLoadingAd, isAdAvailable=${isAdAvailable()}")
            return
        }
        
        Log.d("AppOpenAd", "Loading app open ad with unit ID: $AD_UNIT_ID")
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        
        AppOpenAd.load(
            application,
            AD_UNIT_ID,
            request,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d("AppOpenAd", "App open ad loaded successfully")
                    appOpenAd = ad
                    isLoadingAd = false
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("AppOpenAd", "App open ad failed to load: ${loadAdError.message}, code: ${loadAdError.code}")
                    isLoadingAd = false
                    // Retry loading after a delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!isAdAvailable() && !isLoadingAd) {
                            loadAd()
                        }
                    }, 30000) // Retry after 30 seconds
                }
            }
        )
    }
    
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null
    }
    
    fun showAdIfAvailable(activity: Activity) {
        Log.d("AppOpenAd", "showAdIfAvailable called, isShowingAd: $isShowingAd, isAdAvailable: ${isAdAvailable()}")
        
        if (isShowingAd) {
            Log.d("AppOpenAd", "The app open ad is already showing.")
            return
        }
        
        if (!isAdAvailable()) {
            Log.d("AppOpenAd", "The app open ad is not ready yet. Loading new ad...")
            loadAd()
            return
        }
        
        Log.d("AppOpenAd", "Showing app open ad on activity: ${activity.javaClass.simpleName}")
        
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AppOpenAd", "App open ad dismissed")
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("AppOpenAd", "App open ad failed to show: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d("AppOpenAd", "App open ad showed successfully")
                isShowingAd = true
            }
        }
        
        try {
            appOpenAd?.show(activity)
            lastAdShowTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("AppOpenAd", "Exception showing ad: ${e.message}", e)
            appOpenAd = null
            isShowingAd = false
            loadAd()
        }
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
    private var lastAdShowTime = 0L
    private val MIN_TIME_BETWEEN_ADS = 60000L // 1 minute minimum between ads
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Show app open ad when app comes to foreground (but not on first start - let navigation handle that)
        currentActivity?.let { 
            Log.d("AppOpenAd", "App started, currentActivity: ${it.javaClass.simpleName}, isFirstStart: $isFirstStart, wasInBackground: $wasInBackground")
            // Only show on foreground if app was in background (not first start)
            if (wasInBackground && !isFirstStart) {
                val timeSinceLastAd = System.currentTimeMillis() - lastAdShowTime
                if (timeSinceLastAd >= MIN_TIME_BETWEEN_ADS) {
                    wasInBackground = false
                    // Small delay to ensure activity is fully ready
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        showAdIfAvailable(it)
                    }, 500)
                }
            }
            isFirstStart = false
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        wasInBackground = true
        Log.d("AppOpenAd", "App stopped, wasInBackground = true")
    }
}

