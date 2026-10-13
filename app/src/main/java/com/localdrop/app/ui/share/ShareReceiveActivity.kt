package com.localdrop.app.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdrop.app.PushService
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.ui.theme.LocalDropTheme

/**
 * Share Sheet target: "Share → LocalDrop" from any app lands here with
 * ACTION_SEND / ACTION_SEND_MULTIPLE. Pushes straight to the paired PC using the
 * saved receiver address — no need to open LocalDrop or pick a destination.
 */
class ShareReceiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalDropStore(this)
        val address = store.settings.pushAddress.trim()

        if (address.isEmpty()) {
            // No receiver configured: send the user to Push mode to paste the address once.
            val i = Intent(this, com.localdrop.app.MainActivity::class.java).apply {
                putExtra(EXTRA_GO_PUSH, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
            Toast.makeText(this, "Set your PC receiver address first, then share again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "Nothing to send.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Kick off the foreground push immediately; the card below mirrors PushService state.
        PushService.start(applicationContext, address, uris)

        setContent {
            LocalDropTheme { ShareResultCard(onDone = { finish() }) }
        }
    }

    private fun extractUris(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else
                        @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                ).map { it.toString() }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (android.os.Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list.orEmpty().map { it.toString() }
            }
            else -> emptyList()
        }
    }

    companion object {
        const val EXTRA_GO_PUSH = "go_push"
    }
}

@Composable
private fun ShareResultCard(onDone: () -> Unit) {
    val state by PushService.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                state.running -> {
                    Text("Sending ${state.filesDone}/${state.filesTotal} files", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (state.currentFile.isNotEmpty()) {
                        Text(state.currentFile, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                state.error.isNotEmpty() -> {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Push complete", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone, enabled = !state.running) {
                Text(if (state.running) "Pushing…" else "Done")
            }
        }
    }
}
