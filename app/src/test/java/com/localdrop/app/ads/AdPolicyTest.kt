package com.localdrop.app.ads

import org.junit.Assert.*
import org.junit.Test

class AdPolicyTest {
    @Test fun neverInterruptsTransfersOrPairing() {
        assertFalse(AdPolicy.eligible(600_000, 0, 3, true, false))
        assertFalse(AdPolicy.eligible(600_000, 0, 3, false, true))
    }
    @Test fun requiresThreeCompletedBatchesAndFiveMinuteCooldown() {
        assertFalse(AdPolicy.eligible(600_000, 0, 2, false, false))
        assertFalse(AdPolicy.eligible(299_999, 0, 3, false, false))
        assertTrue(AdPolicy.eligible(300_000, 0, 3, false, false))
        assertFalse(AdPolicy.eligible(700_000, 600_000, 3, false, false))
    }
    @Test fun boundaryAndClockRegressionAreSafe() {
        assertTrue(AdPolicy.eligible(900_000, 600_000, 3, false, false))
        assertFalse(AdPolicy.eligible(500_000, 600_000, 3, false, false))
    }
}
