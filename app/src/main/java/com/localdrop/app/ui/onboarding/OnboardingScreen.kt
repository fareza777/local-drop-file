package com.localdrop.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localdrop.app.ui.home.TransferMode

/**
 * First-run guide. Page order: welcome, then one page per transfer mode, then done.
 * Each mode page reuses [TransferMode] so the steps match the in-app guide exactly.
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember { buildPages() }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[index]
    val isLast = index == pages.lastIndex

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepDots(total = pages.size, current = index, color = page.color)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        if (index > 0) {
                            OutlinedButton(
                                onClick = { index-- },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Back") }
                        }
                        Button(
                            onClick = { if (isLast) onComplete() else index++ },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = page.color)
                        ) {
                            Text(
                                if (isLast) "Start using LocalDrop" else "Next",
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!isLast) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (!isLast) {
                        TextButton(onClick = onComplete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Skip guide", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding_page",
            modifier = Modifier.padding(padding).fillMaxSize()
        ) { i ->
            val p = pages[i]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.size(112.dp).clip(CircleShape).background(p.color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(p.icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = p.color)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.kicker.isNotEmpty()) {
                        Text(
                            p.kicker.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = p.color
                        )
                    }
                    Text(
                        p.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        p.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                p.bullets.forEach { BulletCard(it, p.color) }

                if (p.steps.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("How it works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            p.steps.forEachIndexed { n, s -> NumberedStep(n + 1, s, p.color) }
                        }
                    }
                }

                if (p.note.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = p.color.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = p.color, modifier = Modifier.size(18.dp))
                            Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private data class GuidePage(
    val kicker: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val bullets: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val note: String = ""
)

private fun buildPages(): List<GuidePage> {
    val welcome = GuidePage(
        kicker = "Welcome",
        title = "Move files without the cloud",
        subtitle = "LocalDrop sends files straight between your devices on your own network.",
        icon = Icons.Default.Shield,
        color = Color(0xFF5054C9),
        bullets = listOf(
            "No cloud, no accounts, no servers",
            "Files move directly between your devices, end-to-end encrypted",
            "Choose a mode below. You can change it any time."
        )
    )
    val modes = TransferMode.entries.map { m ->
        GuidePage(
            kicker = m.displayName,
            title = m.tagline,
            subtitle = "Requires: ${m.needs}",
            icon = m.icon,
            color = m.color,
            steps = m.steps,
            note = m.tips.first()
        )
    }
    val done = GuidePage(
        kicker = "All set",
        title = "Pick a mode and start",
        subtitle = "You can reopen this guide from Settings or from the How to use button on each mode.",
        icon = Icons.Default.CheckCircle,
        color = Color(0xFF138A68),
        bullets = listOf(
            "Browser Share is ready to use now",
            "Push to PC and Remote Private need Tailscale or ZeroTier first"
        )
    )
    return listOf(welcome) + modes + listOf(done)
}

@Composable
private fun BulletCard(text: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 2.dp))
    }
}

@Composable
private fun StepDots(total: Int, current: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == current) 22.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (i == current) color
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
            )
        }
    }
}
