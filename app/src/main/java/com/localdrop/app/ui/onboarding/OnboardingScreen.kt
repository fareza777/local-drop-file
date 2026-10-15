package com.localdrop.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdrop.app.ui.tr

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(tr("Your files.\nA shorter journey.", "File Anda.\nPerjalanan lebih singkat."), tr("Share with\nany browser.", "Bagikan lewat\nbrowser mana pun."), tr("Phone to PC.\nOne clear route.", "Ponsel ke PC.\nSatu jalur jelas."), tr("Your network.\nYour control.", "Jaringan Anda.\nKendali Anda."))
    val descriptions = listOf(
        tr("Move files between Android and Windows without a cloud account.", "Pindahkan file antara Android dan Windows tanpa akun cloud."),
        tr("Start Browser Share. Open its private link on another device on the same network.", "Mulai Bagikan via Browser. Buka tautan privatnya di perangkat lain pada jaringan yang sama."),
        tr("Open Receive on Pushvia Desktop. Paste its link into Push to PC, choose files and send.", "Buka Terima di Pushvia Desktop. Tempel tautannya di Kirim ke PC, pilih file lalu kirim."),
        tr("Approve only devices you recognize. Use trusted Wi-Fi or a secure VPN: local HTTP is not encrypted.", "Setujui hanya perangkat yang Anda kenali. Gunakan Wi-Fi tepercaya atau VPN aman: HTTP lokal tidak terenkripsi.")
    )
    BackHandler(page > 0) { page-- }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { index -> Box(Modifier.weight(1f).height(4.dp).background(if (index <= page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) OutlinedButton(onClick = { page-- }, modifier = Modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(tr("Back", "Kembali")) }
                Button(onClick = { if (page == 3) onComplete() else page++ }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(if (page == 3) tr("Open Pushvia", "Buka Pushvia") else tr("Continue", "Lanjut"), fontWeight = FontWeight.SemiBold) }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pushvia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onComplete) { Text(tr("Skip guide", "Lewati")) }
            }
            AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "welcome page", modifier = Modifier.weight(1f)) { index ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compact = maxHeight < 480.dp
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)) {
                        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().height(if (compact) 128.dp else 210.dp)) {
                            Row(Modifier.padding(22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) { Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.padding(if (compact) 16.dp else 24.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary) }
                                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.padding(horizontal = 14.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) { Icon(if (index == 1) Icons.Default.Language else Icons.Default.Computer, null, modifier = Modifier.padding(if (compact) 16.dp else 24.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                        Text("0${index + 1} / " + tr("GETTING STARTED", "MULAI"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(titles[index], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(descriptions[index], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (index == 2) Text(tr("Same Wi-Fi for local transfers. A private VPN is optional for different networks.", "Wi-Fi yang sama untuk transfer lokal. VPN pribadi opsional untuk jaringan berbeda."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
