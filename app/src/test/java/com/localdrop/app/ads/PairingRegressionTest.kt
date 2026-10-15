package com.localdrop.app.ads

import com.localdrop.app.server.LocalHttpServer
import com.localdrop.app.server.SessionToken
import com.localdrop.app.server.TrustedDeviceStore
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class PairingRegressionTest {
    @Test fun approvedPairingWorksAndRemovingDeviceRevokesToken() {
        val root = Files.createTempDirectory("localdrop-pairing").toFile()
        val devices = mutableSetOf<String>()
        val trusted = object : TrustedDeviceStore {
            override fun put(deviceId: String, deviceName: String, fingerprint: String) { devices.add("$deviceId:$fingerprint") }
            override fun contains(deviceId: String, fingerprint: String) = devices.contains("$deviceId:$fingerprint")
        }
        val server = LocalHttpServer(root, SessionToken.issue(), trusted)
        val port = server.start().substringAfterLast(':')
        fun call(route: String, body: String? = null, token: String? = null): Pair<Int, String> {
            val connection = URL("http://127.0.0.1:$port/api/v1/$route").openConnection() as HttpURLConnection
            connection.connectTimeout = 3000; connection.readTimeout = 3000
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { connection.requestMethod = "POST"; connection.doOutput = true; connection.outputStream.use { it.write(body.toByteArray()) } }
            val status = connection.responseCode
            val text = (if (status >= 400) connection.errorStream else connection.inputStream).bufferedReader().use { it.readText() }
            connection.disconnect(); return status to text
        }
        try {
            assertEquals(401, call("transfer/files").first)
            assertEquals(201, call("pair/request", """{"device_id":"pc","fingerprint":"fp","name":"Test PC"}""").first)
            val request = server.pendingRequests().single()
            assertEquals(401, call("pair/verify", """{"request_id":"${request.id}","pin":"${request.pin}"}""").first)
            assertTrue(server.approvePairing(request.id))
            val response = call("pair/verify", """{"request_id":"${request.id}","pin":"${request.pin}"}""")
            assertEquals(response.second, 200, response.first)
            val token = Regex("\"access_token\":\"([^\"]+)\"").find(response.second)!!.groupValues[1]
            assertEquals(200, call("transfer/files", token = token).first)
            val nested = root.resolve("Docs/small.txt"); nested.parentFile.mkdirs(); nested.writeText("small file")
            val downloaded = call("transfer/chunk?file_id=Docs%2Fsmall.txt&offset=0&length=1048576", token = token)
            assertEquals(downloaded.second, 206, downloaded.first)
            assertEquals("small file", downloaded.second)
            val end = call("transfer/chunk?file_id=Docs%2Fsmall.txt&offset=10&length=1048576", token = token)
            assertEquals(206, end.first)
            assertEquals("", end.second)
            assertEquals(400, call("transfer/chunk?file_id=Docs%2Fsmall.txt&offset=invalid&length=1", token = token).first)
            devices.clear()
            assertEquals(401, call("transfer/files", token = token).first)
        } finally { server.stop(); root.deleteRecursively() }
    }
}
