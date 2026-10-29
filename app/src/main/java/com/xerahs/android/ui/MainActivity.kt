package com.xerahs.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.xerahs.android.core.ui.lumen.LumenNavPill
import com.xerahs.android.core.ui.lumen.NavPillItem
import com.xerahs.android.ui.navigation.Screen
import com.xerahs.android.ui.navigation.XerahSNavGraph
import com.xerahs.android.ui.onboarding.OnboardingScreen
import com.xerahs.android.ui.theme.XerahSTheme
import com.xerahs.android.util.BiometricHelper
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.content.IntentCompat
import com.xerahs.android.core.common.file.MimeTypes

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val ACTION_CAPTURE = "com.xerahs.android.action.CAPTURE"
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsState()
            val dynamicColor by mainViewModel.dynamicColor.collectAsState()
            val colorTheme by mainViewModel.colorTheme.collectAsState()
            val oledBlack by mainViewModel.oledBlack.collectAsState()
            val biometricLockMode by mainViewModel.biometricLockMode.collectAsState()
            val customThemeSeedColor by mainViewModel.customThemeSeedColor.collectAsState()
            val isUnlocked by mainViewModel.isUnlocked.collectAsState()
            val pendingSharedPaths by mainViewModel.pendingSharedPaths.collectAsState()
            val pendingLaunchCapture by mainViewModel.pendingLaunchCapture.collectAsState()
            val pendingImportUri by mainViewModel.pendingImportUri.collectAsState()

            XerahSTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorTheme = colorTheme,
                oledBlack = oledBlack,
                customThemeSeedColor = customThemeSeedColor
            ) {
                // Surface supplies the background and the default content colour for everything
                // below: the loading placeholder, onboarding, the lock screen and the main app.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val onboardingState = onboardingCompleted
                    if (onboardingState == null) {
                        // Settings still loading: show only the background. Composing Home here would
                        // consume a pending share before onboarding takes over.
                        Box(Modifier.fillMaxSize())
                    } else Crossfade(
                        targetState = onboardingState,
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
                                modifier = Modifier.fillMaxSize(),
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
                                    OutlinedButton(onClick = { promptBiometric() }, shape = CircleShape) {
                                        Text("Unlock")
                                    }
                                }
                            }
                        } else {
                            MainScreen(
                                sharedPaths = pendingSharedPaths,
                                onSharedHandled = { mainViewModel.clearSharedPaths() },
                                launchCapture = pendingLaunchCapture,
                                onLaunchCaptureHandled = { mainViewModel.clearLaunchCapture() },
                                importUri = pendingImportUri,
                                onImportHandled = { mainViewModel.clearImportUri() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val mode = mainViewModel.biometricLockMode.value
        if (mode == "LOCK_APP" && !mainViewModel.isUnlocked.value) {
            val timeout = mainViewModel.autoLockTimeout.value
            val lastBackgroundTime = mainViewModel.lastBackgroundTime
            val elapsed = System.currentTimeMillis() - lastBackgroundTime
            if (lastBackgroundTime == 0L || elapsed >= timeout) {
                if (BiometricHelper.canAuthenticate(this)) {
                    promptBiometric()
                }
            } else {
                mainViewModel.setUnlocked(true)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val mode = mainViewModel.biometricLockMode.value
        // A rotation also stops the activity, but it must not lock the app.
        if (mode == "LOCK_APP" && !isChangingConfigurations) {
            mainViewModel.lastBackgroundTime = System.currentTimeMillis()
            mainViewModel.setUnlocked(false)
        }
    }

    private fun promptBiometric() {
        if (!BiometricHelper.canAuthenticate(this)) {
            mainViewModel.setUnlocked(true)
            return
        }
        BiometricHelper.showPrompt(
            activity = this,
            onSuccess = { mainViewModel.setUnlocked(true) },
            onFailure = { /* stay locked */ }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { mainViewModel.setImportUri(it.toString()) }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (uri == null && text == null) return
                mainViewModel.handleSend(uri, text)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: return
                mainViewModel.handleSendMultiple(uris)
            }
            ACTION_CAPTURE -> mainViewModel.requestLaunchCapture()
        }
    }
}

@Composable
fun MainScreen(
    sharedPaths: List<String>? = null,
    onSharedHandled: () -> Unit = {},
    launchCapture: Boolean = false,
    onLaunchCaptureHandled: () -> Unit = {},
    importUri: String? = null,
    onImportHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val s3Configured by mainViewModel.s3Configured.collectAsStateWithLifecycle()

    LaunchedEffect(sharedPaths) {
        val paths = sharedPaths ?: return@LaunchedEffect
        val single = paths.singleOrNull()
        val mime = single?.let { MimeTypes.fromFileName(it) }
        when {
            // Editable raster image → editor (HEIC goes straight to upload; it is converted there).
            single != null && MimeTypes.isRasterImage(mime) && !MimeTypes.isHeic(mime) ->
                navController.navigate(Screen.Annotation.createRoute(single))
            single != null -> navController.navigate(Screen.Upload.createRoute(single))
            else -> navController.navigate(Screen.UploadBatch.createRoute(paths))
        }
        onSharedHandled()
    }

    LaunchedEffect(launchCapture) {
        if (launchCapture) {
            navController.navigate(Screen.Capture.route)
            onLaunchCaptureHandled()
        }
    }

    LaunchedEffect(importUri) {
        if (importUri != null) {
            navController.navigate(Screen.UploaderImport.createRoute(importUri))
            onImportHandled()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.S3Explorer.route,
        Screen.Tools.route,
        Screen.Settings.route
    )

    Box(Modifier.fillMaxSize()) {
        XerahSNavGraph(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = showBottomBar,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            val items = buildList {
                add(NavPillItem(Screen.Home.route, "Home", Icons.Outlined.Home))
                if (s3Configured) add(NavPillItem(Screen.S3Explorer.route, "Cloud", Icons.Outlined.Cloud))
                add(NavPillItem(Screen.Tools.route, "Tools", Icons.Outlined.GridView))
                add(NavPillItem(Screen.Settings.route, "Settings", Icons.Outlined.Tune))
            }
            LumenNavPill(items = items, selectedKey = currentRoute, onSelect = { route ->
                if (route == currentRoute) return@LumenNavPill
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            })
        }
    }
}
