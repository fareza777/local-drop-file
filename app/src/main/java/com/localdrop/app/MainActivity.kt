package com.localdrop.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import com.localdrop.app.ui.guide.ModeGuideSheet

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent {
            val store = remember { LocalDropStore(this) }
            var theme by remember { mutableStateOf(store.settings.theme) }
            LocalDropTheme(themeMode = theme) {
                LocalDropApp(store = store, onThemeChanged = { theme = it })
            }
        }
    }
}
private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Default.Home),
    HISTORY("History", Icons.Default.History),
    DEVICES("Devices", Icons.Default.Devices),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDropApp(store: LocalDropStore, onThemeChanged: (String) -> Unit) {
    val context = LocalContext.current
    // Share-sheet deep link: jump straight to Push mode to set the receiver address.
    val goToPush = (context as? android.app.Activity)?.intent
        ?.getBooleanExtra(com.localdrop.app.ui.share.ShareReceiveActivity.EXTRA_GO_PUSH, false) == true
    var initialTab by remember { mutableStateOf(if (goToPush) Tab.SETTINGS else Tab.HOME) }
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
    // Android 11+: the shared Download/LocalDrop folder needs "All files access" to be read by the browser side.
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }
    var showOnboarding by remember { mutableStateOf(!store.settings.onboardingComplete) }
    var tab by remember { mutableStateOf(initialTab) }
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
    val keepOn = store.settings.keepScreenOn
    KeepScreenOn(enabled = keepOn && serverRunning.running)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.HOME -> "LocalDrop"
                            Tab.HISTORY -> "History"
                            Tab.DEVICES -> "Devices"
                            Tab.SETTINGS -> "Settings"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
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
                    onOpenSettings = { tab = Tab.SETTINGS }
                )
                Tab.HISTORY -> HistoryScreen(store)
                Tab.DEVICES -> DevicesScreen(store)
                Tab.SETTINGS -> SettingsScreen(
                    store = store,
                    onThemeChanged = onThemeChanged,
                    onShowGuide = { guideMode = TransferMode.BROWSER },
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
    val pending by com.localdrop.app.TransferServerService.pairing.collectAsState()
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
