package com.xerahs.android.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xerahs.android.feature.annotation.AnnotationScreen
import com.xerahs.android.feature.capture.CaptureScreen
import com.xerahs.android.feature.history.HistoryScreen
import com.xerahs.android.feature.settings.AppearanceSettingsScreen
import com.xerahs.android.feature.settings.BackupSettingsScreen
import com.xerahs.android.feature.settings.SettingsHubScreen
import com.xerahs.android.feature.settings.UploadSettingsScreen
import com.xerahs.android.feature.settings.destinations.FtpConfigScreen
import com.xerahs.android.feature.settings.destinations.ImgurConfigScreen
import com.xerahs.android.feature.settings.destinations.S3ConfigScreen
import com.xerahs.android.feature.upload.UploadScreen

sealed class Screen(val route: String) {
    data object Capture : Screen("capture")
    data object Annotation : Screen("annotation/{imagePath}") {
        fun createRoute(imagePath: String) = "annotation/${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
    }
    data object Upload : Screen("upload/{imagePath}") {
        fun createRoute(imagePath: String) = "upload/${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
    }
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object AppearanceSettings : Screen("settings/appearance")
    data object UploadSettings : Screen("settings/uploads")
    data object BackupSettings : Screen("settings/backup")
    data object ImgurConfig : Screen("settings/imgur")
    data object S3Config : Screen("settings/s3")
    data object FtpConfig : Screen("settings/ftp")
    data object UploadBatch : Screen("upload-batch/{imagePaths}") {
        fun createRoute(imagePaths: List<String>) =
            "upload-batch/${java.net.URLEncoder.encode(imagePaths.joinToString("|"), "UTF-8")}"
    }
}

@Composable
fun XerahSNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Capture.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
                slideInHorizontally(initialOffsetX = { 100 }, animationSpec = tween(300))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
                slideOutHorizontally(targetOffsetX = { -100 }, animationSpec = tween(300))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) +
                slideInHorizontally(initialOffsetX = { -100 }, animationSpec = tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                slideOutHorizontally(targetOffsetX = { 100 }, animationSpec = tween(300))
        }
    ) {
        composable(Screen.Capture.route) {
            CaptureScreen(
                onImageCaptured = { imagePath ->
                    navController.navigate(Screen.Annotation.createRoute(imagePath))
                },
                onMultiImageCaptured = { imagePaths ->
                    navController.navigate(Screen.UploadBatch.createRoute(imagePaths))
                }
            )
        }

        composable(
            route = Screen.Annotation.route,
            arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val imagePath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("imagePath") ?: "",
                "UTF-8"
            )
            AnnotationScreen(
                imagePath = imagePath,
                onExportComplete = { exportedPath ->
                    navController.navigate(Screen.Upload.createRoute(exportedPath)) {
                        popUpTo(Screen.Capture.route) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Upload.route,
            arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val imagePath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("imagePath") ?: "",
                "UTF-8"
            )
            UploadScreen(
                imagePath = imagePath,
                onUploadComplete = {
                    // Clear the upload flow from the back stack
                    navController.popBackStack(Screen.Capture.route, inclusive = false)
                    // Navigate to History the same way bottom nav does
                    navController.navigate(Screen.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsHubScreen(
                onNavigateToAppearance = { navController.navigate(Screen.AppearanceSettings.route) },
                onNavigateToUploads = { navController.navigate(Screen.UploadSettings.route) },
                onNavigateToBackup = { navController.navigate(Screen.BackupSettings.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppearanceSettings.route) {
            AppearanceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.UploadSettings.route) {
            UploadSettingsScreen(
                onNavigateToImgurConfig = { navController.navigate(Screen.ImgurConfig.route) },
                onNavigateToS3Config = { navController.navigate(Screen.S3Config.route) },
                onNavigateToFtpConfig = { navController.navigate(Screen.FtpConfig.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BackupSettings.route) {
            BackupSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImgurConfig.route) {
            ImgurConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.S3Config.route) {
            S3ConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.FtpConfig.route) {
            FtpConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.UploadBatch.route,
            arguments = listOf(navArgument("imagePaths") { type = NavType.StringType })
        ) { backStackEntry ->
            val raw = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("imagePaths") ?: "",
                "UTF-8"
            )
            val imagePaths = raw.split("|").filter { it.isNotBlank() }
            UploadScreen(
                imagePath = imagePaths.first(),
                imagePaths = imagePaths,
                onUploadComplete = {
                    navController.popBackStack(Screen.Capture.route, inclusive = false)
                    navController.navigate(Screen.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
