package com.example.cleanertool.ads

import android.app.Activity
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.example.cleanertool.navigation.Screen

class NavigationAdInterceptor(
    private val appOpenAdManager: AppOpenAdManager,
    private val activity: Activity
) {
    
    private var previousRoute: String? = null
    
    fun setupNavigationListener(navController: NavController) {
        navController.addOnDestinationChangedListener { _, destination: NavDestination?, _ ->
            destination?.route?.let { route ->
                Log.d("NavigationAd", "Navigation to: $route, previous: $previousRoute")
                
                // Show app open ad when navigating to Home screen from any other screen
                if (route == Screen.Home.route) {
                    // Don't show if already on Home (avoid duplicate)
                    if (previousRoute != null && previousRoute != Screen.Home.route) {
                        Log.d("NavigationAd", "Navigating to Home from $previousRoute, showing app open ad")
                        // Small delay to ensure screen transition is smooth
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            appOpenAdManager.showAdIfAvailable(activity)
                        }, 500)
                    } else if (previousRoute == Screen.Landing.route) {
                        Log.d("NavigationAd", "Navigating to Home from Landing, showing app open ad")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            appOpenAdManager.showAdIfAvailable(activity)
                        }, 500)
                    }
                }
                previousRoute = route
            }
        }
    }
}

