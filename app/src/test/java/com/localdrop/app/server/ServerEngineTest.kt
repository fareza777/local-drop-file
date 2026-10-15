package com.localdrop.app.server

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class ServerEngineTest {
    private class Api(val server: LocalHttpServer, val token: String) {
        val port = server.start().substringAfterLast(':')
        fun call(method: String, route: String, body: ByteArray? = null, headers: Map<String, String> = emptyMap(), auth: Boolean = true): Pair<Int, String> {
            val c = URL("http://127.0.0.1:$port/api/v1/$route").openConnection() as HttpURLConnection
            c.connectTimeout = 3000; c.readTimeout = 3000; c.requestMethod = method
            if (auth) c.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body) } }
            val status = c.responseCode
            val text = (if (status >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect(); return status to text
        }
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun withServer(pin: String? = null, block: (Api, java.io.File) -> Unit) {
        val root = Files.createTempDirectory("localdrop-engine").toFile()
        val token = SessionToken.issue()
        val server = LocalHttpServer(root, token, null, accessPin = pin)
        try { block(Api(server, token.value), root) } finally { server.stop(); root.deleteRecursively() }
    }

    @Test fun interruptedUploadReportsOffsetAndResumes() = withServer { api, root ->
        val data = ByteArray(300) { (it % 200).toByte() }
        val digest = sha(data)
        fun chunk(offset: Int, bytes: ByteArray) = api.call("POST", "transfer/chunk?transfer_id=ld-abc&file_id=Docs%2Fa.bin&offset=$offset", bytes, mapOf("x-sha256" to digest, "x-file-size" to "300"))
        assertEquals(0, Regex("\"offset\":(\\d+)").find(api.call("GET", "transfer/status?transfer_id=ld-abc&file_id=Docs%2Fa.bin&size=300").second)!!.groupValues[1].toInt())
        assertEquals(200, chunk(0, data.copyOfRange(0, 100)).first)
        assertTrue(api.call("GET", "transfer/status?transfer_id=ld-abc&file_id=Docs%2Fa.bin&size=300").second.contains("\"offset\":100"))
        assertEquals(400, chunk(0, data.copyOfRange(0, 100)).first)
        val last = chunk(100, data.copyOfRange(100, 300))
        assertEquals(last.second, 201, last.first)
        assertArrayEquals(data, root.resolve("Docs/a.bin").readBytes())
    }

    @Test fun partialUploadSurvivesAServerRestartInStatus() {
        val root = Files.createTempDirectory("localdrop-restart").toFile()
        try {
            val data = ByteArray(64) { it.toByte() }; val digest = sha(data)
            val token = SessionToken.issue()
            val first = LocalHttpServer(root, token, null)
            val one = Api(first, token.value)
            one.call("POST", "transfer/chunk?transfer_id=ld-keep&file_id=a.bin&offset=0", data.copyOfRange(0, 40), mapOf("x-sha256" to digest, "x-file-size" to "64"))
            first.stop()
            val second = LocalHttpServer(root, token, null)
            val two = Api(second, token.value)
            assertTrue(two.call("GET", "transfer/status?transfer_id=ld-keep&file_id=a.bin&size=64").second.contains("\"offset\":40"))
            val done = two.call("POST", "transfer/chunk?transfer_id=ld-keep&file_id=a.bin&offset=40", data.copyOfRange(40, 64), mapOf("x-sha256" to digest, "x-file-size" to "64"))
            assertEquals(done.second, 201, done.first)
            assertArrayEquals(data, root.resolve("a.bin").readBytes())
            second.stop()
        } finally { root.deleteRecursively() }
    }

    @Test fun transferIdsCannotEscapeTheRoot() = withServer { api, root ->
        val digest = sha(byteArrayOf(1))
        val r = api.call("POST", "transfer/chunk?transfer_id=%2F..%2F..%2Fevil&file_id=a.bin&offset=0", byteArrayOf(1), mapOf("x-sha256" to digest, "x-file-size" to "5"))
        assertEquals(400, r.first)
        assertEquals(400, api.call("GET", "transfer/status?transfer_id=..%2Fx&file_id=a.bin&size=5").first)
        assertTrue(root.parentFile.listFiles().orEmpty().none { it.name.contains("evil") })
    }

    @Test fun emptyFilesAreSupportedAndNeverOverwrite() = withServer { api, root ->
        assertEquals(201, api.call("POST", "transfer/empty?file_id=notes%2Fempty.txt", ByteArray(0)).first)
        assertEquals(0L, root.resolve("notes/empty.txt").length())
        root.resolve("keep.txt").writeText("keep")
        assertEquals(201, api.call("POST", "transfer/empty?file_id=keep.txt", ByteArray(0)).first)
        assertEquals("keep", root.resolve("keep.txt").readText())
        assertEquals(400, api.call("POST", "transfer/empty?file_id=..%2Fescape.txt", ByteArray(0)).first)
    }

    @Test fun largeFilesAboveOldLimitAreAccepted() = withServer { api, _ ->
        val digest = sha(byteArrayOf(1))
        val r = api.call("POST", "transfer/chunk?transfer_id=ld-big&file_id=big.bin&offset=0", byteArrayOf(1), mapOf("x-sha256" to digest, "x-file-size" to (500L * 1024 * 1024).toString()))
        assertEquals(200, r.first)
    }

    @Test fun wrongPinsLockTheClientOut() = withServer(pin = "123456") { api, _ ->
        repeat(5) { assertEquals(403, api.call("GET", "unlock?pin=000000", auth = false).first) }
        assertEquals(429, api.call("GET", "unlock?pin=123456", auth = false).first)
    }

    @Test fun correctPinStillWorksBeforeLockout() = withServer(pin = "123456") { api, _ ->
        repeat(2) { api.call("GET", "unlock?pin=000000", auth = false) }
        val ok = api.call("GET", "unlock?pin=123456", auth = false)
        assertEquals(ok.second, 200, ok.first)
    }

    @Test fun limiterUnlocksAfterTheWaitAndForgetsOnSuccess() {
        var now = 0L
        val limiter = AttemptLimiter(3, 1000) { now }
        repeat(3) { limiter.fail("a") }
        assertTrue(limiter.isLocked("a")); assertFalse(limiter.isLocked("b"))
        now = 1001; assertFalse(limiter.isLocked("a"))
        limiter.fail("a"); limiter.success("a"); limiter.fail("a"); limiter.fail("a"); assertFalse(limiter.isLocked("a"))
    }

    @Test fun pairingRequestIsDroppedAfterFiveWrongPins() = withServer { api, _ ->
        assertEquals(201, api.call("POST", "pair/request", """{"device_id":"pc","fingerprint":"fp","name":"PC"}""".toByteArray(), auth = false).first)
        val request = api.server.pendingRequests().single()
        api.server.approvePairing(request.id)
        repeat(5) { api.call("POST", "pair/verify", """{"request_id":"${request.id}","pin":"999999"}""".toByteArray(), auth = false) }
        assertTrue(api.server.pendingRequests().isEmpty())
    }
}
