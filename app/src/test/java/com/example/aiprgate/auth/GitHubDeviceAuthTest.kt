package com.example.aiprgate.auth

import com.example.aiprgate.github.GitHubError
import com.example.aiprgate.github.GitHubException
import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class GitHubDeviceAuthTest {
    private val startBody = """{
        "device_code":"device-value","user_code":"ABCD-EFGH",
        "verification_uri":"https://github.com/login/device","interval":5,"expires_in":900
    }"""

    @Test
    fun startEncodesOnlySuppliedClientIdAndParsesDeviceCode() = runTest {
        val transport = RecordingTransport(startBody)

        val code = GitHubDeviceAuth(transport).start("client +&=한글")

        assertEquals(
            DeviceCode("device-value", "ABCD-EFGH", "https://github.com/login/device", 5, 900),
            code,
        )
        assertEquals("https://github.com/login/device/code", transport.url)
        assertEquals(mapOf("client_id" to "client +&=한글"), decode(transport.body))
        assertEquals("client_id=client+%2B%26%3D%ED%95%9C%EA%B8%80", transport.body)
        assertEquals(1, transport.calls)
    }

    @Test
    fun pollEncodesDeviceGrantWithoutClientSecret() = runTest {
        val transport = RecordingTransport("""{"error":"authorization_pending"}""")

        assertEquals(DevicePoll.Pending, GitHubDeviceAuth(transport).poll("id +&", "code +&=한"))

        assertEquals("https://github.com/login/oauth/access_token", transport.url)
        assertEquals(
            mapOf(
                "client_id" to "id +&",
                "device_code" to "code +&=한",
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
            decode(transport.body),
        )
        assertFalse(transport.body.contains("client_secret"))
        assertEquals(1, transport.calls)
    }

    @Test
    fun pollReturnsEveryProtocolStateWithoutSchedulingAnotherRequest() = runTest {
        val cases = mapOf(
            "authorization_pending" to DevicePoll.Pending,
            "slow_down" to DevicePoll.SlowDown,
            "access_denied" to DevicePoll.Denied,
            "expired_token" to DevicePoll.Expired,
        )
        for ((error, expected) in cases) {
            for (status in listOf(200, 400)) {
                val transport = RecordingTransport("""{"error":"$error"}""", status)

                assertEquals(expected, GitHubDeviceAuth(transport).poll("id", "code"))

                assertEquals(1, transport.calls)
            }
        }
    }

    @Test
    fun pollReturnsAuthorizedTokenForBearerResponse() = runTest {
        val transport = RecordingTransport("""{"access_token":"invalid-test-token","token_type":"bearer"}""")

        val result = GitHubDeviceAuth(transport).poll("id", "code")

        assertEquals(DevicePoll.Authorized("invalid-test-token"), result)
    }

    @Test
    fun startRejectsUnsafeVerificationUrisAndInvalidFields() = runTest {
        val invalidFields = listOf(
            "verification_uri" to "http://github.com/login/device",
            "verification_uri" to "https://github.com.evil.test/login/device",
            "verification_uri" to "https://github.com/login/device?redirect=evil",
            "verification_uri" to "https://user@github.com/login/device",
            "verification_uri" to "https://github.com/login/device#fragment",
            "device_code" to "",
            "user_code" to " ",
            "device_code" to 42,
            "interval" to 0,
            "interval" to -1,
            "interval" to 1.5,
            "interval" to "5",
            "expires_in" to 0,
            "expires_in" to 2147483648L,
            "expires_in" to JSONObject.NULL,
        )
        for ((field, value) in invalidFields) {
            val body = JSONObject(startBody).put(field, value).toString()

            expectError(GitHubError.INVALID_RESPONSE) {
                GitHubDeviceAuth(RecordingTransport(body)).start("id")
            }
        }
        for (field in listOf("device_code", "user_code", "verification_uri", "interval", "expires_in")) {
            val json = JSONObject(startBody).apply { remove(field) }
            expectError(GitHubError.INVALID_RESPONSE) {
                GitHubDeviceAuth(RecordingTransport(json.toString())).start("id")
            }
        }
    }

    @Test
    fun malformedOrContradictoryPollResponsesNeverAuthorize() = runTest {
        for (body in listOf(
            "not-json invalid-test-token",
            "[]",
            "{}",
            """{"access_token":" "}""",
            """{"access_token":42,"token_type":"bearer"}""",
            """{"access_token":"invalid-test-token","token_type":"basic"}""",
            """{"access_token":"invalid-test-token","token_type":"bearer","error":"access_denied"}""",
            """{"error":null}""",
            """{"error":"unknown_state"}""",
        )) {
            expectError(GitHubError.INVALID_RESPONSE) {
                GitHubDeviceAuth(RecordingTransport(body)).poll("id", "code")
            }
        }
        expectError(GitHubError.INVALID_RESPONSE) {
            GitHubDeviceAuth(RecordingTransport("""{"access_token":"test","token_type":"bearer"}""", 400))
                .poll("id", "code")
        }
    }

    @Test
    fun oauthConfigurationFailuresAreTyped() = runTest {
        val cases = mapOf(
            "incorrect_client_credentials" to GitHubError.AUTHENTICATION,
            "incorrect_device_code" to GitHubError.VALIDATION,
            "unsupported_grant_type" to GitHubError.VALIDATION,
            "device_flow_disabled" to GitHubError.VALIDATION,
        )
        for ((error, kind) in cases) {
            expectError(kind) {
                GitHubDeviceAuth(RecordingTransport("""{"error":"$error"}""")).poll("id", "code")
            }
        }
        expectError(GitHubError.VALIDATION) {
            GitHubDeviceAuth(RecordingTransport("""{"error":"device_flow_disabled"}""")).start("id")
        }
    }

    @Test
    fun httpAndNetworkFailuresAreTypedWithoutResponseOrCauseDisclosure() = runTest {
        val cases = mapOf(
            301 to GitHubError.INVALID_RESPONSE,
            302 to GitHubError.INVALID_RESPONSE,
            401 to GitHubError.AUTHENTICATION,
            403 to GitHubError.PERMISSION,
            404 to GitHubError.NOT_FOUND,
            422 to GitHubError.VALIDATION,
            429 to GitHubError.RATE_LIMIT,
            500 to GitHubError.SERVER,
            503 to GitHubError.SERVER,
        )
        for ((status, kind) in cases) {
            val error = expectError(kind) {
                GitHubDeviceAuth(RecordingTransport("invalid-test-token", status)).poll("id", "code")
            }
            assertEquals(status, error.status)
        }
        expectError(GitHubError.NETWORK) {
            GitHubDeviceAuth(DeviceAuthTransport { _, _ -> throw IOException("invalid-test-token") })
                .start("id")
        }
    }

    @Test
    fun cancellationPropagatesAndBlankInputsNeverReachTransport() = runTest {
        val cancelled = GitHubDeviceAuth(DeviceAuthTransport { _, _ -> throw CancellationException() })
        try {
            cancelled.poll("id", "code")
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation is not a network error.
        }
        val transport = RecordingTransport(startBody)
        val auth = GitHubDeviceAuth(transport)
        for (call in listOf<suspend () -> Any>(
            { auth.start(" ") }, { auth.poll("", "code") }, { auth.poll("id", " ") },
        )) {
            try {
                call()
                fail("Expected rejected blank input")
            } catch (_: IllegalArgumentException) {
                assertEquals(0, transport.calls)
            }
        }
    }

    private suspend fun expectError(
        kind: GitHubError,
        call: suspend () -> Any,
    ): GitHubException {
        try {
            call()
            throw AssertionError("Expected $kind")
        } catch (error: GitHubException) {
            assertEquals(kind, error.kind)
            assertEquals(kind.name, error.message)
            assertNull(error.cause)
            return error
        }
    }

    private fun decode(form: String): Map<String, String> =
        form.split("&").associate {
            val fields = it.split("=", limit = 2)
            URLDecoder.decode(fields[0], "UTF-8") to URLDecoder.decode(fields[1], "UTF-8")
        }

    private class RecordingTransport(
        private val response: String,
        private val status: Int = 200,
    ) : DeviceAuthTransport {
        var calls = 0
        var url = ""
        var body = ""
        override fun post(url: String, encodedForm: String): DeviceAuthResponse {
            calls++
            this.url = url
            body = encodedForm
            return DeviceAuthResponse(status, response)
        }
    }
}
