package com.localdrop.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdrop.app.MainActivity
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SplashScreen { startActivity(Intent(this, MainActivity::class.java)); finish() } }
    }
}
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "brand reveal")
    val scale by animateFloatAsState(if (visible) 1f else .94f, tween(550, easing = FastOutSlowInEasing), label = "brand scale")
    LaunchedEffect(Unit) { visible = true; delay(850); onFinish() }
    Box(Modifier.fillMaxSize().background(Color(0xFF11151F)).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val tint = Color(0xFFAFBFFF).copy(alpha = .07f)
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(tint, size.width * .39f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(tint, size.width * .61f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        Column(Modifier.alpha(alpha).scale(scale), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).background(Color(0xFF29375B), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Pushvia", tint = Color(0xFFDCE4FF), modifier = Modifier.size(54.dp))
            }
            Spacer(Modifier.height(26.dp))
            Text("Pushvia", color = Color(0xFFF2F4FC), fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-1.2).sp)
            Spacer(Modifier.height(8.dp))
            Text("File Transfer & Sync", color = Color(0xFFB9C2D8), fontSize = 16.sp)
        }
        Text("ANDROID + DESKTOP", color = Color(0xFF8B98B6), fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp))
    }
}
