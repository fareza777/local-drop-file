package com.localdrop.app.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localdrop.app.data.*
import com.localdrop.app.ui.isIndonesian
import com.localdrop.app.ui.tr
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DevicesScreen(store: LocalDropStore) {
    var devices by remember { mutableStateOf(store.trustedDevices()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(top = 8.dp, bottom = 10.dp)) {
            Text(tr("Trusted devices", "Perangkat tepercaya"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(tr("Computers allowed to open this phone. Remove any you do not recognize.", "Komputer yang boleh mengakses ponsel ini. Hapus yang tidak Anda kenali."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (devices.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Devices, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(14.dp))
                Text(tr("No paired computers", "Belum ada komputer terpasang"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tr("Start Browser Share, then pair from Pushvia Desktop.", "Mulai Browser, lalu pasangkan dari Pushvia Desktop."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(devices, key = { it.id }) { DeviceCard(it, store) { devices = store.trustedDevices() } }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: TrustedDevice, store: LocalDropStore, onRemoved: () -> Unit) {
    val indonesian = isIndonesian()
    val format = remember(indonesian) { SimpleDateFormat("d MMM yyyy, HH:mm", if (indonesian) Locale("id", "ID") else Locale.ENGLISH) }
    var expanded by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { expanded = !expanded }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Computer, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(13.dp), tint = Color(0xFF138A68))
                        Text(tr("Trusted · ${format.format(Date.from(device.lastSeen))}", "Tepercaya · ${format.format(Date.from(device.lastSeen))}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Default.DeleteOutline, tr("Remove", "Hapus"), tint = MaterialTheme.colorScheme.error) }
            }
            if (expanded) Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(tr("ID (not an encryption key): ", "ID (bukan kunci enkripsi): ") + device.publicKeyFingerprint, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFFF8F00)) },
        title = { Text(tr("Remove device?", "Hapus perangkat?")) },
        text = { Text(tr("${device.name} cannot connect again until you pair it again.", "${device.name} tidak dapat terhubung lagi sampai dipasangkan ulang.")) },
        confirmButton = { TextButton(onClick = { store.saveTrustedDevices(store.trustedDevices().filter { it.id != device.id }); onRemoved(); confirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(tr("Remove", "Hapus")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Cancel", "Batal")) } }
    )
}
