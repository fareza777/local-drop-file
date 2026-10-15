package com.localdrop.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.localdrop.app.ui.isIndonesian
import com.localdrop.app.ui.tr
import androidx.compose.ui.graphics.vector.ImageVector

@Composable fun TransferMode.label() = when (this) {
    TransferMode.BROWSER -> tr("Browser", "Browser")
    TransferMode.PUSH -> tr("Send to PC", "Kirim ke PC")
    TransferMode.REMOTE -> tr("Remote", "Jarak jauh")
}
@Composable fun TransferMode.title() = when (this) {
    TransferMode.BROWSER -> tr("Browser Share", "Bagikan via Browser")
    TransferMode.PUSH -> tr("Push to PC", "Kirim ke PC")
    TransferMode.REMOTE -> tr("Remote Private", "Jarak Jauh (VPN)")
}
@Composable fun TransferMode.subtitle() = when (this) {
    TransferMode.BROWSER -> tr("Any computer with a browser, same Wi-Fi", "Komputer mana pun dengan browser, Wi-Fi sama")
    TransferMode.PUSH -> tr("Pick files, send them to Pushvia Desktop", "Pilih file, kirim ke Pushvia Desktop")
    TransferMode.REMOTE -> tr("Reach this phone over Tailscale or ZeroTier", "Akses ponsel ini lewat Tailscale atau ZeroTier")
}
@Composable fun TransferMode.needsText() = when (this) {
    TransferMode.BROWSER -> tr("Same Wi-Fi as the other device", "Wi-Fi yang sama dengan perangkat lain")
    TransferMode.PUSH -> tr("Same Wi-Fi, or a private VPN", "Wi-Fi yang sama, atau VPN pribadi")
    TransferMode.REMOTE -> tr("Tailscale or ZeroTier on both devices", "Tailscale atau ZeroTier di kedua perangkat")
}
@Composable fun TransferMode.stepTexts(): List<String> = when (this) {
    TransferMode.BROWSER -> if (isIndonesian()) listOf(
        "Sambungkan kedua perangkat ke Wi-Fi yang sama.",
        "Ketuk Mulai berbagi.",
        "Pindai kode QR di perangkat lain, atau salin tautan privat lengkap beserta tokennya.",
        "Unduh file dari halaman web, atau unggah file ke ponsel ini."
    ) else steps
    TransferMode.PUSH -> if (isIndonesian()) listOf(
        "Di PC, buka Pushvia Desktop lalu tekan Mulai menerima di layar Terima.",
        "Salin tautan yang tampil, lalu tempel di sini.",
        "Pilih foto atau file di ponsel, lalu ketuk Kirim ke PC.",
        "Tetap di jaringan yang sama. Jika koneksi putus, pengiriman dilanjutkan dari titik terakhir."
    ) else steps
    TransferMode.REMOTE -> if (isIndonesian()) listOf(
        "Pasang Tailscale atau ZeroTier di ponsel ini dan di perangkat lain.",
        "Masuk dengan akun atau jaringan yang sama di keduanya.",
        "Pastikan kedua perangkat terlihat terhubung di aplikasi VPN.",
        "Mulai Jarak Jauh, lalu pakai alamat VPN ponsel beserta port-nya di perangkat lain."
    ) else steps
}

/** Single source of truth for the three transfer modes. Used by Home, onboarding, and guides. */
enum class TransferMode(
    val displayName: String,
    val icon: ImageVector,
    val tagline: String,
    val description: String,
    val color: Color,
    val needs: String,
    val steps: List<String>,
    val tips: List<String>,
    /** True when the mode can be started from the app. */
    val isLive: Boolean
) {
    BROWSER(
        displayName = "Browser Share",
        icon = Icons.Default.QrCode2,
        tagline = "Send files to any computer with a browser",
        description = "Pushvia starts a small private server on this phone. Scan the QR code with a computer or another phone on the same Wi-Fi, then download or upload files from the browser.",
        color = Color(0xFF3155CA),
        needs = "Same Wi-Fi as the other device",
        steps = listOf(
            "Connect both devices to the same Wi-Fi. Browser Share can access shared phone storage.",
            "Tap Start Browser Share.",
            "Scan the QR code on another device, or copy the full private link including its token.",
            "Download files from the page, or upload files back to this phone."
        ),
        tips = listOf(
            "The page works in any modern browser. No install needed.",
            "Stop the share when you are done. It also stops automatically after the auto-stop time.",
            "Files never leave your local network."
        ),
        isLive = true
    ),
    PUSH(
        displayName = "Push to PC",
        icon = Icons.Default.Publish,
        tagline = "Select files and send them to your computer",
        description = "Keep Pushvia Desktop open on the PC. Start its receiver and paste the private receive link here. Choose files to send over the same Wi-Fi or a private VPN. Interrupted chunks can resume while the transfer service stays active.",
        color = Color(0xFF3155CA),
        needs = "Same Wi-Fi, or a private VPN for different networks",
        steps = listOf(
            "On the PC, open Pushvia Desktop and press Start receiving on the Receive screen.",
            "Copy the address it shows and paste it below.",
            "Pick photos or files on this phone, then tap Push to PC.",
            "Keep the phone on the same network. If the connection drops, the push continues where it left off."
        ),
        tips = listOf(
            "Already-received files are skipped, so you can safely push the same folder again.",
            "The push runs as a background service and survives screen lock and app switching.",
            "Pushvia has no file-storage cloud. A VPN provider may relay encrypted traffic when a direct route is unavailable."
        ),
        isLive = true
    ),
    REMOTE(
        displayName = "Remote Private",
        icon = Icons.Default.Public,
        tagline = "Reach your phone from another network using a private VPN",
        description = "Remote mode works through a private network such as Tailscale or ZeroTier. Both devices join the same private network, then connect as if they were on one LAN.",
        color = Color(0xFF3155CA),
        needs = "Tailscale or ZeroTier on both devices",
        steps = listOf(
            "Install Tailscale or ZeroTier on this phone and on the other device.",
            "Sign in to the same account or network on both.",
            "Confirm both devices show as connected in the VPN app.",
            "Start Remote Private, then use the phone's reachable VPN address and port on the other device."
        ),
        tips = listOf(
            "The VPN handles routing. Pushvia runs a local HTTP server on this phone, not a hosted cloud server.",
            "Remote uses the same server as Browser Share, so the page and features are identical."
        ),
        isLive = true
    )
}
