package com.localdrop.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import com.localdrop.app.data.Direction
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.data.Status
import com.localdrop.app.data.Transfer
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Push to PC: streams files from this phone to the Pushvia Desktop receiver.
 *
 * Runs in the foreground so it keeps going while the app is backgrounded or the
 * screen is locked. Resume is server-driven: before each file we ask the receiver
 * how many bytes it already holds (/upload/init), skip files it already has, and
 * continue a partial file from that offset. On a dropped connection we back off and
 * re-query the offset, so an interrupted push picks up exactly where it stopped.
 */
class PushService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _state.value = _state.value.copy(running = false, error = "Stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: ""
        val uris = intent?.getStringArrayListExtra(EXTRA_URIS) ?: arrayListOf()
        if (address.isEmpty() || uris.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            notification("Preparing ${uris.size} files…")
        )

        cancelFlag = false
        Thread { runPush(address, uris) }.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelFlag = true
        super.onDestroy()
    }

    // ---------------------------------------------------------------- push loop

    private fun runPush(address: String, uris: List<String>) {
        val (base, token) = normalize(address)
        _state.value = PushState(running = true, address = base, filesTotal = uris.size)

        // Declare the batch up front so the desktop can show real totals.
        val items = uris.mapNotNull { resolve(it) }
        val bytesTotal = items.sumOf { it.size }
        _state.value = _state.value.copy(bytesTotal = bytesTotal)

        var filesDone = 0
        var bytesDone = 0L
        var failed = 0

        for ((index, item) in items.withIndex()) {
            if (cancelFlag) break
            _state.value = _state.value.copy(currentFile = item.name, filesDone = filesDone, bytesDone = bytesDone)
            updateNotification("Sending ${index + 1}/${items.size} · ${item.name}")

            val result = pushOne(base, token, item) { done ->
                if (!cancelFlag) {
                    _state.value = _state.value.copy(bytesDone = bytesDone + done, currentFile = item.name)
                }
            }
            when (result) {
                is PushResult.Ok -> {
                    filesDone++
                    bytesDone += item.size
                    _state.value = _state.value.copy(filesDone = filesDone, bytesDone = bytesDone)
                }
                is PushResult.Skipped -> {
                    // Receiver already has this file: count it as done and move on.
                    filesDone++
                    bytesDone += item.size
                    _state.value = _state.value.copy(filesDone = filesDone, bytesDone = bytesDone, skipped = _state.value.skipped + 1)
                }
                is PushResult.Failed -> {
                    failed++
                }
            }
        }

        val state = _state.value
        _state.value = state.copy(
            running = false,
            currentFile = "",
            error = if (failed > 0) "$failed file(s) failed" else ""
        )
        // One History row for the whole push — a row per file would flood it
        // (a photo folder can be a thousand files).
        if (items.isNotEmpty()) {
            try {
                val label = if (items.size == 1) items.first().name else "Push to PC · ${items.size} files"
                val incomplete = failed > 0 || cancelFlag
                LocalDropStore(applicationContext).addTransfer(
                    Transfer(
                        id = java.util.UUID.randomUUID().toString(),
                        name = label,
                        sizeBytes = bytesDone,
                        direction = Direction.SEND,
                        timestamp = java.time.Instant.now(),
                        status = if (incomplete) Status.FAILED else Status.COMPLETE
                    )
                )
            } catch (_: Exception) {
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Push a single file with resume + bounded retries for connection drops. */
    private fun pushOne(base: String, token: String, item: Item, onProgress: (Long) -> Unit): PushResult {
        val sha = sha256Of(item) ?: return PushResult.Failed
        var attempt = 0

        while (attempt < 6 && !cancelFlag) {
            try {
                // Ask the receiver where to (re)start: skip, resume, or fresh.
                val init = postJson(base, "/upload/init", token, JSONObject()
                    .put("file_id", item.fileId)
                    .put("size", item.size)
                    .put("sha256", sha))
                if (init == null) {
                    attempt++; backoff(attempt); continue
                }
                if (init.optBoolean("skip", false) ||
                    (init.optBoolean("complete", false) && init.optLong("received", 0) >= item.size)
                ) {
                    onProgress(item.size)
                    return PushResult.Skipped
                }
                // Stream from the server-reported offset until done or the link drops.
                var offset = init.optLong("received", 0).coerceIn(0L, item.size)
                onProgress(offset)

                var dropped = false
                while (offset < item.size && !cancelFlag) {
                    val chunk = readChunk(item, offset) ?: return PushResult.Failed
                    if (chunk.isEmpty()) break // shorter than declared size
                    val resp = postChunk(base, token, item, offset, sha, chunk)
                    if (resp == null) {
                        dropped = true // connection lost: back out, re-query offset, resume
                        break
                    }
                    if (resp.has("error")) {
                        if (resp.optBoolean("restart", false)) {
                            offset = 0; onProgress(0); continue
                        }
                        return PushResult.Failed
                    }
                    offset = resp.optLong("received", offset + chunk.size)
                    onProgress(offset.coerceAtMost(item.size))
                    if (resp.optBoolean("complete", false)) return PushResult.Ok
                }

                if (cancelFlag) return PushResult.Failed
                if (offset >= item.size) return PushResult.Ok
                if (!dropped) return PushResult.Ok // ran out of bytes (short file)
                // Dropped mid-file: fall through to re-init and resume from the new offset.
                attempt++; backoff(attempt)
            } catch (e: Exception) {
                attempt++; backoff(attempt)
            }
        }
        return PushResult.Failed
    }

    // ------------------------------------------------------------------ plumbing

    private data class Item(val uri: Uri, val fileId: String, val name: String, val size: Long)

    private fun resolve(uriStr: String): Item? = try {
        val uri = Uri.parse(uriStr)
        var name = "file"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        if (size <= 0) size = contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        if (name.isBlank()) name = "file"
        Item(uri, sanitize(name), name, size)
    } catch (_: Exception) {
        null
    }

    /** Receiver rejects ':' and control chars and refuses '..' segments. */
    private fun sanitize(name: String): String = name
        .replace('/', '_').replace('\\', '_').replace(':', '_')
        .replace("..", "_")
        .filter { !it.isISOControl() }
        .ifBlank { "file" }

    private fun sha256Of(item: Item): String? = try {
        val d = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        val stream = contentResolver.openInputStream(item.uri)
        if (stream == null) {
            null
        } else {
            stream.use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    d.update(buf, 0, n)
                }
            }
            d.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) {
        null
    }

    private fun readChunk(item: Item, offset: Long): ByteArray? = try {
        contentResolver.openInputStream(item.uri)?.use { s ->
            skipFully(s, offset)
            val remaining = (item.size - offset).toInt().coerceAtLeast(0)
            val want = minOf(CHUNK, remaining)
            val out = ByteArray(want)
            var got = 0
            while (got < want) {
                val n = s.read(out, got, want - got)
                if (n < 0) break
                got += n
            }
            if (got == 0) ByteArray(0) else out.copyOf(got)
        }
    } catch (_: Exception) {
        null
    }

    private fun skipFully(s: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val skipped = s.skip(left)
            if (skipped > 0) left -= skipped else {
                if (s.read() < 0) break
                left--
            }
        }
    }

    // --------------------------------------------------------------- HTTP layer

    private fun normalize(address: String): Pair<String, String> {
        var a = address.trim()
        var token = ""
        val qIndex = a.indexOf('?')
        if (qIndex >= 0) {
            val query = a.substring(qIndex + 1)
            a = a.substring(0, qIndex)
            for (pair in query.split('&')) {
                val kv = pair.split('=', limit = 2)
                if (kv.size == 2 && (kv[0] == "t" || kv[0] == "token")) token = kv[1]
            }
        }
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
        return a.trimEnd('/') to token
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 30000
        c.requestMethod = method
        if (token.isNotEmpty()) c.setRequestProperty("x-token", token)
        return c
    }

    private fun postJson(base: String, path: String, token: String, body: JSONObject): JSONObject? = try {
        val c = open("$base$path", "POST", token)
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) null else readJson(c)
    } catch (_: Exception) {
        null
    }

    private fun postChunk(base: String, token: String, item: Item, offset: Long, sha: String, data: ByteArray): JSONObject? = try {
        val fid = java.net.URLEncoder.encode(item.fileId, "UTF-8")
        val c = open("$base/upload/chunk?file_id=$fid&offset=$offset", "POST", token)
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.setRequestProperty("x-file-size", item.size.toString())
        c.setRequestProperty("x-sha256", sha)
        c.setFixedLengthStreamingMode(data.size)
        c.outputStream.use { it.write(data) }
        if (c.responseCode !in 200..299) null else readJson(c)
    } catch (_: Exception) {
        null
    }

    private fun readJson(c: HttpURLConnection): JSONObject? = try {
        BufferedReader(InputStreamReader(c.inputStream)).use { JSONObject(it.readText()) }
    } catch (_: Exception) {
        null
    } finally {
        c.disconnect()
    }

    private fun backoff(attempt: Int) {
        val ms = (500L * attempt).coerceAtMost(8000L)
        var waited = 0L
        while (waited < ms && !cancelFlag) {
            Thread.sleep(100)
            waited += 100
        }
    }

    // ------------------------------------------------------- notification + state

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Push to PC", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_localdrop)
            .setContentTitle("Pushing files to your PC")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private sealed class PushResult {
        object Ok : PushResult()
        object Skipped : PushResult()
        object Failed : PushResult()
    }

    companion object {
        private const val CHUNK = 1024 * 1024
        private const val CHANNEL = "push-to-pc"
        private const val NOTIFICATION_ID = 4242
        private const val ACTION_STOP = "com.localdrop.app.PUSH_STOP"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_URIS = "uris"

        @Volatile private var cancelFlag = false

        private val _state = MutableStateFlow(PushState())
        val state: StateFlow<PushState> = _state.asStateFlow()

        fun start(context: Context, address: String, uris: List<String>) {
            _state.value = PushState(running = true, address = address, filesTotal = uris.size)
            val i = Intent(context, PushService::class.java)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_URIS, ArrayList(uris))
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                _state.value = PushState()
                throw e
            }
        }

        fun stop(context: Context) {
            cancelFlag = true
            val i = Intent(context, PushService::class.java).setAction(ACTION_STOP)
            context.startService(i)
            _state.value = _state.value.copy(running = false, error = "Stopped")
        }
    }
}

/** Snapshot of an active push, observed by the UI. */
data class PushState(
    val running: Boolean = false,
    val address: String = "",
    val filesTotal: Int = 0,
    val filesDone: Int = 0,
    val skipped: Int = 0,
    val bytesTotal: Long = 0,
    val bytesDone: Long = 0,
    val currentFile: String = "",
    val error: String = ""
)
