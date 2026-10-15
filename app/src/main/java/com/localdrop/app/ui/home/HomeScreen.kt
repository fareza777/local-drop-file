package com.localdrop.app.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.localdrop.app.PushService
import com.localdrop.app.TransferServerService
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.ui.settings.validReceiverAddress
import com.localdrop.app.ui.tr

private val Ok = Color(0xFF138A68)
private val Warn = Color(0xFFE0703A)
private val BrandBrush = Brush.linearGradient(listOf(Color(0xFF4B6BF0), Color(0xFF2B46B8)))

@Composable
fun HomeScreen(
    context: Context,
    store: LocalDropStore,
    onShowGuide: (TransferMode) -> Unit,
    onOpenSettings: () -> Unit,
    initialMode: TransferMode? = null
) {
    var selectedMode by remember { mutableStateOf(initialMode ?: TransferMode.entries.firstOrNull { it.name == store.settings.lastMode } ?: TransferMode.BROWSER) }
    val serverState by TransferServerService.state.collectAsState()
    val push by PushService.state.collectAsState()
    val accessPin by TransferServerService.accessPin.collectAsState()
    var storageMessage by remember { mutableStateOf("") }
    val needAll = tr("Allow All files access, return here, then tap Start sharing.", "Izinkan akses Semua file, kembali ke sini, lalu ketuk Mulai berbagi.")
    val needStorage = tr("Allow storage access to start Browser Share. Send to PC works with picked files.", "Izinkan akses penyimpanan untuk memulai Browser. Kirim ke PC tetap bisa dengan file pilihan.")
    val needSettings = tr("Enable All files access for Pushvia in Android Settings.", "Aktifkan akses Semua file untuk Pushvia di Pengaturan Android.")
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) TransferServerService.start(context, selectedMode.displayName) else storageMessage = needStorage
    }
    fun startSharing() {
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            storageMessage = needAll
            try { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))) }
            catch (_: Exception) { storageMessage = needSettings }
        } else if (android.os.Build.VERSION.SDK_INT < 30 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
        } else { storageMessage = ""; TransferServerService.start(context, selectedMode.displayName) }
    }
    LaunchedEffect(selectedMode) { store.settings = store.settings.copy(lastMode = selectedMode.name) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(live = serverState.running || push.running, onHelp = { onShowGuide(selectedMode) })
        ModeSelector(selectedMode) { selectedMode = it }
        if (storageMessage.isNotEmpty()) Notice(Icons.Default.Info, storageMessage, MaterialTheme.colorScheme.primary)
        AnimatedContent(selectedMode, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "mode") { mode ->
            if (mode == TransferMode.PUSH) PushCard(context, store)
            else ShareCard(mode, serverState.running, serverState.url, serverState.token, accessPin, serverState.error, ::startSharing) { TransferServerService.stop(context) }
        }
        RecentStrip(store, serverState.running, push.running)
        Spacer(Modifier.weight(1f, fill = false))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenSettings).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Shield, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Local HTTP is not encrypted. Use trusted Wi-Fi or a VPN.", "HTTP lokal tidak terenkripsi. Gunakan Wi-Fi tepercaya atau VPN."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
}

