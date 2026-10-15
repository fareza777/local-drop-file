package com.localdrop.app.ui.guide

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdrop.app.ui.home.TransferMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeGuideSheet(mode: TransferMode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Steps") }
    var error by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(mode.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(mode.needs, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Steps", "Network help").forEach { value -> FilterChip(selected = tab == value, onClick = { tab = value }, label = { Text(value) }) }
            }
            if (tab == "Steps") {
                mode.steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("${index+1}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Text("Same Wi-Fi? No VPN needed for Browser Share or Push to PC.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("Different networks? Choose ONE VPN below. Install it on Android and Windows, sign in to the same Tailscale account or join the same ZeroTier network, and allow both devices. Then use the phone or PC's VPN address, not its Wi-Fi address.", style = MaterialTheme.typography.bodyMedium)
                listOf("Download Tailscale" to "https://tailscale.com/download", "Download ZeroTier" to "https://www.zerotier.com/download/").forEach { (label, url) ->
                    OutlinedButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { error = "Open $url in a browser." } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(label, modifier = Modifier.weight(1f)); Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                    }
                }
                Text("LocalDrop does not install or configure a VPN for you. VPN providers may use relays. Local HTTP is not encrypted without an encrypted network layer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("Got it") }
        }
    }
}
