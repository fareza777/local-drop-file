package com.localdrop.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.localdrop.app.AutoBackupStore
import com.localdrop.app.TransferServerService
import com.localdrop.app.ads.AdsManager
import com.localdrop.app.data.AppSettings
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.ui.home.TransferMode

const val DESKTOP_RELEASES = "https://github.com/fareza777/local-drop-file/releases"
private val Categories = listOf("General", "Transfer", "Privacy", "Help")

@Composable
fun SettingsScreen(
    store: LocalDropStore,
    onThemeChanged: (String) -> Unit,
    onShowGuide: (TransferMode) -> Unit,
    onResetOnboarding: () -> Unit,
    onOpenDevices: () -> Unit,
    ads: AdsManager? = null
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(store.settings) }
    var category by rememberSaveable { mutableStateOf("General") }
    var clearHistory by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val server by TransferServerService.state.collectAsState()
    fun update(transform: (AppSettings) -> AppSettings) { settings = transform(settings); store.settings = settings }
    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun enableBackup() {
        update { it.copy(autoBackupEnabled = true) }; AutoBackupStore.setEnabled(context, true)
        message = "Backup enabled. Android schedules checks; keep the PC receiver available."
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (mediaPermissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) enableBackup()
        else message = "Allow photo and video access to enable automatic media backup. Manual file selection still works."
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your workspace, your way", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Changes save automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Categories.forEach { value ->
                FilterChip(selected = category == value, onClick = { category = value; message = "" }, label = { Text(value, style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.weight(1f))
            }
        }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            when (category) {
                "General" -> {
                    item { SettingsGroup("Appearance") {
                        SettingRow(Icons.Default.Palette, "Color theme", "Match your device or choose a theme")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("System", "Light", "Dark").forEach { theme ->
                                FilterChip(selected = settings.theme == theme, onClick = { update { it.copy(theme = theme) }; onThemeChanged(theme) }, label = { Text(theme) }, modifier = Modifier.weight(1f))
                            }
                        }
                    } }
                    item { SettingsGroup("While sharing") {
                        SettingRow(Icons.Default.LightMode, "Keep screen awake", "Only while Browser Share or Remote Private is active", trailing = {
                            Switch(settings.keepScreenOn, { value -> update { it.copy(keepScreenOn = value) } })
                        })
                        SettingRow(Icons.Default.Timer, "Auto-stop sharing", "Stop after ${settings.autoStopMinutes} minutes without a request")
                        Slider(value = settings.autoStopMinutes.toFloat(), onValueChange = { value -> update { it.copy(autoStopMinutes = value.toInt().coerceIn(1,120)) } }, valueRange = 1f..120f, modifier = Modifier.padding(horizontal = 16.dp))
                        Text("Applies the next time sharing starts. Push transfers are not affected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    } }
                }
                "Transfer" -> {
                    item { SettingsGroup("Send to desktop") {
                        OutlinedTextField(value = settings.pushAddress, onValueChange = { value -> update { it.copy(pushAddress = value.trim()) } }, label = { Text("PC receive link") }, placeholder = { Text("http://192.168.1.8:42198?t=…") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp), supportingText = { Text("Copy the private link from Desktop → Receive. Keep its token private.") })
                        SettingRow(Icons.Default.PhotoLibrary, "Automatic media backup", "Periodically send new accessible photos and videos; Android controls scheduling", trailing = {
                            Switch(settings.autoBackupEnabled, { enabled ->
                                if (!enabled) { update { it.copy(autoBackupEnabled = false) }; AutoBackupStore.setEnabled(context, false) }
                                else if (!validReceiverAddress(settings.pushAddress)) message = "Set a valid PC receive link, including its token, first."
                                else if (mediaPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) enableBackup()
                                else permissions.launch(mediaPermissions)
                            })
                        })
                        if (settings.autoBackupEnabled) {
                            val last = AutoBackupStore.lastRunAt(context)
                            SettingRow(Icons.Default.History, "Last backup check", if (last == 0L) "Not checked yet" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last)))
                        }
                    } }
                    item { SettingsGroup("Network & storage") {
                        SettingRow(Icons.Default.Wifi, "Local network required", "Allow Wi-Fi, Ethernet or VPN; block mobile-data-only sharing", trailing = { Switch(settings.wifiOnly, { value -> update { it.copy(wifiOnly = value) } }) })
                        SettingRow(Icons.Default.Folder, "Phone storage access", "Browser Share can browse shared phone storage. Android requires All files access.", onClick = {
                            try {
                                val intent = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            } catch (_: Exception) { message = "Open Android Settings → Apps → LocalDrop → Permissions." }
                        })
                        SettingRow(Icons.Default.DeleteSweep, "Clear transfer history", "Remove records only, never your files", onClick = { clearHistory = true })
                    } }
                }
                "Privacy" -> {
                    item { SettingsGroup("Access & pairing") {
                        SettingRow(Icons.Default.Lock, "Browser PIN", "Require an extra PIN on the browser page. New desktop pairings always need approval.", trailing = { Switch(settings.pinProtection, { value -> update { it.copy(pinProtection = value) } }) })
                        Text("PIN changes apply after restarting sharing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingRow(Icons.Default.Devices, "Trusted devices", "Review paired computers and remove their access", onClick = onOpenDevices)
                    } }
                    item { SettingsGroup("Your data") {
                        SettingRow(Icons.Default.Shield, "Know your connection", "LocalDrop does not store files in a cloud. Local HTTP is not encrypted; use trusted Wi-Fi or a secure VPN.")
                        SettingRow(Icons.Default.Info, "Advertising", "Android uses Google test ads. Ad requests contact Google after consent permits them. Release ads are disabled.")
                        if (ads?.privacyRequired == true) SettingRow(Icons.Default.PrivacyTip, "Advertising privacy options", "Review or change your consent", onClick = { ads.showPrivacyOptions() })
                        SettingRow(Icons.Default.CheckCircle, "Ad status", ads?.status ?: "Ads unavailable")
                    } }
                }
                "Help" -> {
                    item { SettingsGroup("Quick guides") {
                        TransferMode.entries.forEach { mode -> SettingRow(mode.icon, mode.displayName, mode.needs, onClick = { onShowGuide(mode) }) }
                        SettingRow(Icons.Default.Refresh, "Replay welcome guide", "Review the basics without resetting files or settings", onClick = onResetOnboarding)
                    } }
                    item { SettingsGroup("Desktop companion") {
                        SettingRow(Icons.Default.Computer, "Get LocalDrop for Windows", "Open the official releases page. Installer availability depends on the publisher.", onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DESKTOP_RELEASES))) }
                            catch (_: Exception) { message = "No browser available. Visit $DESKTOP_RELEASES" }
                        })
                        SettingRow(Icons.Default.Share, "Share the desktop download page", "Send the official link to your computer or a friend", onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "LocalDrop Desktop for Windows\n$DESKTOP_RELEASES"), "Share desktop download"))
                        })
                    } }
                    item { SettingsGroup("About") {
                        SettingRow(Icons.Default.Info, "LocalDrop 1.0.0", if (server.running) "Sharing is active on this phone" else "File transfer on your own network")
                    } }
                }
            }
        }
    }
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text("Clear transfer history?") }, text = { Text("Transfer records on this phone will be removed. Your files stay intact.") }, confirmButton = { TextButton(onClick = { store.clearHistory(); clearHistory = false; message = "Transfer history cleared." }) { Text("Clear records") } }, dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("Cancel") } })
}

fun validReceiverAddress(value: String): Boolean = try {
    val url = java.net.URI(value.trim())
    url.scheme in listOf("http", "https") && !url.host.isNullOrBlank() && url.userInfo == null &&
        url.rawQuery?.split('&')?.any { it.startsWith("t=") && it.length > 2 } == true
} catch (_: Exception) { false }

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(content = content) }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp).size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
