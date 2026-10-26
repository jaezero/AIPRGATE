package com.example.aiprgate.auth

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HttpDeviceAuthTransportTest {
    @Test
    fun postsJsonAcceptAndUtf8FormWithRedirectsDisabled() {
        val connection = FakeConnection(200, """{"error":"authorization_pending"}""")
        val transport = HttpDeviceAuthTransport { connection }

        val response = transport.post(connection.url.toString(), "client_id=a%2Bb")

        assertEquals(200, response.status)
        assertEquals("""{"error":"authorization_pending"}""", response.body)
        assertEquals("POST", connection.requestMethod)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals(
            "application/x-www-form-urlencoded; charset=UTF-8",
            connection.getRequestProperty("Content-Type"),
        )
        assertEquals("client_id=a%2Bb", connection.sent.toString("UTF-8"))
        assertTrue(connection.doOutput)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
        assertTrue(connection.closed)
    }

    @Test
    fun readsOAuthBadRequestFromErrorStream() {
        val connection = FakeConnection(400, """{"error":"incorrect_device_code"}""")

        val response = HttpDeviceAuthTransport { connection }
            .post(connection.url.toString(), "client_id=test")

        assertEquals(400, response.status)
        assertEquals("""{"error":"incorrect_device_code"}""", response.body)
        assertTrue(connection.closed)
    }

    @Test
    fun redirectsAndServerErrorsDoNotReadBodiesOrOpenAnotherConnection() {
        for (status in listOf(301, 302, 307, 308, 500)) {
            val connection = FakeConnection(status, "must-not-be-read")
            var connections = 0
            val transport = HttpDeviceAuthTransport { connections++; connection }

            val response = transport.post(connection.url.toString(), "device_code=test")

            assertEquals(status, response.status)
            assertEquals("", response.body)
            assertFalse(connection.instanceFollowRedirects)
            assertEquals(0, connection.bodyReads)
            assertEquals(1, connections)
            assertTrue(connection.closed)
        }
    }

    @Test
    fun disconnectsOnIoFailureAndRejectsNonHttpsEndpoints() {
        val connection = FakeConnection(200, "{}", failOutput = true)
        val transport = HttpDeviceAuthTransport { connection }
        try {
            transport.post(connection.url.toString(), "client_id=test")
            fail("Expected I/O failure")
        } catch (_: IOException) {
            assertTrue(connection.closed)
        }
        var opens = 0
        val guarded = HttpDeviceAuthTransport { opens++; connection }
        for (url in listOf("http://github.com/login/device/code", "https://evil.test/")) {
            try {
                guarded.post(url, "client_id=test")
                fail("Expected rejected endpoint")
            } catch (_: IllegalArgumentException) {
                assertEquals(0, opens)
            }
        }
    }

    private class FakeConnection(
        private val code: Int,
        private val body: String,
        private val failOutput: Boolean = false,
    ) : HttpURLConnection(URL("https://github.com/login/device/code")) {
        val sent = ByteArrayOutputStream()
        var closed = false
        var bodyReads = 0
        override fun getOutputStream(): ByteArrayOutputStream {
            if (failOutput) throw IOException("test failure")
            return sent
        }
        override fun getResponseCode(): Int = code
        override fun getInputStream(): ByteArrayInputStream {
            bodyReads++
            return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        }
        override fun getErrorStream(): ByteArrayInputStream = getInputStream()
        override fun disconnect() { closed = true }
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
