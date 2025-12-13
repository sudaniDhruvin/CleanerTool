package com.example.cleanertool.ads

import android.app.Activity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.example.cleanertool.navigation.Screen

class NavigationAdInterceptor(
    private val appOpenAdManager: AppOpenAdManager,
    private val activity: Activity
) {
    
    private val routesToShowAppOpenAd = setOf(
        Screen.BatteryScanning.route,
        Screen.AppProcessScanning.route,
        "photo_permission",
        Screen.Home.route
    )
    
    fun setupNavigationListener(navController: NavController) {
        navController.addOnDestinationChangedListener { _, destination: NavDestination?, _ ->
            destination?.route?.let { route ->
                if (routesToShowAppOpenAd.contains(route)) {
                    // Show app open ad when navigating to these screens
                    appOpenAdManager.showAdIfAvailable(activity)
                }
            }
        }
    }
}

