package com.xerahs.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xerahs.android.ui.navigation.Screen
import com.xerahs.android.ui.navigation.XerahSNavGraph
import com.xerahs.android.ui.onboarding.OnboardingScreen
import com.xerahs.android.ui.theme.XerahSTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private var pendingSharedImagePath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingSharedImagePath = handleIncomingIntent(intent)

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsState()
            val dynamicColor by mainViewModel.dynamicColor.collectAsState()
            val colorTheme by mainViewModel.colorTheme.collectAsState()
            val oledBlack by mainViewModel.oledBlack.collectAsState()

            XerahSTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorTheme = colorTheme,
                oledBlack = oledBlack
            ) {
                Crossfade(
                    targetState = onboardingCompleted,
                    animationSpec = tween(500),
                    label = "onboarding-crossfade"
                ) { completed ->
                    if (!completed) {
                        OnboardingScreen(
                            onComplete = { mainViewModel.completeOnboarding() }
                        )
                    } else {
                        MainScreen(
                            sharedImagePath = pendingSharedImagePath,
                            onSharedImageHandled = { pendingSharedImagePath = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingSharedImagePath = handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent): String? {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
            return copyUriToInternal(uri)
        }
        return null
    }

    private fun copyUriToInternal(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val capturesDir = File(filesDir, "captures")
            if (!capturesDir.exists()) capturesDir.mkdirs()
            val file = File(capturesDir, "shared_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun MainScreen(
    sharedImagePath: String? = null,
    onSharedImageHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem("Browse", Icons.Default.PhotoLibrary, Screen.Capture.route),
        BottomNavItem("History", Icons.Default.History, Screen.History.route),
        BottomNavItem("Settings", Icons.Default.Settings, Screen.Settings.route)
    )

    LaunchedEffect(sharedImagePath) {
        if (sharedImagePath != null) {
            navController.navigate(Screen.Annotation.createRoute(sharedImagePath))
            onSharedImageHandled()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in listOf(
        Screen.Capture.route,
        Screen.History.route,
        Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = {
                                Text(
                                    text = item.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        XerahSNavGraph(
            navController = navController,
            startDestination = Screen.Capture.route
        )
    }
}
