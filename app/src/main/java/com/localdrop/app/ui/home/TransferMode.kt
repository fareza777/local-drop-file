package com.localdrop.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

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
        description = "LocalDrop starts a small private server on this phone. Scan the QR code with a computer or another phone on the same Wi-Fi, then download or upload files from the browser.",
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
        description = "Keep LocalDrop Desktop open on the PC. Start its receiver and paste the private receive link here. Choose files to send over the same Wi-Fi or a private VPN. Interrupted chunks can resume while the transfer service stays active.",
        color = Color(0xFF3155CA),
        needs = "Same Wi-Fi, or a private VPN for different networks",
        steps = listOf(
            "On the PC, open LocalDrop Desktop and press Start receiving on the Receive screen.",
            "Copy the address it shows and paste it below.",
            "Pick photos or files on this phone, then tap Push to PC.",
            "Keep the phone on the same network. If the connection drops, the push continues where it left off."
        ),
        tips = listOf(
            "Already-received files are skipped, so you can safely push the same folder again.",
            "The push runs as a background service and survives screen lock and app switching.",
            "LocalDrop has no file-storage cloud. A VPN provider may relay encrypted traffic when a direct route is unavailable."
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
            "The VPN handles routing. LocalDrop runs a local HTTP server on this phone, not a hosted cloud server.",
            "Remote uses the same server as Browser Share, so the page and features are identical."
        ),
        isLive = true
    )
}
