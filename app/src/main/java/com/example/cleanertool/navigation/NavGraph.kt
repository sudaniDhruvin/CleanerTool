package com.example.cleanertool.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cleanertool.ui.screens.*

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Landing.route
    ) {
        composable(Screen.Landing.route) {
            LandingScreen(navController = navController)
        }
        composable(Screen.Scan.route) {
            ScanScreen(navController = navController)
        }
        composable(Screen.Clean.route) {
            CleanScreen(navController = navController)
        }
        composable(Screen.RamProcess.route) {
            RamProcessScreen(navController = navController)
        }
        composable(Screen.Permissions.route) {
            PermissionRequestScreen(
                onAllPermissionsGranted = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.StorageGallery.route) {
            StorageGalleryScreen(navController = navController)
        }
        composable(Screen.BatteryCharging.route) {
            BatteryChargingScreen(navController = navController)
        }
        composable(Screen.AppManagement.route) {
            AppManagementScreen(navController = navController)
        }
        composable(Screen.SpeakerMaintenance.route) {
            SpeakerMaintenanceScreen(navController = navController)
        }
        composable(Screen.UninstallReminder.route) {
            UninstallReminderScreen(navController = navController)
        }
    }
}

sealed class Screen(val route: String) {
    object Landing : Screen("landing")
    object Scan : Screen("scan")
    object Clean : Screen("clean")
    object RamProcess : Screen("ram_process")
    object Permissions : Screen("permissions")
    object Home : Screen("home")
    object StorageGallery : Screen("storage_gallery")
    object BatteryCharging : Screen("battery_charging")
    object AppManagement : Screen("app_management")
    object SpeakerMaintenance : Screen("speaker_maintenance")
    object UninstallReminder : Screen("uninstall_reminder")
}

