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
import com.xerahs.android.feature.settings.SecuritySettingsScreen
import com.xerahs.android.feature.settings.AppUpdateScreen
import com.xerahs.android.feature.settings.SettingsHubScreen
import com.xerahs.android.feature.settings.StatisticsScreen
import com.xerahs.android.feature.settings.StorageSettingsScreen
import com.xerahs.android.feature.settings.ThemeEditorScreen
import com.xerahs.android.feature.settings.UploadSettingsScreen
import com.xerahs.android.feature.settings.profiles.ProfileEditorScreen
import com.xerahs.android.feature.settings.profiles.ProfileManagementScreen
import com.xerahs.android.feature.settings.profiles.ProfileManagementViewModel
import com.xerahs.android.feature.settings.destinations.CustomHttpConfigScreen
import com.xerahs.android.feature.settings.destinations.FtpConfigScreen
import com.xerahs.android.feature.settings.destinations.ImgurConfigScreen
import com.xerahs.android.feature.settings.destinations.S3ConfigScreen
import com.xerahs.android.feature.s3explorer.S3ExplorerScreen
import com.xerahs.android.feature.s3explorer.S3StatsScreen
import com.xerahs.android.feature.upload.UploadScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xerahs.android.ui.MainViewModel
import com.xerahs.android.util.BiometricHelper

sealed class Screen(val route: String) {
    data object Capture : Screen("capture")
    data object Annotation : Screen("annotation/{imagePath}") {
        fun createRoute(imagePath: String) = "annotation/${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
    }
    data object Upload : Screen("upload/{imagePath}") {
        fun createRoute(imagePath: String) = "upload/${java.net.URLEncoder.encode(imagePath, "UTF-8")}"
    }
    data object History : Screen("history")
    data object S3Explorer : Screen("s3explorer")
    data object S3Stats : Screen("s3explorer/stats")
    data object Settings : Screen("settings")
    data object AppearanceSettings : Screen("settings/appearance")
    data object UploadSettings : Screen("settings/uploads")
    data object BackupSettings : Screen("settings/backup")
    data object ImgurConfig : Screen("settings/imgur")
    data object S3Config : Screen("settings/s3")
    data object FtpConfig : Screen("settings/ftp")
    data object CustomHttpConfig : Screen("settings/custom-http")
    data object StorageSettings : Screen("settings/storage")
    data object SecuritySettings : Screen("settings/security")
    data object Statistics : Screen("settings/statistics")
    data object ThemeEditor : Screen("settings/theme-editor")
    data object ProfileManagement : Screen("settings/profiles")
    data object ProfileEditor : Screen("settings/profiles/edit/{profileId}") {
        fun createRoute(profileId: String?) = "settings/profiles/edit/${profileId ?: "new"}"
    }
    data object AppUpdate : Screen("settings/updates")
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

        composable(Screen.S3Explorer.route) {
            S3ExplorerScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.S3Config.route)
                },
                onNavigateToStats = {
                    navController.navigate(Screen.S3Stats.route)
                }
            )
        }

        composable(Screen.S3Stats.route) {
            S3StatsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsHubScreen(
                onNavigateToAppearance = { navController.navigate(Screen.AppearanceSettings.route) },
                onNavigateToUploads = { navController.navigate(Screen.UploadSettings.route) },
                onNavigateToBackup = { navController.navigate(Screen.BackupSettings.route) },
                onNavigateToStorage = { navController.navigate(Screen.StorageSettings.route) },
                onNavigateToSecurity = { navController.navigate(Screen.SecuritySettings.route) },
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) },
                onNavigateToUpdates = { navController.navigate(Screen.AppUpdate.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppearanceSettings.route) {
            AppearanceSettingsScreen(
                onNavigateToThemeEditor = { navController.navigate(Screen.ThemeEditor.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ThemeEditor.route) {
            val settingsViewModel: com.xerahs.android.feature.settings.SettingsViewModel = hiltViewModel()
            ThemeEditorScreen(
                onSave = { theme ->
                    settingsViewModel.saveCustomTheme(theme)
                    settingsViewModel.selectCustomTheme(theme.id)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.UploadSettings.route) {
            UploadSettingsScreen(
                onNavigateToImgurConfig = { navController.navigate(Screen.ImgurConfig.route) },
                onNavigateToS3Config = { navController.navigate(Screen.S3Config.route) },
                onNavigateToFtpConfig = { navController.navigate(Screen.FtpConfig.route) },
                onNavigateToCustomHttpConfig = { navController.navigate(Screen.CustomHttpConfig.route) },
                onNavigateToProfiles = { navController.navigate(Screen.ProfileManagement.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BackupSettings.route) {
            BackupSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.StorageSettings.route) {
            StorageSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ProfileManagement.route) {
            ProfileManagementScreen(
                onNavigateToEditor = { profileId ->
                    navController.navigate(Screen.ProfileEditor.createRoute(profileId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ProfileEditor.route,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.ProfileManagement.route)
            }
            val viewModel: ProfileManagementViewModel = hiltViewModel(parentEntry)

            androidx.compose.runtime.LaunchedEffect(profileId) {
                if (profileId == "new") {
                    viewModel.startNewProfile()
                } else if (profileId != null) {
                    viewModel.startEditProfile(profileId)
                }
            }

            ProfileEditorScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable(Screen.SecuritySettings.route) {
            SecuritySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppUpdate.route) {
            AppUpdateScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImgurConfig.route) {
            BiometricGate(navController) {
                ImgurConfigScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.S3Config.route) {
            BiometricGate(navController) {
                S3ConfigScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.FtpConfig.route) {
            BiometricGate(navController) {
                FtpConfigScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.CustomHttpConfig.route) {
            BiometricGate(navController) {
                CustomHttpConfigScreen(onBack = { navController.popBackStack() })
            }
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

@Composable
private fun BiometricGate(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val mainViewModel: MainViewModel = hiltViewModel()
    val lockMode by mainViewModel.biometricLockMode.collectAsState()

    if (lockMode != "LOCK_CREDENTIALS") {
        content()
        return
    }

    var authenticated by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (authenticated) {
        content()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.padding(12.dp))
                Text(
                    text = "Authentication required",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.padding(8.dp))
                FilledTonalButton(onClick = {
                    val activity = context as? androidx.fragment.app.FragmentActivity
                    if (activity != null && BiometricHelper.canAuthenticate(activity)) {
                        BiometricHelper.showPrompt(
                            activity = activity,
                            title = "Verify Identity",
                            subtitle = "Authenticate to access credentials",
                            onSuccess = { authenticated = true },
                            onFailure = { navController.popBackStack() }
                        )
                    } else {
                        authenticated = true
                    }
                }) {
                    Text("Authenticate")
                }
            }
        }

        // Auto-prompt on first composition
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null && BiometricHelper.canAuthenticate(activity)) {
                BiometricHelper.showPrompt(
                    activity = activity,
                    title = "Verify Identity",
                    subtitle = "Authenticate to access credentials",
                    onSuccess = { authenticated = true },
                    onFailure = { navController.popBackStack() }
                )
            } else {
                authenticated = true
            }
        }
    }
}
