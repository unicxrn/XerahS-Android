package com.xerahs.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xerahs.android.core.common.file.EditorRouting
import com.xerahs.android.core.domain.model.BiometricLockMode
import com.xerahs.android.core.ui.lumen.LumenCard
import com.xerahs.android.core.ui.lumen.LumenNavPill
import com.xerahs.android.core.ui.lumen.NavPillItem
import com.xerahs.android.feature.upload.intake.LinkIntakeSheet
import com.xerahs.android.feature.upload.intake.LinkIntakeViewModel
import com.xerahs.android.feature.upload.intake.singleUrlOrNull
import com.xerahs.android.shortcuts.ProfileShortcuts
import com.xerahs.android.ui.intake.ClipAction
import com.xerahs.android.ui.intake.ClipboardIntake
import com.xerahs.android.ui.intake.IntakeUriPolicy
import com.xerahs.android.ui.navigation.Screen
import com.xerahs.android.ui.navigation.XerahSNavGraph
import com.xerahs.android.ui.navigation.setupRouteFor
import com.xerahs.android.ui.onboarding.OnboardingScreen
import com.xerahs.android.ui.theme.XerahSTheme
import com.xerahs.android.util.BiometricHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val ACTION_CAPTURE = "com.xerahs.android.action.CAPTURE"
        const val ACTION_UPLOAD_CLIPBOARD = "com.xerahs.android.action.UPLOAD_CLIPBOARD"
        private const val KEY_PENDING_CLIPBOARD = "pendingClipboard"
        private const val PROMPT_RETRY_GUARD_MS = 1500L
        private const val DEEP_LINK_SCHEME = "xerahs"
    }

    private val mainViewModel: MainViewModel by viewModels()
    private val linkIntakeViewModel: LinkIntakeViewModel by viewModels()
    private var promptShowing = false
    private var lastPromptEndedAt = 0L
    private val ownAuthorities by lazy { setOf("$packageName.fileprovider") }
    /** Set by the "Upload clipboard" shortcut; the clipboard is read once the window has focus. */
    private var pendingClipboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleIncomingIntent(intent, fresh = true)
        } else {
            pendingClipboard = savedInstanceState.getBoolean(KEY_PENDING_CLIPBOARD)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.intakeMessages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // A share sent straight to a profile is queued; go back to the app it came from.
                mainViewModel.finishRequests.collect { finish() }
            }
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
            val lockNeedsSetup by mainViewModel.lockNeedsSetup.collectAsState()
            val pendingSharedPaths by mainViewModel.pendingSharedPaths.collectAsState()
            val pendingLaunchCapture by mainViewModel.pendingLaunchCapture.collectAsState()
            val pendingImportPath by mainViewModel.pendingImportPath.collectAsState()
            val pendingDeepLink by mainViewModel.pendingDeepLink.collectAsState()
            val pendingSetupRoute by mainViewModel.pendingSetupRoute.collectAsState()

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
                    // Keep the window out of recents and screenshots for as long as App lock is on.
                    DisposableEffect(biometricLockMode) {
                        if (biometricLockMode == BiometricLockMode.LOCK_APP) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                    }
                    // Cold start: ask as soon as the stored mode is known.
                    LaunchedEffect(biometricLockMode, onboardingState) {
                        if (biometricLockMode == BiometricLockMode.LOCK_APP && onboardingState == true &&
                            !mainViewModel.isUnlocked.value
                        ) {
                            promptBiometric(auto = true)
                        }
                    }
                    if (onboardingState == null || biometricLockMode == null) {
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
                                onComplete = { dest ->
                                    mainViewModel.completeOnboarding(dest?.let(::setupRouteFor))
                                },
                                onSelectDestination = { dest ->
                                    mainViewModel.setDefaultDestination(dest)
                                }
                            )
                        } else if (biometricLockMode == BiometricLockMode.LOCK_APP && !isUnlocked) {
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
                                    if (lockNeedsSetup) {
                                        Text(
                                            text = "Set up a screen lock to use App lock",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 32.dp)
                                        )
                                        Spacer(modifier = Modifier.padding(8.dp))
                                        OutlinedButton(
                                            onClick = { openSecuritySettings() },
                                            shape = CircleShape
                                        ) {
                                            Text("Open settings")
                                        }
                                    } else {
                                        OutlinedButton(onClick = { promptBiometric(auto = false) }, shape = CircleShape) {
                                            Text("Unlock")
                                        }
                                    }
                                }
                            }
                        } else {
                            MainScreen(
                                sharedPaths = pendingSharedPaths,
                                onSharedHandled = { mainViewModel.clearSharedPaths() },
                                launchCapture = pendingLaunchCapture,
                                onLaunchCaptureHandled = { mainViewModel.clearLaunchCapture() },
                                importPath = pendingImportPath,
                                onImportHandled = { mainViewModel.clearImportPath() },
                                deepLink = pendingDeepLink,
                                onDeepLinkHandled = { mainViewModel.clearDeepLink() },
                                setupRoute = pendingSetupRoute,
                                onSetupRouteHandled = { mainViewModel.clearSetupRoute() },
                                onUploadClipboard = ::uploadClipboard
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
        // Mode not loaded yet: the cold-start effect in setContent handles it.
        if (mode != BiometricLockMode.LOCK_APP) return
        val backgroundedAt = mainViewModel.lastBackgroundTime
        mainViewModel.lastBackgroundTime = 0L
        val wasUnlocked = backgroundedAt != 0L
        // Unlocked and never left (for example a rotation): nothing to do.
        if (mainViewModel.isUnlocked.value && !wasUnlocked) return
        val elapsed = System.currentTimeMillis() - backgroundedAt
        if (LockPolicy.shouldPrompt(mode, wasUnlocked, elapsed, mainViewModel.autoLockTimeout.value)) {
            mainViewModel.setUnlocked(false)
            promptBiometric(auto = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PENDING_CLIPBOARD, pendingClipboard)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android 10+ only lets the focused app read the clipboard, so wait for focus.
        if (hasFocus && pendingClipboard) {
            pendingClipboard = false
            uploadClipboard()
        }
    }

    /** Same flow as Home's "Upload clipboard": a file copies, a link opens the link sheet, text uploads. */
    private fun uploadClipboard() {
        val item = getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val uri = item?.uri
        val text = item?.text?.toString()
        when (val action = ClipboardIntake.decide(uri?.let { IntakeUriPolicy.isAllowed(it, ownAuthorities) }, text)) {
            ClipAction.CopyUri -> mainViewModel.handleSend(uri, null)
            is ClipAction.Link -> linkIntakeViewModel.open(action.url)
            is ClipAction.Text -> mainViewModel.handleSend(null, action.text)
            ClipAction.Unreadable -> mainViewModel.showIntakeMessage("Couldn't read that file")
            ClipAction.Empty -> mainViewModel.showIntakeMessage("Your clipboard is empty")
        }
    }

    override fun onStop() {
        super.onStop()
        // A rotation also stops the activity, but it must not lock the app.
        if (mainViewModel.biometricLockMode.value == BiometricLockMode.LOCK_APP && !isChangingConfigurations) {
            // Only an unlocked app can start the timeout; leaving the lock screen never unlocks.
            mainViewModel.lastBackgroundTime =
                if (mainViewModel.isUnlocked.value) System.currentTimeMillis() else 0L
        }
    }

    private fun openSecuritySettings() {
        try {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    /** Shows the prompt. Cancelling leaves the app locked; an auto prompt will not loop on cancel. */
    private fun promptBiometric(auto: Boolean) {
        if (promptShowing) return
        if (auto && SystemClock.elapsedRealtime() - lastPromptEndedAt < PROMPT_RETRY_GUARD_MS) return
        if (!BiometricHelper.canAuthenticate(this)) {
            // Never unlock for free. Only a missing screen lock gets the setup message.
            mainViewModel.setLockNeedsSetup(BiometricHelper.needsScreenLockSetup(this))
            return
        }
        mainViewModel.setLockNeedsSetup(false)
        promptShowing = true
        BiometricHelper.showPrompt(
            activity = this,
            onSuccess = {
                promptShowing = false
                lastPromptEndedAt = SystemClock.elapsedRealtime()
                mainViewModel.setUnlocked(true)
            },
            onFailure = {
                promptShowing = false
                lastPromptEndedAt = SystemClock.elapsedRealtime()
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent, fresh = false)
    }

    /** [fresh] is true when this activity was started for [intent], so it may finish after a direct share. */
    private fun handleIncomingIntent(intent: Intent, fresh: Boolean) {
        // Reopened from recents: the share or shortcut was handled when it first arrived.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == DEEP_LINK_SCHEME) {
            mainViewModel.setDeepLink(intent.data.toString())
            // Navigate once, from MainScreen (after onboarding and the app lock), not again when
            // the NavController reads the launch intent on its own.
            setIntent(Intent(intent).setData(null))
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (IntakeUriPolicy.isAllowed(uri, ownAuthorities)) {
                    mainViewModel.handleImport(uri)
                } else {
                    toastUnreadable()
                }
            }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (uri == null && text == null) return
                if (uri != null && !IntakeUriPolicy.isAllowed(uri, ownAuthorities)) {
                    toastUnreadable()
                    return
                }
                // A shared link alone opens the link sheet; other text uploads as a text file.
                val link = if (uri == null && text != null) singleUrlOrNull(text) else null
                // A profile shortcut uploads straight away, a lone link included (as text).
                val profileId = shortcutProfileId(intent)
                when {
                    profileId != null -> mainViewModel.uploadToProfile(profileId, listOfNotNull(uri), text, finishAfter = fresh)
                    link != null -> linkIntakeViewModel.open(link)
                    else -> mainViewModel.handleSend(uri, text)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: return
                val allowed = uris.filter { IntakeUriPolicy.isAllowed(it, ownAuthorities) }
                if (allowed.isEmpty()) {
                    toastUnreadable()
                    return
                }
                val profileId = shortcutProfileId(intent)
                if (profileId != null) {
                    mainViewModel.uploadToProfile(profileId, allowed, null, finishAfter = fresh)
                } else {
                    mainViewModel.handleSendMultiple(allowed)
                }
            }
            ACTION_CAPTURE -> mainViewModel.requestLaunchCapture()
            ACTION_UPLOAD_CLIPBOARD -> {
                pendingClipboard = true
                if (hasWindowFocus()) {
                    pendingClipboard = false
                    uploadClipboard()
                }
            }
        }
    }

    private fun shortcutProfileId(intent: Intent): String? =
        ProfileShortcuts.profileIdOf(intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))

    private fun toastUnreadable() {
        Toast.makeText(this, "Couldn't read that file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(
    sharedPaths: List<String>? = null,
    onSharedHandled: () -> Unit = {},
    launchCapture: Boolean = false,
    onLaunchCaptureHandled: () -> Unit = {},
    importPath: String? = null,
    onImportHandled: () -> Unit = {},
    deepLink: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    setupRoute: String? = null,
    onSetupRouteHandled: () -> Unit = {},
    onUploadClipboard: () -> Unit = {}
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    // Activity-scoped (MainScreen sits outside the NavHost), shared with MainActivity's intake.
    val linkIntakeViewModel: LinkIntakeViewModel = hiltViewModel()
    val s3Configured by mainViewModel.s3Configured.collectAsStateWithLifecycle()

    val linkReadyPath by linkIntakeViewModel.readyPath.collectAsStateWithLifecycle()
    LaunchedEffect(linkReadyPath) {
        val path = linkReadyPath ?: return@LaunchedEffect
        // Same routing as any shared file (editor for editable images, else Upload).
        mainViewModel.offerSharedPaths(listOf(path))
        linkIntakeViewModel.consumeReady()
    }

    LaunchedEffect(sharedPaths) {
        val paths = sharedPaths ?: return@LaunchedEffect
        // A direct share that still needs input keeps its profile and skips the editor.
        val profileId = mainViewModel.pendingSharedProfileId.value
        if (profileId != null) {
            navController.navigate(
                paths.singleOrNull()?.let { Screen.Upload.createRoute(it, profileId) }
                    ?: Screen.UploadBatch.createRoute(paths, profileId)
            )
            onSharedHandled()
            return@LaunchedEffect
        }
        val single = paths.singleOrNull()
        val editable = single != null && withContext(Dispatchers.IO) { EditorRouting.opensInEditor(single) }
        when {
            // Editable raster image → editor (HEIC, GIF and animated WebP go straight to upload).
            single != null && editable ->
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

    LaunchedEffect(importPath) {
        if (importPath != null) {
            navController.navigate(Screen.UploaderImport.createRoute(importPath))
            onImportHandled()
        }
    }

    LaunchedEffect(setupRoute) {
        if (setupRoute != null) {
            // A share, import or link that is already waiting wins; the setup hint is dropped.
            val otherPending = sharedPaths != null || importPath != null || deepLink != null
            if (!otherPending) navController.navigate(setupRoute)
            onSetupRouteHandled()
        }
    }

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            val uri = Uri.parse(deepLink)
            // Only links the graph declares; anything else is ignored rather than crashing navigate().
            if (navController.graph.hasDeepLink(uri)) navController.navigate(uri)
            onDeepLinkHandled()
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
            modifier = Modifier.fillMaxSize(),
            onUploadClipboard = onUploadClipboard,
            onUploadFromLink = { linkIntakeViewModel.openEntry() }
        )
        LinkIntakeSheet(linkIntakeViewModel)
        val intakeBusy by mainViewModel.intakeBusy.collectAsStateWithLifecycle()
        AnimatedVisibility(
            visible = intakeBusy,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            LumenCard(radius = 999.dp, elevation = 8.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Copying file", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
