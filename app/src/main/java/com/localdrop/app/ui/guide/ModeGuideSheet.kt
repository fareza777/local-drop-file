package com.localdrop.app.ui.guide

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdrop.app.ui.home.TransferMode
import com.localdrop.app.ui.home.needsText
import com.localdrop.app.ui.home.stepTexts
import com.localdrop.app.ui.home.title
import com.localdrop.app.ui.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeGuideSheet(mode: TransferMode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(mode.title(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(mode.needsText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("Steps", "Langkah"), tr("Network help", "Bantuan jaringan")).forEachIndexed { index, value -> FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(value) }) }
            }
            if (tab == 0) {
                mode.stepTexts().forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("${index + 1}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Text(tr("Same Wi-Fi? No VPN needed for Browser Share or Push to PC.", "Wi-Fi sama? Tidak perlu VPN untuk Browser maupun Kirim ke PC."), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(tr("Different networks? Choose ONE VPN below. Install it on Android and Windows, sign in to the same Tailscale account or join the same ZeroTier network, and allow both devices. Then use the VPN address of the phone or PC, not its Wi-Fi address.", "Beda jaringan? Pilih SATU VPN di bawah. Pasang di Android dan Windows, masuk dengan akun Tailscale yang sama atau gabung ke jaringan ZeroTier yang sama, lalu izinkan kedua perangkat. Gunakan alamat VPN ponsel atau PC, bukan alamat Wi-Fi-nya."), style = MaterialTheme.typography.bodyMedium)
                listOf(tr("Download Tailscale", "Unduh Tailscale") to "https://tailscale.com/download", tr("Download ZeroTier", "Unduh ZeroTier") to "https://www.zerotier.com/download/").forEach { (label, url) ->
                    OutlinedButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { error = url } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(label, modifier = Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                    }
                }
                Text(tr("Pushvia does not install or configure a VPN for you. VPN providers may use relays. Local HTTP is not encrypted without an encrypted network layer.", "Pushvia tidak memasang atau mengatur VPN untuk Anda. Penyedia VPN dapat memakai relay. HTTP lokal tidak terenkripsi tanpa lapisan jaringan terenkripsi."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text(tr("Got it", "Mengerti")) }
        }
    }
}
