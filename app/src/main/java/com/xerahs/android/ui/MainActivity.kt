package com.xerahs.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
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
import com.xerahs.android.util.BiometricHelper
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private var pendingSharedImagePath by mutableStateOf<String?>(null)
    private var pendingSharedImagePaths by mutableStateOf<List<String>?>(null)
    private var isUnlocked by mutableStateOf(false)
    private var lastBackgroundTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsState()
            val dynamicColor by mainViewModel.dynamicColor.collectAsState()
            val colorTheme by mainViewModel.colorTheme.collectAsState()
            val oledBlack by mainViewModel.oledBlack.collectAsState()
            val biometricLockMode by mainViewModel.biometricLockMode.collectAsState()

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
                            onComplete = { mainViewModel.completeOnboarding() },
                            onSelectDestination = { dest ->
                                mainViewModel.setDefaultDestination(dest)
                            }
                        )
                    } else if (biometricLockMode == "LOCK_APP" && !isUnlocked) {
                        // Lock overlay
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
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.padding(16.dp))
                                Text(
                                    text = "XerahS is locked",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.padding(8.dp))
                                FilledTonalButton(onClick = { promptBiometric() }) {
                                    Text("Unlock")
                                }
                            }
                        }
                    } else {
                        MainScreen(
                            sharedImagePath = pendingSharedImagePath,
                            sharedImagePaths = pendingSharedImagePaths,
                            onSharedImageHandled = {
                                pendingSharedImagePath = null
                                pendingSharedImagePaths = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val mode = mainViewModel.biometricLockMode.value
        if (mode == "LOCK_APP" && !isUnlocked) {
            val timeout = mainViewModel.autoLockTimeout.value
            val elapsed = System.currentTimeMillis() - lastBackgroundTime
            if (lastBackgroundTime == 0L || elapsed >= timeout) {
                if (BiometricHelper.canAuthenticate(this)) {
                    promptBiometric()
                }
            } else {
                isUnlocked = true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val mode = mainViewModel.biometricLockMode.value
        if (mode == "LOCK_APP") {
            lastBackgroundTime = System.currentTimeMillis()
            isUnlocked = false
        }
    }

    private fun promptBiometric() {
        if (!BiometricHelper.canAuthenticate(this)) {
            isUnlocked = true
            return
        }
        BiometricHelper.showPrompt(
            activity = this,
            onSuccess = { isUnlocked = true },
            onFailure = { /* stay locked */ }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
            val path = copyUriToInternal(uri) ?: return
            pendingSharedImagePath = path
            pendingSharedImagePaths = null
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return
            val paths = uris.mapNotNull { copyUriToInternal(it) }
            if (paths.isNotEmpty()) {
                pendingSharedImagePaths = paths
                pendingSharedImagePath = null
            }
        }
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
    sharedImagePaths: List<String>? = null,
    onSharedImageHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem("Browse", Icons.Default.PhotoLibrary, Screen.Capture.route),
        BottomNavItem("History", Icons.Default.History, Screen.History.route),
        BottomNavItem("Explorer", Icons.Default.CloudQueue, Screen.S3Explorer.route),
        BottomNavItem("Settings", Icons.Default.Settings, Screen.Settings.route)
    )

    LaunchedEffect(sharedImagePath) {
        if (sharedImagePath != null) {
            navController.navigate(Screen.Annotation.createRoute(sharedImagePath))
            onSharedImageHandled()
        }
    }

    LaunchedEffect(sharedImagePaths) {
        if (sharedImagePaths != null && sharedImagePaths.isNotEmpty()) {
            navController.navigate(Screen.UploadBatch.createRoute(sharedImagePaths))
            onSharedImageHandled()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in listOf(
        Screen.Capture.route,
        Screen.History.route,
        Screen.S3Explorer.route,
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
