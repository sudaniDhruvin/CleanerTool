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
        Log.d("NavigationAd", "Setting up navigation listener")
        navController.addOnDestinationChangedListener { _, destination: NavDestination?, _ ->
            destination?.route?.let { route ->
                Log.d("NavigationAd", "Navigation to: $route, previous: $previousRoute")
                
                // Show app open ad when navigating to Home screen from any other screen
                if (route == Screen.Home.route) {
                    // Don't show if already on Home (avoid duplicate)
                    if (previousRoute != null && previousRoute != Screen.Home.route) {
                        Log.d("NavigationAd", "Navigating to Home from $previousRoute, will show app open ad")
                        // Small delay to ensure screen transition is smooth
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Log.d("NavigationAd", "Calling showAdIfAvailable after delay")
                            appOpenAdManager.showAdIfAvailable(activity)
                        }, 800) // Increased delay to 800ms
                    } else if (previousRoute == Screen.Landing.route) {
                        Log.d("NavigationAd", "Navigating to Home from Landing, will show app open ad")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Log.d("NavigationAd", "Calling showAdIfAvailable after delay (from Landing)")
                            appOpenAdManager.showAdIfAvailable(activity)
                        }, 800) // Increased delay to 800ms
                    }
                }
                previousRoute = route
            }
        }
    }
}

