package com.localdrop.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localdrop.app.data.*
import com.localdrop.app.ui.isIndonesian
import com.localdrop.app.ui.tr
import java.text.SimpleDateFormat
import java.util.*

private val Ok = Color(0xFF138A68)
private val Bad = Color(0xFFE53935)
private val Busy = Color(0xFFFF8F00)

@Composable
fun HistoryScreen(store: LocalDropStore) {
    val transfers = remember { store.transfers() }
    val format = SimpleDateFormat("d MMM, HH:mm", if (isIndonesian()) Locale("id", "ID") else Locale.ENGLISH)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("History", "Riwayat"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (transfers.isNotEmpty()) Text(tr("${transfers.size} transfers", "${transfers.size} transfer"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (transfers.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.History, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(14.dp))
                Text(tr("No transfers yet", "Belum ada transfer"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tr("Finished transfers show up here.", "Transfer yang selesai muncul di sini."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(transfers, key = { it.id }) { TransferRow(it, format) }
            }
        }
    }
}

@Composable
private fun TransferRow(transfer: Transfer, format: SimpleDateFormat) {
    val send = transfer.direction == Direction.SEND
    val tint = if (send) MaterialTheme.colorScheme.primary else Ok
    val status = when (transfer.status) { Status.COMPLETE -> Ok to tr("Done", "Selesai"); Status.FAILED -> Bad to tr("Failed", "Gagal"); Status.IN_PROGRESS -> Busy to tr("Running", "Berjalan") }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(if (send) Icons.Default.Upload else Icons.Default.Download, null, Modifier.size(22.dp), tint = tint) }
            Column(Modifier.weight(1f)) {
                Text(transfer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${if (send) tr("Sent", "Dikirim") else tr("Received", "Diterima")} · ${size(transfer.sizeBytes)} · ${format.format(Date.from(transfer.timestamp))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = status.first.copy(alpha = 0.12f)) { Text(status.second, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = status.first) }
        }
    }
}

private fun size(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
