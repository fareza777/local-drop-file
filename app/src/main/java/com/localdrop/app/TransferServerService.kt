package com.localdrop.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localdrop.app.data.Direction
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.data.Status
import com.localdrop.app.data.Transfer
import com.localdrop.app.data.TrustedDevice
import com.localdrop.app.server.LocalHttpServer
import com.localdrop.app.server.SessionToken
import com.localdrop.app.server.TrustedDeviceStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs the Browser Share / Direct server in the foreground so it keeps working
 * while the app is in the background. UI observes [state] and calls [start]/[stop].
 */
class TransferServerService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val mode = intent?.getStringExtra(EXTRA_MODE) ?: "Browser Share"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Active transfers", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_localdrop)
            .setContentTitle("Pushvia is active")
            .setContentText("$mode is running")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        val wifiOnly = getSharedPreferences("localdrop", Context.MODE_PRIVATE).getBoolean("wifi_only", true)
        if (wifiOnly && !hasLocalNetwork(this)) {
            _state.value = ServerState(false, "", "", error = "No local network. Connect to Wi-Fi, or turn off Wi-Fi only in Settings.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val url = startServer(this, autoStopMinutes(this))
        if (url.isEmpty()) {
            _state.value = ServerState(false, "", "", error = "Could not start the server. Try again.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    /** Snapshot of the running server. */
    data class ServerState(val running: Boolean = false, val url: String = "", val token: String = "", val error: String = "")

    companion object {
        const val EXTRA_MODE = "mode"
        const val ACTION_STOP = "com.localdrop.app.action.STOP"
        private const val CHANNEL = "localdrop_transfer"
        private const val NOTIFICATION_ID = 4412

        private val _state = MutableStateFlow(ServerState())
        val state: StateFlow<ServerState> = _state.asStateFlow()

        @Volatile private var instance: LocalHttpServer? = null
        private var watchdog: java.util.Timer? = null
        private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

        /** PIN shown on the phone for Browser Share when PIN protection is on; null otherwise. */
        private val _accessPin = MutableStateFlow<String?>(null)
        val accessPin: StateFlow<String?> = _accessPin.asStateFlow()

        /**
         * Whether the phone has a usable local network: Wi-Fi, Ethernet, or a VPN (Tailscale and
         * ZeroTier run as VPN transports, so Remote mode needs this to pass). Mobile data alone fails.
         */
        fun hasLocalNetwork(context: Context): Boolean {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
            // Check every active network, not just the default one, because a VPN can be the default.
            return cm.allNetworks.any { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@any false
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
            }
        }

        /** Pairing requests waiting for the phone user to approve or reject. */
        private val _pairing = MutableStateFlow<List<LocalHttpServer.PairRequest>>(emptyList())
        val pairing: StateFlow<List<LocalHttpServer.PairRequest>> = _pairing.asStateFlow()

        /** Called by the server when a request arrives. Keeps the list in sync for the UI. */
        private fun refreshPairing() {
            _pairing.value = instance?.pendingRequests().orEmpty()
        }

        /** Phone user approves a request from the pairing dialog. */
        fun approvePairing(requestId: String): Boolean {
            val ok = instance?.approvePairing(requestId) ?: false
            refreshPairing()
            return ok
        }

        /** Phone user rejects or closes a request. */
        fun rejectPairing(requestId: String) {
            instance?.rejectPairing(requestId)
            refreshPairing()
        }

        /** Starts the server if needed and returns its base URL, or empty on failure. */
        @Synchronized
        fun startServer(context: Context, autoStopMinutes: Int = 15): String {
            instance?.takeIf { it.isRunning() }?.let { return it.origin }
            val token = SessionToken.issue(ttlSeconds = 3600)
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("localdrop", Context.MODE_PRIVATE)
            val pinOn = prefs.getBoolean("pin_protection", false)
            val accessPin = if (pinOn) (100000 + java.security.SecureRandom().nextInt(900000)).toString() else null
            _accessPin.value = accessPin
            val store = LocalDropStore(app)
            val server = LocalHttpServer(
                root = dropFolder(),
                token = token,
                trusted = object : TrustedDeviceStore {
                    override fun put(deviceId: String, deviceName: String, fingerprint: String) {
                        val now = java.time.Instant.now()
                        val list = store.trustedDevices().filterNot { it.id == deviceId } +
                            TrustedDevice(deviceId, deviceName, fingerprint, now)
                        store.saveTrustedDevices(list)
                    }
                    override fun contains(deviceId: String, fingerprint: String): Boolean =
                        store.trustedDevices().any { it.id == deviceId && it.publicKeyFingerprint == fingerprint }
                },
                pageProvider = { app.assets.open("web/index.html").bufferedReader().use { it.readText() } },
                accessPin = accessPin
            )
            server.onPairRequest = { _pairing.value = server.pendingRequests() }
            server.onPairingChanged = { _pairing.value = server.pendingRequests() }
            // Every completed transfer gets a History row (browser uploads, desktop
            // pushes, downloads and ZIP exports alike).
            server.onTransfer = { name, size, outgoing ->
                store.addTransfer(
                    Transfer(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        sizeBytes = size,
                        direction = if (outgoing) Direction.SEND else Direction.RECEIVE,
                        status = Status.COMPLETE
                    )
                )
            }
            return try {
                val url = server.start()
                instance = server
                // Many phones drop broadcast packets unless a multicast lock is held; this lets the desktop find the phone.
                multicastLock = runCatching {
                    (app.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                        .createMulticastLock("localdrop-discovery").apply { setReferenceCounted(false); acquire() }
                }.getOrNull()
                // Browsers need the token in the URL; the page reads it from ?token= and sends it on each request.
                // "url" is the short address shown on screen; the token travels in the copy/QR link.
                _state.value = ServerState(true, url, token.value)
                armWatchdog(autoStopMinutes)
                url
            } catch (_: Exception) {
                _state.value = ServerState()
                ""
            }
        }

        /** Stops the server automatically after [minutes] without any request. */
        private fun armWatchdog(minutes: Int) {
            watchdog?.cancel()
            val timer = java.util.Timer("localdrop-autostop", true)
            val limitMs = minutes.coerceIn(1, 120) * 60_000L
            timer.scheduleAtFixedRate(object : java.util.TimerTask() {
                override fun run() {
                    val s = instance
                    if (s == null) {
                        cancel()
                        return
                    }
                    if (System.currentTimeMillis() - s.lastActivity >= limitMs) {
                        stopServer()
                        cancel()
                    }
                }
            }, 30_000L, 30_000L)
            watchdog = timer
        }

        @Synchronized
        fun stopServer() {
            watchdog?.cancel()
            watchdog = null
            instance?.stop()
            instance = null
            runCatching { multicastLock?.release() }
            multicastLock = null
            _state.value = ServerState()
        }

        /**
         * Browser root is the phone's internal storage, so every folder is visible and manageable.
         */
        fun dropFolder(): File = android.os.Environment.getExternalStorageDirectory()

        /** Reads the auto-stop value the user saved in Settings. */
        fun autoStopMinutes(context: Context): Int =
            context.getSharedPreferences("localdrop", Context.MODE_PRIVATE).getInt("autostop", 15)

        fun start(context: Context, mode: String) {
            val intent = Intent(context, TransferServerService::class.java).putExtra(EXTRA_MODE, mode)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TransferServerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
