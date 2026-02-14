package com.xerahs.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.xerahs.android.feature.annotation.AnnotationScreen
import com.xerahs.android.feature.capture.CaptureScreen
import com.xerahs.android.feature.history.HistoryScreen
import com.xerahs.android.feature.settings.SettingsScreen
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
    data object ImgurConfig : Screen("settings/imgur")
    data object S3Config : Screen("settings/s3")
    data object FtpConfig : Screen("settings/ftp")
}

@Composable
fun XerahSNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Capture.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Capture.route) {
            CaptureScreen(
                onImageCaptured = { imagePath ->
                    navController.navigate(Screen.Annotation.createRoute(imagePath))
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
                        popUpTo(Screen.Capture.route)
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
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.Capture.route)
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
            SettingsScreen(
                onNavigateToImgurConfig = { navController.navigate(Screen.ImgurConfig.route) },
                onNavigateToS3Config = { navController.navigate(Screen.S3Config.route) },
                onNavigateToFtpConfig = { navController.navigate(Screen.FtpConfig.route) },
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
    }
}