/** The last few transfers, so the Home screen shows proof that things worked without a trip to History. */
@Composable
private fun RecentStrip(store: LocalDropStore, serving: Boolean, pushing: Boolean) {
    val items = remember(serving, pushing) { store.transfers().take(3) }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Recent", "Terbaru"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        items.forEach { item ->
            val sent = item.direction == com.localdrop.app.data.Direction.SEND
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (sent) Icons.Default.Upload else Icons.Default.Download, null, Modifier.size(18.dp), tint = if (sent) MaterialTheme.colorScheme.primary else Ok)
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(fmtBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Header(live: Boolean, onHelp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(BrandBrush), contentAlignment = Alignment.Center) { Icon(Icons.Default.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text("Pushvia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            Text("File Transfer & Sync", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        StatusPill(live)
        IconButton(onClick = onHelp, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.HelpOutline, tr("How to use", "Cara pakai")) }
    }
}

@Composable
private fun StatusPill(live: Boolean) {
    val color = if (live) Ok else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.13f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(if (live) tr("Live", "Aktif") else tr("Idle", "Siaga"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun ModeSelector(selected: TransferMode, onSelect: (TransferMode) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TransferMode.entries.forEach { mode ->
                val on = selected == mode
                Surface(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { onSelect(mode) },
                    shape = RoundedCornerShape(12.dp), color = if (on) MaterialTheme.colorScheme.surface else Color.Transparent, shadowElevation = if (on) 2.dp else 0.dp
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(mode.icon, null, Modifier.size(18.dp), tint = tint)
                        Spacer(Modifier.width(6.dp))
                        Text(mode.label(), style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardShell(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ModeTitle(mode: TransferMode) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(mode.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(mode.icon, null, Modifier.size(24.dp), tint = mode.color) }
        Column(Modifier.weight(1f)) {
            Text(mode.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(mode.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun HeroButton(text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, danger: Boolean = false) {
    val bg = if (!enabled) null else if (danger) null else BrandBrush
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (danger) MaterialTheme.colorScheme.error else Color.Transparent, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxSize().let { if (bg != null) it.background(bg) else it }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun ShareCard(mode: TransferMode, running: Boolean, url: String, token: String, accessPin: String?, error: String, onStart: () -> Unit, onStop: () -> Unit) {
    CardShell {
        ModeTitle(mode)
        if (error.isNotEmpty()) Notice(Icons.Default.Warning, error, Warn)
        if (running && url.isNotEmpty()) {
            LiveShare(url, "$url/?token=$token", accessPin)
            HeroButton(tr("Stop sharing", "Hentikan berbagi"), Icons.Default.Stop, onStop, modifier = Modifier.fillMaxWidth(), danger = true)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Step(1, tr("Use the same Wi-Fi on both devices", "Pakai Wi-Fi yang sama di kedua perangkat"))
                Step(2, tr("Start sharing on this phone", "Mulai berbagi di ponsel ini"))
                Step(3, tr("Scan the QR code from the other device", "Pindai kode QR dari perangkat lain"))
            }
            HeroButton(tr("Start sharing", "Mulai berbagi"), Icons.Default.PlayArrow, onStart, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LiveShare(shortUrl: String, fullUrl: String, accessPin: String?) {
    val qr = remember(fullUrl) { qrBitmap(fullUrl, 420) }
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val copiedText = tr("Copied", "Tersalin")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (qr != null) Surface(shape = RoundedCornerShape(12.dp), color = Color.White) { Image(qr.asImageBitmap(), "QR $shortUrl", Modifier.size(132.dp).padding(6.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(shortUrl.removePrefix("http://"), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (accessPin != null) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text("PIN  $accessPin", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(fullUrl)); copied = true }, Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (copied) copiedText else tr("Copy link", "Salin tautan"), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, fullUrl), null)) }, Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Share", "Bagikan"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.09f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Renders a QR code for [content] as an ARGB bitmap. Returns null if encoding fails. */
internal fun qrBitmap(content: String, size: Int): Bitmap? = try {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { if (matrix[it % size, it / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
} catch (_: Exception) {
    null
}

private fun fmtBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(java.util.Locale.US, n / 1024.0)
    n < 1024L * 1024 * 1024 -> "%.1f MB".format(java.util.Locale.US, n / 1048576.0)
    else -> "%.2f GB".format(java.util.Locale.US, n / 1073741824.0)
}

/**
 * Push to PC: paste the desktop receiver address, pick files, and push them in the background.
 * The service resumes after a drop and skips files the PC already has.
 */
@Composable
private fun PushCard(context: Context, store: LocalDropStore) {
    val mode = TransferMode.PUSH
    val push by PushService.state.collectAsState()
    var address by remember { mutableStateOf(store.settings.pushAddress) }
    var picked by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val clipboard = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked = it }
    LaunchedEffect(address) { if (address != store.settings.pushAddress) store.settings = store.settings.copy(pushAddress = address) }
    val valid = validReceiverAddress(address)

    CardShell {
        ModeTitle(mode)
        if (push.error.isNotEmpty()) Notice(Icons.Default.Warning, push.error, Warn)
        if (push.running) {
            val frac = if (push.bytesTotal > 0) (push.bytesDone.toFloat() / push.bytesTotal).coerceIn(0f, 1f) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("${push.filesDone} / ${push.filesTotal} " + tr("files", "file"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = mode.color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Text(fmtBytes(push.bytesDone) + (if (push.bytesTotal > 0) " / " + fmtBytes(push.bytesTotal) else "") + (if (push.currentFile.isNotEmpty()) "  ·  ${push.currentFile}" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HeroButton(tr("Stop sending", "Hentikan pengiriman"), Icons.Default.Stop, { PushService.stop(context) }, modifier = Modifier.fillMaxWidth(), danger = true)
        } else {
            OutlinedTextField(
                value = address, onValueChange = { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("PC receive link", "Tautan terima PC")) }, placeholder = { Text("http://192.168.1.8:42198?t=…") },
                trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.trim()?.let { address = it } }) { Icon(Icons.Default.ContentPaste, tr("Paste", "Tempel")) } },
                isError = address.isNotBlank() && !valid,
                supportingText = { Text(if (address.isNotBlank() && !valid) tr("Paste the full link including ?t= and its token.", "Tempel tautan lengkap termasuk ?t= dan tokennya.") else tr("Desktop → Receive → Start receiving → Copy link", "Desktop → Terima → Mulai menerima → Salin tautan")) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (picked.isEmpty()) tr("Choose files", "Pilih file") else tr("${picked.size} chosen", "${picked.size} dipilih"), maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
                HeroButton(tr("Send to PC", "Kirim ke PC"), Icons.Default.Send, { picked.firstOrNull()?.let { PushService.start(context, address.trim(), picked.map { it.toString() }) } }, valid && picked.isNotEmpty(), Modifier.weight(1f))
            }
        }
    }
}
