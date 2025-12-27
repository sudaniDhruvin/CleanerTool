package com.example.cleanertool.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cleanertool.ads.AppOpenAdManager
import com.example.cleanertool.ads.NavigationAdInterceptor
import com.example.cleanertool.ui.screens.*

@Composable
fun NavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Setup navigation ad interceptor for app open ads
    LaunchedEffect(Unit) {
        activity?.let { act ->
            val appOpenAdManager = AppOpenAdManager.getInstance(context.applicationContext as android.app.Application)
            val interceptor = NavigationAdInterceptor(appOpenAdManager, act)
            interceptor.setupNavigationListener(navController)
            android.util.Log.d("NavGraph", "Navigation ad interceptor set up for activity: ${act.javaClass.simpleName}")
        } ?: run {
            android.util.Log.w("NavGraph", "Activity is null, cannot set up navigation ad interceptor")
        }
    }
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
        composable(Screen.AppProcessScanning.route) {
            AppProcessScanningScreen(navController = navController)
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
        composable("photo_permission") {
            PhotoPermissionScreen(navController = navController)
        }
        composable("photo_scanning") {
            PhotoScanningScreen(navController = navController)
        }
        composable(Screen.StorageGallery.route) {
            StorageGalleryScreen(navController = navController)
        }
        composable(Screen.BatteryScanning.route) {
            BatteryScanningScreen(navController = navController)
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
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("permission_management") {
            PermissionManagementScreen(navController = navController)
        }
        composable("settings_menu") {
            SettingsMenuScreen(navController = navController)
        }
        composable("feedback") {
            FeedbackScreen(navController = navController)
        }
        composable("about") {
            AboutScreen(navController = navController)
        }
        composable("rate_us") {
            RateUsScreen(navController = navController)
        }
        composable("notification_settings") {
            NotificationSettingsScreen(navController = navController)
        }
        composable("notification_cleaner") {
            NotificationCleanerScreen(navController = navController)
        }
        composable("image_detail") {
            ImageDetailScreen(navController = navController)
        }
        composable("compressing") {
            CompressingScreen(
                navController = navController,
                onComplete = { success ->
                    if (success) {
                        navController.navigate("compression_success") {
                            popUpTo("compressing") { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
        composable("compression_success") {
            CompressionSuccessScreen(
                navController = navController,
                onNavigateBack = {
                    // Navigate back to storage gallery
                    navController.navigate("storage_gallery") {
                        popUpTo("compression_success") { inclusive = true }
                        popUpTo("image_detail") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "compression_error?error={error}",
            arguments = listOf(
                navArgument("error") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val errorMessage = backStackEntry.arguments?.getString("error")
            CompressionErrorScreen(
                navController = navController,
                errorMessage = errorMessage,
                onRetry = {
                    // Navigate back to image detail screen to retry
                    navController.navigate("image_detail") {
                        popUpTo("compression_error") { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.DuplicateCleaner.route) {
            DuplicateCleanerScreen(navController = navController)
        }
        composable(Screen.LargeFiles.route) {
            LargeFilesScreen(navController = navController)
        }
        composable(Screen.ApkCleaner.route) {
            ApkCleanerScreen(navController = navController)
        }
        composable(Screen.EmptyFolders.route) {
            EmptyFoldersScreen(navController = navController)
        }
        composable(Screen.ClipboardCleaner.route) {
            ClipboardCleanerScreen(navController = navController)
        }
        composable(Screen.ContactsCleaner.route) {
            ContactsCleanerScreen(navController = navController)
        }
        composable(Screen.LinkCleaner.route) {
            LinkCleanerScreen(navController = navController)
        }
        composable("call_assistant_setup") {
            CallAssistantSetupScreen(navController = navController)
        }
    }
}

sealed class Screen(val route: String) {
    object Landing : Screen("landing")
    object Scan : Screen("scan")
    object Clean : Screen("clean")
    object AppProcessScanning : Screen("app_process_scanning")
    object RamProcess : Screen("ram_process")
    object Permissions : Screen("permissions")
    object Home : Screen("home")
    object StorageGallery : Screen("storage_gallery")
    object BatteryScanning : Screen("battery_scanning")
    object BatteryCharging : Screen("battery_charging")
    object AppManagement : Screen("app_management")
    object SpeakerMaintenance : Screen("speaker_maintenance")
    object UninstallReminder : Screen("uninstall_reminder")
    object DuplicateCleaner : Screen("duplicate_cleaner")
    object LargeFiles : Screen("large_files")
    object ApkCleaner : Screen("apk_cleaner")
    object EmptyFolders : Screen("empty_folders")
    object ClipboardCleaner : Screen("clipboard_cleaner")
    object ContactsCleaner : Screen("contacts_cleaner")
    object LinkCleaner : Screen("link_cleaner")
}

