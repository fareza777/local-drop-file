package com.localdrop.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdrop.app.ui.home.TransferMode

/**
 * Per-mode guide shown from Home and Settings. Uses the same [TransferMode] data as onboarding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeGuideSheet(mode: TransferMode, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(mode.color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(mode.icon, contentDescription = null, tint = mode.color)
                }
                Column {
                    Text(mode.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (mode.isLive) "Ready to use" else "Setup required",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mode.isLive) Color(0xFF138A68) else Color(0xFFE0703A),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(mode.description, style = MaterialTheme.typography.bodyMedium)

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What you need", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(mode.needs, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            mode.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(mode.color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 2.dp))
                }
            }

            Text("Good to know", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            mode.tips.forEach { tip ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = mode.color, modifier = Modifier.size(18.dp))
                    Text(tip, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mode.color)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Got it", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
