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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
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

    companion object {
        const val ACTION_CAPTURE = "com.xerahs.android.action.CAPTURE"
    }

    private val mainViewModel: MainViewModel by viewModels()
    private var pendingSharedImagePath by mutableStateOf<String?>(null)
    private var pendingSharedImagePaths by mutableStateOf<List<String>?>(null)
    private var pendingLaunchCapture by mutableStateOf(false)
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
            val customThemeSeedColor by mainViewModel.customThemeSeedColor.collectAsState()

            XerahSTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorTheme = colorTheme,
                oledBlack = oledBlack,
                customThemeSeedColor = customThemeSeedColor
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
                            },
                            launchCapture = pendingLaunchCapture,
                            onLaunchCaptureHandled = { pendingLaunchCapture = false }
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
        } else if (intent.action == ACTION_CAPTURE) {
            pendingLaunchCapture = true
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

@Composable
fun MainScreen(
    sharedImagePath: String? = null,
    sharedImagePaths: List<String>? = null,
    onSharedImageHandled: () -> Unit = {},
    launchCapture: Boolean = false,
    onLaunchCaptureHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val s3Configured by mainViewModel.s3Configured.collectAsStateWithLifecycle()

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

    LaunchedEffect(launchCapture) {
        if (launchCapture) {
            navController.navigate(Screen.Capture.route)
            onLaunchCaptureHandled()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.S3Explorer.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                BottomAppBar(
                    actions = {
                        val homeSelected = currentRoute == Screen.Home.route
                        IconButton(
                            onClick = {
                                if (!homeSelected) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) { inclusive = false }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (homeSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (s3Configured) {
                            val cloudSelected = currentRoute == Screen.S3Explorer.route
                            IconButton(
                                onClick = {
                                    if (!cloudSelected) {
                                        navController.navigate(Screen.S3Explorer.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = "Cloud",
                                    tint = if (cloudSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { navController.navigate(Screen.Capture.route) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        XerahSNavGraph(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
