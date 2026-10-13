package com.localdrop.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localdrop.app.TransferServerService
import com.localdrop.app.data.AppSettings
import com.localdrop.app.data.LocalDropStore

private val ThemeOptions = listOf("System", "Light", "Dark")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: LocalDropStore,
    onThemeChanged: (String) -> Unit,
    onShowGuide: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var settings by remember { mutableStateOf(store.settings) }
    var showTheme by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val serverRunning by TransferServerService.state.collectAsState()

    fun update(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        store.settings = settings
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Appearance
        item {
            SettingsGroup("Appearance") {
                SettingRow(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = settings.theme,
                    onClick = { showTheme = true }
                )
            }
        }

        // Auto camera backup (#4)
        item {
            SettingsGroup("Auto camera backup") {
                SettingRow(
                    icon = Icons.Default.PhotoCamera,
                    title = "Back up camera roll to PC",
                    subtitle = if (settings.autoBackupEnabled)
                        "On · new photos & videos push automatically"
                    else
                        "New photos & videos push to your PC automatically",
                    trailing = {
                        Switch(
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = { on ->
                                update { s -> s.copy(autoBackupEnabled = on) }
                                com.localdrop.app.AutoBackupStore.setEnabled(context, on)
                            }
                        )
                    }
                )
                if (settings.autoBackupEnabled) {
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    val last = com.localdrop.app.AutoBackupStore.lastRunAt(context)
                    SettingRow(
                        icon = Icons.Default.History,
                        title = "Last check",
                        subtitle = if (last == 0L) "Not run yet" else "Ran " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last)),
                        trailing = null
                    )
                }
            }
        }

        // Transfer
        item {
            SettingsGroup("Transfer") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto-stop", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Stop sharing after ${settings.autoStopMinutes} min of inactivity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Slider(
                        value = settings.autoStopMinutes.toFloat(),
                        onValueChange = { v -> settings = settings.copy(autoStopMinutes = v.toInt().coerceIn(1, 120)) },
                        onValueChangeFinished = { store.settings = settings },
                        valueRange = 1f..120f,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Folder,
                    title = "Storage location",
                    subtitle = "App storage / LocalDrop / drop",
                    trailing = null
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear history",
                    subtitle = "Remove all transfer records from this phone",
                    onClick = { showClearHistory = true }
                )
            }
        }

        // Security
        item {
            SettingsGroup("Security") {
                SettingRow(
                    icon = Icons.Default.Lock,
                    title = "PIN protection",
                    subtitle = "Require a PIN before a new device can connect",
                    trailing = {
                        Switch(
                            checked = settings.pinProtection,
                            onCheckedChange = { update { s -> s.copy(pinProtection = it) } }
                        )
                    }
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Devices,
                    title = "Trusted devices",
                    subtitle = "View and remove devices you have paired",
                    chevron = true,
                    onClick = null
                )
            }
        }

        // Network
        item {
            SettingsGroup("Network") {
                SettingRow(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi only",
                    subtitle = "Only run sharing on Wi-Fi, not mobile data",
                    trailing = {
                        Switch(
                            checked = settings.wifiOnly,
                            onCheckedChange = { update { s -> s.copy(wifiOnly = it) } }
                        )
                    }
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.LightMode,
                    title = "Keep screen on",
                    subtitle = "Stop the screen from sleeping while a share is running",
                    trailing = {
                        Switch(
                            checked = settings.keepScreenOn,
                            onCheckedChange = { update { s -> s.copy(keepScreenOn = it) } }
                        )
                    }
                )
            }
        }

        // Help
        item {
            SettingsGroup("Help") {
                SettingRow(
                    icon = Icons.Default.MenuBook,
                    title = "Mode guides",
                    subtitle = "Step-by-step help for each transfer mode",
                    chevron = true,
                    onClick = onShowGuide
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Refresh,
                    title = "Replay welcome guide",
                    subtitle = "Show the first-run onboarding again",
                    chevron = true,
                    onClick = onResetOnboarding
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version ${"1.0.0"}",
                    chevron = true,
                    onClick = { showAbout = true }
                )
            }
        }

        item {
            Text(
                "LocalDrop. No cloud, no accounts, no servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeOptions.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.theme == option,
                                onClick = {
                                    update { s -> s.copy(theme = option) }
                                    onThemeChanged(option)
                                    showTheme = false
                                }
                            )
                            Text(option, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Close") } }
        )
    }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text("Clear history?") },
            text = { Text("This removes all transfer records on this phone. Files are not deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.clearHistory()
                        showClearHistory = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text("Cancel") } }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("LocalDrop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Version 1.0.0")
                    Text("Private file transfer on your own network.")
                    Text(
                        if (serverRunning.running) "Server status: running" else "Server status: stopped",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickableRow(onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            trailing != null -> trailing()
            chevron -> Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
