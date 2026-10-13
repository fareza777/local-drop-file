package com.localdrop.app.ui.home

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.localdrop.app.PushService
import com.localdrop.app.TransferServerService
import com.localdrop.app.data.LocalDropStore

@Composable
fun HomeScreen(
    context: Context,
    store: LocalDropStore,
    onShowGuide: (TransferMode) -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(TransferMode.entries.firstOrNull { it.name == store.settings.lastMode } ?: TransferMode.BROWSER) }
    val serverState by TransferServerService.state.collectAsState()
    val accessPin by TransferServerService.accessPin.collectAsState()

    LaunchedEffect(selectedMode) {
        store.settings = store.settings.copy(lastMode = selectedMode.name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderSection(serverState.running)

        ModeSelector(selectedMode) { selectedMode = it }

        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "mode_content"
        ) { mode ->
            if (mode == TransferMode.PUSH) {
                PushCard(
                    context = context,
                    store = store,
                    onShowGuide = { onShowGuide(mode) }
                )
            } else {
                ModeCard(
                    mode = mode,
                    isRunning = serverState.running,
                    serverUrl = serverState.url,
                    serverToken = serverState.token,
                    accessPin = accessPin,
                    error = serverState.error,
                    onStart = { TransferServerService.start(context, mode.displayName) },
                    onStop = { TransferServerService.stop(context) },
                    onShowGuide = { onShowGuide(mode) }
                )
            }
        }

        if (!serverState.running) SecurityBanner(onOpenSettings)
    }
}

@Composable
private fun HeaderSection(running: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Transfer Files",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Pick a mode, then start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusBadge(running)
    }
}

@Composable
private fun StatusBadge(running: Boolean) {
    val color = if (running) Color(0xFF138A68) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Text(
                text = if (running) "Live" else "Idle",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun ModeSelector(selectedMode: TransferMode, onModeSelected: (TransferMode) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TransferMode.entries.forEach { mode ->
                ModeTab(
                    mode = mode,
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModeTab(
    mode: TransferMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val content = if (selected) mode.color else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        color = bg,
        shadowElevation = if (selected) 2.dp else 0.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(mode.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = content)
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ModeCard(
    mode: TransferMode,
    isRunning: Boolean,
    serverUrl: String,
    serverToken: String,
    accessPin: String?,
    error: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowGuide: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(mode.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(mode.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = mode.color)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(mode.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${mode.tagline} · ${mode.needs}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (mode.isLive) {
                if (error.isNotEmpty()) {
                    InfoRow(Icons.Default.Warning, error, Color(0xFFE0703A))
                }
                if (isRunning && serverUrl.isNotEmpty()) {
                    ServerInfo(serverUrl, "$serverUrl/?token=$serverToken", accessPin)
                }
                Button(
                    onClick = if (isRunning) onStop else onStart,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else mode.color
                    )
                ) {
                    Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRunning) "Stop" else "Start ${mode.displayName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                InfoRow(Icons.Default.Construction, "Coming soon in this build. Follow the guide below to set it up.", Color(0xFFE0703A))
            }

            OutlinedButton(
                onClick = onShowGuide,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("How to use ${mode.displayName}")
            }
        }
    }
}

@Composable
private fun ServerInfo(shortUrl: String, fullUrl: String, accessPin: String?) {
    val qr = remember(fullUrl) { qrBitmap(fullUrl, 360) }
    val clipboard = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (qr != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "QR code for $shortUrl",
                        modifier = Modifier.size(128.dp).padding(8.dp)
                    )
                }
            }
            Text(shortUrl, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            if (accessPin != null) {
                Text("PIN $accessPin", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(fullUrl)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy link", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SecurityBanner(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(modifier = Modifier.weight(1f)) {
                Text("No cloud. No accounts. No servers.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Files move directly between your devices, end-to-end encrypted. Tap to review security settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Renders a QR code for [content] as an ARGB bitmap. Returns null if encoding fails. */
internal fun qrBitmap(content: String, size: Int): Bitmap? = try {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bmp
} catch (_: Exception) {
    null
}

private fun fmtBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    n < 1024L * 1024 * 1024 -> "%.1f MB".format(n / 1048576.0)
    else -> "%.2f GB".format(n / 1073741824.0)
}

/**
 * Push to PC: paste the desktop receiver address, pick hundreds or thousands of
 * files, and push them in the background. The service resumes after a drop and
 * skips files the PC already has.
 */
@Composable
private fun PushCard(context: Context, store: LocalDropStore, onShowGuide: () -> Unit) {
    val mode = TransferMode.PUSH
    val push by PushService.state.collectAsState()
    var address by remember { mutableStateOf(store.settings.pushAddress) }
    var picked by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris
    }
    LaunchedEffect(address) {
        if (address != store.settings.pushAddress) store.settings = store.settings.copy(pushAddress = address)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(mode.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(mode.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = mode.color)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(mode.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(mode.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (push.error.isNotEmpty()) {
                InfoRow(Icons.Default.Warning, push.error, Color(0xFFE0703A))
            }

            if (push.running) {
                val frac = if (push.bytesTotal > 0) push.bytesDone.toFloat() / push.bytesTotal else 0f
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = mode.color
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${push.filesDone} / ${push.filesTotal} files", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(fmtBytes(push.bytesDone) + (if (push.bytesTotal > 0) " / " + fmtBytes(push.bytesTotal) else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (push.currentFile.isNotEmpty()) {
                    Text("Sending: ${push.currentFile}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Button(
                    onClick = { PushService.stop(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop push", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("PC receiver address") },
                    placeholder = { Text("http://100.x.y.z:42198?t=…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Start “Receive” on LocalDrop Desktop, then copy its address here.") }
                )

                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (picked.isEmpty()) "Choose photos & files" else "${picked.size} selected — change", fontSize = 15.sp)
                }

                Button(
                    onClick = {
                        picked.firstOrNull()?.let { PushService.start(context, address.trim(), picked.map { it.toString() }) }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = address.isNotBlank() && picked.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = mode.color)
                ) {
                    Icon(mode.icon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Push to PC", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedButton(
                onClick = onShowGuide,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("How to use ${mode.displayName}")
            }
        }
    }
}
