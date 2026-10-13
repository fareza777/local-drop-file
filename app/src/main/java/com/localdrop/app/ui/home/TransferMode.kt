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
        color = Color(0xFF5054C9),
        needs = "Same Wi-Fi as the other device",
        steps = listOf(
            "Add the files you want to share, or keep the app open to receive.",
            "Tap Start Browser Share.",
            "Scan the QR code with the other device's camera, or type the address shown.",
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
        tagline = "Push hundreds or thousands of files from this phone to your PC",
        description = "Your PC runs the LocalDrop receiver in the background. This phone pushes the files you pick over your private network (Tailscale or ZeroTier), keeps running while you use other apps, and resumes exactly where it stopped if the connection drops.",
        color = Color(0xFF138A68),
        needs = "Tailscale or ZeroTier on both devices",
        steps = listOf(
            "On the PC, open LocalDrop Desktop and press Start receiving on the Receive screen.",
            "Copy the address it shows and paste it below.",
            "Pick photos or files on this phone, then tap Push to PC.",
            "Keep the phone on the same network. If the connection drops, the push continues where it left off."
        ),
        tips = listOf(
            "Already-received files are skipped, so you can safely push the same folder again.",
            "The push runs as a background service and survives screen lock and app switching.",
            "LocalDrop never uses a relay: files travel directly between your devices."
        ),
        isLive = true
    ),
    REMOTE(
        displayName = "Remote Private",
        icon = Icons.Default.Public,
        tagline = "Reach your phone from another network using a private VPN",
        description = "Remote mode works through a private network such as Tailscale or ZeroTier. Both devices join the same private network, then connect as if they were on one LAN.",
        color = Color(0xFFE0703A),
        needs = "Tailscale or ZeroTier on both devices",
        steps = listOf(
            "Install Tailscale or ZeroTier on this phone and on the other device.",
            "Sign in to the same account or network on both.",
            "Confirm both devices show as connected in the VPN app.",
            "Start Browser Share, then open the VPN address shown on the other device."
        ),
        tips = listOf(
            "The VPN handles the connection. LocalDrop runs no servers of its own.",
            "Remote uses the same server as Browser Share, so the page and features are identical."
        ),
        isLive = true
    )
}
