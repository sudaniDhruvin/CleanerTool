package com.example.cleanertool.ads

object AdConstants {
    // Production Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-7085320120847108/6667854194"
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-7085320120847108/3874363419"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-7085320120847108/3874363419"
    
    // Google Test Ad Unit IDs (use these for development/testing)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-7085320120847108/6667854194"
    const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-7085320120847108/3874363419"
    
    // Use test ads in debug mode
    fun getBannerAdUnitId(isDebug: Boolean = true): String {
        return if (isDebug) TEST_BANNER_AD_UNIT_ID else BANNER_AD_UNIT_ID
    }
    
    fun getNativeAdUnitId(isDebug: Boolean = true): String {
        return if (isDebug) TEST_NATIVE_AD_UNIT_ID else NATIVE_AD_UNIT_ID
    }
    
    fun getAppOpenAdUnitId(isDebug: Boolean = true): String {
        return if (isDebug) TEST_APP_OPEN_AD_UNIT_ID else APP_OPEN_AD_UNIT_ID
    }
}

