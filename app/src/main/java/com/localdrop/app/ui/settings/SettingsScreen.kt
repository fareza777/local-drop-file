package com.localdrop.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.localdrop.app.AutoBackupStore
import com.localdrop.app.TransferServerService
import com.localdrop.app.ads.AdsManager
import com.localdrop.app.data.AppSettings
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.ui.AppLanguages
import com.localdrop.app.ui.home.TransferMode
import com.localdrop.app.ui.home.title
import com.localdrop.app.ui.home.needsText
import com.localdrop.app.ui.tr

const val DESKTOP_RELEASES = "https://github.com/fareza777/local-drop-file/releases/latest"
private enum class Category(val en: String, val id: String) { GENERAL("General", "Umum"), TRANSFER("Transfer", "Transfer"), PRIVACY("Privacy", "Privasi"), HELP("Help", "Bantuan") }

@Composable
fun SettingsScreen(
    store: LocalDropStore,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onShowGuide: (TransferMode) -> Unit,
    onResetOnboarding: () -> Unit,
    onOpenDevices: () -> Unit,
    ads: AdsManager? = null
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(store.settings) }
    var category by rememberSaveable { mutableStateOf(Category.GENERAL) }
    var clearHistory by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val server by TransferServerService.state.collectAsState()
    fun update(transform: (AppSettings) -> AppSettings) { settings = transform(settings); store.settings = settings }
    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val backupOn = tr("Backup on. Only new photos and videos are sent while the PC receiver is reachable.", "Backup aktif. Hanya foto dan video baru yang dikirim saat penerima PC dapat dijangkau.")
    val backupDenied = tr("Allow photo and video access to enable backup.", "Izinkan akses foto dan video untuk mengaktifkan backup.")
    val needLink = tr("Set a valid PC receive link first.", "Isi tautan terima PC yang valid dulu.")
    fun enableBackup() { update { it.copy(autoBackupEnabled = true) }; AutoBackupStore.setEnabled(context, true); message = backupOn }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (mediaPermissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) enableBackup() else message = backupDenied
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(tr("Settings", "Pengaturan"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
        Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Category.entries.forEach { value ->
                    val on = category == value
                    Surface(Modifier.weight(1f).height(38.dp).clickable { category = value; message = "" }, shape = RoundedCornerShape(10.dp), color = if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, shadowElevation = if (on) 2.dp else 0.dp) {
                        Box(contentAlignment = Alignment.Center) { Text(tr(value.en, value.id), maxLines = 1, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (category) {
                Category.GENERAL -> {
                    item { Group(tr("Appearance", "Tampilan")) {
                        Chips(tr("Theme", "Tema"), Icons.Default.Palette, listOf("System" to tr("System", "Sistem"), "Light" to tr("Light", "Terang"), "Dark" to tr("Dark", "Gelap")), settings.theme) { theme -> update { it.copy(theme = theme) }; onThemeChanged(theme) }
                        Chips(tr("Language", "Bahasa"), Icons.Default.Language, AppLanguages.map { it to (if (it == "System") tr("System", "Sistem") else if (it == "Indonesia") "Indonesia" else "English") }, settings.language) { lang -> update { it.copy(language = lang) }; onLanguageChanged(lang) }
                    } }
                    item { Group(tr("While sharing", "Saat berbagi")) {
                        Row2(Icons.Default.LightMode, tr("Keep screen awake", "Layar tetap menyala"), tr("Only while sharing is active", "Hanya saat berbagi aktif"), trailing = { Switch(settings.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } }) })
                        Row2(Icons.Default.Timer, tr("Auto-stop after ${settings.autoStopMinutes} min idle", "Berhenti otomatis setelah ${settings.autoStopMinutes} mnt siaga"), tr("Applies next time sharing starts", "Berlaku saat berbagi dimulai lagi"))
                        Slider(settings.autoStopMinutes.toFloat(), { v -> update { it.copy(autoStopMinutes = v.toInt().coerceIn(1, 120)) } }, valueRange = 1f..120f, modifier = Modifier.padding(horizontal = 16.dp))
                    } }
                }
                Category.TRANSFER -> {
                    item { Group(tr("Send to desktop", "Kirim ke desktop")) {
                        OutlinedTextField(settings.pushAddress, { v -> update { it.copy(pushAddress = v.trim()) } }, label = { Text(tr("PC receive link", "Tautan terima PC")) }, placeholder = { Text("http://192.168.1.8:42198?t=…") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        Row2(Icons.Default.PhotoLibrary, tr("Automatic photo backup", "Backup foto otomatis"), tr("New photos and videos only, checked about every 30 min", "Hanya foto dan video baru, dicek sekitar tiap 30 mnt"), trailing = {
                            Switch(settings.autoBackupEnabled, { enabled ->
                                if (!enabled) { update { it.copy(autoBackupEnabled = false) }; AutoBackupStore.setEnabled(context, false) }
                                else if (!validReceiverAddress(settings.pushAddress)) message = needLink
                                else if (mediaPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) enableBackup()
                                else permissions.launch(mediaPermissions)
                            })
                        })
                        if (settings.autoBackupEnabled) {
                            val last = AutoBackupStore.lastRunAt(context)
                            Row2(Icons.Default.History, tr("Last backup check", "Pengecekan backup terakhir"), if (last == 0L) tr("Not checked yet", "Belum dicek") else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last)))
                        }
                    } }
                    item { Group(tr("Network & storage", "Jaringan & penyimpanan")) {
                        Row2(Icons.Default.Wifi, tr("Local network only", "Hanya jaringan lokal"), tr("Wi-Fi, Ethernet or VPN. Blocks mobile data only.", "Wi-Fi, Ethernet atau VPN. Memblokir data seluler saja."), trailing = { Switch(settings.wifiOnly, { v -> update { it.copy(wifiOnly = v) } }) })
                        Row2(Icons.Default.Folder, tr("Phone storage access", "Akses penyimpanan ponsel"), tr("Browser Share needs All files access", "Browser Share perlu akses Semua file"), onClick = {
                            try {
                                context.startActivity(if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) { message = "Android Settings → Apps → Pushvia → Permissions" }
                        })
                        Row2(Icons.Default.DeleteSweep, tr("Clear transfer history", "Hapus riwayat transfer"), tr("Removes records only, never your files", "Hanya menghapus catatan, bukan file Anda"), onClick = { clearHistory = true })
                    } }
                }
                Category.PRIVACY -> {
                    item { Group(tr("Access & pairing", "Akses & pairing")) {
                        Row2(Icons.Default.Lock, tr("Browser PIN", "PIN browser"), tr("Extra PIN on the browser page. Applies after restarting sharing.", "PIN tambahan di halaman browser. Berlaku setelah berbagi dimulai ulang."), trailing = { Switch(settings.pinProtection, { v -> update { it.copy(pinProtection = v) } }) })
                        Row2(Icons.Default.Devices, tr("Trusted devices", "Perangkat tepercaya"), tr("Review paired computers and remove access", "Tinjau komputer yang dipasangkan dan cabut akses"), onClick = onOpenDevices)
                    } }
                    item { Group(tr("Your data", "Data Anda")) {
                        Row2(Icons.Default.Shield, tr("No cloud storage", "Tanpa penyimpanan cloud"), tr("Local HTTP is not encrypted. Use trusted Wi-Fi or a secure VPN.", "HTTP lokal tidak terenkripsi. Gunakan Wi-Fi tepercaya atau VPN aman."))
                        Row2(Icons.Default.Info, tr("Advertising", "Iklan"), tr("Test ads only. Requests start after consent permits them.", "Hanya iklan uji. Permintaan dimulai setelah persetujuan mengizinkan."))
                        if (ads?.privacyRequired == true) Row2(Icons.Default.PrivacyTip, tr("Ad privacy options", "Opsi privasi iklan"), tr("Review or change your consent", "Tinjau atau ubah persetujuan"), onClick = { ads.showPrivacyOptions() })
                    } }
                }
                Category.HELP -> {
                    item { Group(tr("Quick guides", "Panduan singkat")) {
                        TransferMode.entries.forEach { mode -> Row2(mode.icon, mode.title(), mode.needsText(), onClick = { onShowGuide(mode) }) }
                        Row2(Icons.Default.Refresh, tr("Replay welcome guide", "Putar ulang panduan awal"), tr("Settings and files stay untouched", "Pengaturan dan file tidak berubah"), onClick = onResetOnboarding)
                    } }
                    item { Group(tr("Desktop companion", "Pendamping desktop")) {
                        Row2(Icons.Default.Computer, tr("Get Pushvia for Windows", "Unduh Pushvia untuk Windows"), tr("Opens the latest release page", "Membuka halaman rilis terbaru"), onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DESKTOP_RELEASES))) } catch (_: Exception) { message = DESKTOP_RELEASES }
                        })
                        Row2(Icons.Default.Share, tr("Share the download link", "Bagikan tautan unduhan"), tr("Send it to your computer", "Kirim ke komputer Anda"), onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Pushvia Desktop for Windows\n$DESKTOP_RELEASES"), null))
                        })
                    } }
                    item { Group(tr("About", "Tentang")) {
                        Row2(Icons.Default.Info, "Pushvia: File Transfer & Sync 1.1.0", if (server.running) tr("Sharing is active on this phone", "Berbagi aktif di ponsel ini") else tr("File transfer on your own network", "Transfer file di jaringan Anda sendiri"))
                    } }
                }
            }
        }
    }
    val historyCleared = tr("History cleared.", "Riwayat dihapus.")
    if (clearHistory) AlertDialog(
        onDismissRequest = { clearHistory = false },
        title = { Text(tr("Clear transfer history?", "Hapus riwayat transfer?")) },
        text = { Text(tr("Records on this phone will be removed. Your files stay intact.", "Catatan di ponsel ini akan dihapus. File Anda tetap aman.")) },
        confirmButton = { TextButton(onClick = { store.clearHistory(); clearHistory = false; message = historyCleared }) { Text(tr("Clear", "Hapus")) } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text(tr("Cancel", "Batal")) } }
    )
}

fun validReceiverAddress(value: String): Boolean = try {
    val url = java.net.URI(value.trim())
    url.scheme in listOf("http", "https") && !url.host.isNullOrBlank() && url.userInfo == null &&
        url.rawQuery?.split('&')?.any { it.startsWith("t=") && it.length > 2 } == true
} catch (_: Exception) { false }

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(content = content) }
    }
}

@Composable
private fun Chips(title: String, icon: ImageVector, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) -> FilterChip(selected == value, { onSelect(value) }, label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Row2(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(7.dp).size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
