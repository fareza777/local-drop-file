package com.localdrop.app.ads

object AdPolicy {
    const val COOLDOWN_MS = 5 * 60_000L
    fun eligible(now: Long, lastShown: Long, completedBatches: Int, transferActive: Boolean, pairingActive: Boolean): Boolean =
        !transferActive && !pairingActive && completedBatches >= 3 && now - lastShown >= COOLDOWN_MS
}
