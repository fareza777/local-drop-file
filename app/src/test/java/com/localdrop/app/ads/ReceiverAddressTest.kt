package com.localdrop.app.ads

import com.localdrop.app.ui.settings.validReceiverAddress
import org.junit.Assert.*
import org.junit.Test

class ReceiverAddressTest {
    @Test fun validLinksKeepTheirToken() {
        assertTrue(validReceiverAddress("http://192.168.1.8:42198?t=abc"))
        assertTrue(validReceiverAddress("https://pc.local?t=abc"))
        assertTrue(validReceiverAddress("http://[fd00::1]:42198?t=abc"))
    }
    @Test fun rejectsMissingTokenAndCredentials() {
        listOf("", "192.168.1.8:42198", "http://pc:42198", "http://pc?t=", "ftp://pc?t=abc", "http://user:pass@pc?t=abc", "not a URL").forEach { assertFalse(it, validReceiverAddress(it)) }
    }
}
