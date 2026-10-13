package com.localdrop.app.ui.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localdrop.app.TransferServerService
import com.localdrop.app.server.LocalHttpServer

/**
 * Shown on the phone when a desktop asks to pair. The user must approve here. The PIN shown
 * after approval is what the desktop asks for, which confirms both sides are looking at the
 * same request.
 */
@Composable
fun PairingDialog(request: LocalHttpServer.PairRequest) {
    var approved by remember(request.id) { mutableStateOf(request.approved) }

    AlertDialog(
        onDismissRequest = { /* require an explicit choice */ },
        icon = { Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(if (approved) "Enter this PIN on your computer" else "Pair with ${request.deviceName}?", textAlign = TextAlign.Center)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!approved) {
                    Text(
                        "A computer is asking to connect to this phone. Only approve if you started this pairing on that computer.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "Key: ${request.fingerprint.take(24)}…",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        request.pin.chunked(3).joinToString(" "),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "The PIN expires in a few minutes. Keep this screen open until the computer confirms.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!approved) {
                Button(onClick = {
                    TransferServerService.approvePairing(request.id)
                    approved = true
                }) { Text("Approve") }
            } else {
                Button(onClick = { TransferServerService.rejectPairing(request.id) }) { Text("Done") }
            }
        },
        dismissButton = {
            if (!approved) {
                TextButton(onClick = { TransferServerService.rejectPairing(request.id) }) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}
