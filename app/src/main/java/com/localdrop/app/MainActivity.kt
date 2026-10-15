package com.localdrop.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.saveable.rememberSaveable
import com.localdrop.app.ads.AdsManager
import com.localdrop.app.ads.TestBanner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.ui.devices.DevicesScreen
import com.localdrop.app.ui.history.HistoryScreen
import com.localdrop.app.ui.home.HomeScreen
import com.localdrop.app.ui.home.TransferMode
import com.localdrop.app.ui.onboarding.OnboardingScreen
import com.localdrop.app.ui.settings.SettingsScreen
import com.localdrop.app.ui.theme.LocalDropTheme
import com.localdrop.app.ui.LocalAppLanguage
import com.localdrop.app.ui.tr
import com.localdrop.app.ui.guide.ModeGuideSheet

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        setContent {
            val store = remember { LocalDropStore(this) }
            var theme by remember { mutableStateOf(store.settings.theme) }
            var language by remember { mutableStateOf(store.settings.language) }
            LocalDropTheme(themeMode = theme) {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    LocalDropApp(store = store, onThemeChanged = { theme = it }, onLanguageChanged = { language = it })
                }
            }
        }
    }
}
private enum class Tab(val en: String, val id: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", "Beranda", Icons.Default.Home),
    HISTORY("History", "Riwayat", Icons.Default.History),
    DEVICES("Devices", "Perangkat", Icons.Default.Devices),
    SETTINGS("Settings", "Pengaturan", Icons.Default.Settings)
}

@Composable
fun LocalDropApp(store: LocalDropStore, onThemeChanged: (String) -> Unit, onLanguageChanged: (String) -> Unit) {
    val context = LocalContext.current
    // Share-sheet deep link: jump straight to Push mode to set the receiver address.
    val goToPush = (context as? android.app.Activity)?.intent
        ?.getBooleanExtra(com.localdrop.app.ui.share.ShareReceiveActivity.EXTRA_GO_PUSH, false) == true
    val initialTab = Tab.HOME
    // Android 13+ needs permission to show the foreground-service notification.
    val notifPermission = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* result is not needed; the server works without it */ }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    var showOnboarding by remember { mutableStateOf(!store.settings.onboardingComplete) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var guideMode by remember { mutableStateOf<TransferMode?>(null) }

    if (showOnboarding) {
        OnboardingScreen(onComplete = {
            store.settings = store.settings.copy(onboardingComplete = true)
            showOnboarding = false
        })
        return
    }

    // Keep the screen on while a share is active, if the user enabled it.
    val serverRunning by com.localdrop.app.TransferServerService.state.collectAsState()
    val push by PushService.state.collectAsState()
    val pending by TransferServerService.pairing.collectAsState()
    var keepOn by remember { mutableStateOf(store.settings.keepScreenOn) }
    LaunchedEffect(tab) { keepOn = store.settings.keepScreenOn }
    KeepScreenOn(enabled = keepOn && serverRunning.running)
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
    val scope = rememberCoroutineScope()
    val ads = remember(context) { (context as? android.app.Activity)?.let { AdsManager(it, scope) } }
    DisposableEffect(ads) {
        ads?.requestConsent()
        onDispose { ads?.dispose() }
    }
    var wasPushing by remember { mutableStateOf(false) }
    LaunchedEffect(push.running) {
        if (wasPushing && !push.running && push.error.isEmpty() && push.filesDone > 0 && push.filesDone == push.filesTotal) ads?.completedBatch()
        wasPushing = push.running
    }

    Scaffold(
        bottomBar = {
            Column {
            if (tab == Tab.HISTORY && ads?.ready == true && !serverRunning.running && !push.running && pending.isEmpty()) TestBanner()
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val label = tr(t.en, t.id)
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (t == Tab.HOME && tab != Tab.HOME) ads?.naturalBreak(serverRunning.running || push.running, pending.isNotEmpty())
                            tab = t
                        },
                        icon = { Icon(t.icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    context = context,
                    store = store,
                    onShowGuide = { guideMode = it },
                    onOpenSettings = { tab = Tab.SETTINGS },
                    initialMode = if (goToPush) TransferMode.PUSH else null
                )
                Tab.HISTORY -> HistoryScreen(store)
                Tab.DEVICES -> DevicesScreen(store)
                Tab.SETTINGS -> SettingsScreen(
                    store = store,
                    onThemeChanged = onThemeChanged,
                    onLanguageChanged = onLanguageChanged,
                    onShowGuide = { guideMode = it },
                    onOpenDevices = { tab = Tab.DEVICES },
                    ads = ads,
                    onResetOnboarding = {
                        store.settings = store.settings.copy(onboardingComplete = false)
                        showOnboarding = true
                    }
                )
            }
        }
    }

    guideMode?.let { mode ->
        ModeGuideSheet(mode = mode, onDismiss = { guideMode = null })
    }

    // Pairing: show the first request that still needs a decision (or the PIN after approval).
    pending.firstOrNull()?.let { request ->
        com.localdrop.app.ui.pairing.PairingDialog(request)
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
