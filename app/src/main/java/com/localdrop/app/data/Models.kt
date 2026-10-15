package com.localdrop.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Transfer(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val direction: Direction,
    val timestamp: Instant = Instant.now(),
    val status: Status = Status.COMPLETE
)

enum class Direction { SEND, RECEIVE }
enum class Status { COMPLETE, FAILED, IN_PROGRESS }

data class TrustedDevice(
    val id: String,
    val name: String,
    val publicKeyFingerprint: String,
    val lastSeen: Instant
)

/**
 * User preferences. Only settings that are actually enforced by the app are
 * exposed here. Anything not yet implemented is shown as "coming soon" in the UI.
 */
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val autoStopMinutes: Int = 15,
    val theme: String = "Light",
    val pinProtection: Boolean = false,
    val wifiOnly: Boolean = true,
    val keepScreenOn: Boolean = false,
    val lastMode: String = "BROWSER",
    /** Receiver address pasted from Pushvia Desktop for Push to PC. */
    val pushAddress: String = "",
    /** Auto Backup Kamera: periodically push new camera photos/videos to the PC. */
    val autoBackupEnabled: Boolean = false,
    /** "System", "English" or "Indonesia". */
    val language: String = "English"
)

class LocalDropStore(context: Context) {
    private val prefs = context.getSharedPreferences("localdrop", Context.MODE_PRIVATE)

    var settings: AppSettings
        get() = AppSettings(
            onboardingComplete = prefs.getBoolean("onboarded", false),
            autoStopMinutes = prefs.getInt("autostop", 15),
            theme = prefs.getString("theme", "Light") ?: "Light",
            pinProtection = prefs.getBoolean("pin_protection", false),
            wifiOnly = prefs.getBoolean("wifi_only", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", false),
            lastMode = prefs.getString("last_mode", "BROWSER") ?: "BROWSER",
            pushAddress = prefs.getString("push_address", "") ?: "",
            autoBackupEnabled = prefs.getBoolean("auto_backup", false),
            language = prefs.getString("app_language", "English") ?: "English"
        )
        set(v) {
            prefs.edit()
                .putBoolean("onboarded", v.onboardingComplete)
                .putInt("autostop", v.autoStopMinutes.coerceIn(1, 120))
                .putString("theme", v.theme)
                .putBoolean("pin_protection", v.pinProtection)
                .putBoolean("wifi_only", v.wifiOnly)
                .putBoolean("keep_screen_on", v.keepScreenOn)
                .putString("last_mode", v.lastMode)
                .putString("push_address", v.pushAddress)
                .putBoolean("auto_backup", v.autoBackupEnabled)
                .putString("app_language", v.language)
                .apply()
        }

    fun addTransfer(t: Transfer) {
        val a = JSONArray(prefs.getString("history", "[]"))
        a.put(
            JSONObject()
                .put("id", t.id)
                .put("name", t.name)
                .put("size", t.sizeBytes)
                .put("direction", t.direction.name)
                .put("timestamp", t.timestamp.toString())
                .put("status", t.status.name)
        )
        while (a.length() > 100) a.remove(0)
        prefs.edit().putString("history", a.toString()).apply()
    }

    fun transfers(): List<Transfer> {
        val a = JSONArray(prefs.getString("history", "[]"))
        return (0 until a.length()).mapNotNull {
            try {
                val o = a.getJSONObject(it)
                Transfer(
                    o.getString("id"),
                    o.getString("name"),
                    o.getLong("size"),
                    Direction.valueOf(o.getString("direction")),
                    Instant.parse(o.getString("timestamp")),
                    Status.valueOf(o.getString("status"))
                )
            } catch (_: Exception) {
                null
            }
        }.reversed()
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }

    fun trustedDevices(): List<TrustedDevice> {
        val a = JSONArray(prefs.getString("trusted", "[]"))
        return (0 until a.length()).mapNotNull {
            try {
                val o = a.getJSONObject(it)
                TrustedDevice(
                    o.getString("id"),
                    o.getString("name"),
                    o.getString("fingerprint"),
                    Instant.parse(o.getString("seen"))
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun saveTrustedDevices(devices: List<TrustedDevice>) {
        val a = JSONArray()
        devices.forEach {
            a.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("fingerprint", it.publicKeyFingerprint)
                    .put("seen", it.lastSeen.toString())
            )
        }
        prefs.edit().putString("trusted", a.toString()).apply()
    }
}
