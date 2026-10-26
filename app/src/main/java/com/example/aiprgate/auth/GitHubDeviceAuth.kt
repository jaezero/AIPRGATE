package com.example.aiprgate.auth

import com.example.aiprgate.github.GitHubError
import com.example.aiprgate.github.GitHubException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** One request per call. The caller owns scheduling, cancellation and local expiry. */
class GitHubDeviceAuth internal constructor(private val transport: DeviceAuthTransport) : DeviceAuth {
    constructor() : this(HttpDeviceAuthTransport())

    override suspend fun start(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "Client ID must not be blank" }
        val json = request(DEVICE_URL, form("client_id" to clientId))
        if (json.has("error")) throw oauthError(json)
        DeviceCode(
            deviceCode = json.requiredString("device_code"),
            userCode = json.requiredString("user_code"),
            verificationUri = json.requiredString("verification_uri").also {
                if (it != VERIFICATION_URI) invalidResponse()
            },
            intervalSeconds = json.positiveInt("interval"),
            expiresInSeconds = json.positiveInt("expires_in"),
        )
    }

    override suspend fun poll(clientId: String, deviceCode: String): DevicePoll =
        withContext(Dispatchers.IO) {
            require(clientId.isNotBlank()) { "Client ID must not be blank" }
            require(deviceCode.isNotBlank()) { "Device code must not be blank" }
            val json = request(
                TOKEN_URL,
                form(
                    "client_id" to clientId,
                    "device_code" to deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
            if (json.has("error")) {
                // Contradictory responses must never become an authenticated session.
                if (json.has("access_token")) invalidResponse()
                when (json.requiredString("error")) {
                    "authorization_pending" -> DevicePoll.Pending
                    "slow_down" -> DevicePoll.SlowDown
                    "access_denied" -> DevicePoll.Denied
                    "expired_token" -> DevicePoll.Expired
                    else -> throw oauthError(json)
                }
            } else {
                if (!json.requiredString("token_type").equals("bearer", ignoreCase = true)) {
                    invalidResponse()
                }
                DevicePoll.Authorized(json.requiredString("access_token"))
            }
        }

    private fun request(url: String, body: String): JSONObject {
        val response = try {
            transport.post(url, body)
        } catch (_: IOException) {
            throw GitHubException(GitHubError.NETWORK)
        }
        if (response.status != 200 && response.status != 400) {
            val kind = when (response.status) {
                401 -> GitHubError.AUTHENTICATION
                403 -> GitHubError.PERMISSION
                404 -> GitHubError.NOT_FOUND
                429 -> GitHubError.RATE_LIMIT
                in 500..599 -> GitHubError.SERVER
                in 300..399 -> GitHubError.INVALID_RESPONSE
                else -> GitHubError.VALIDATION
            }
            throw GitHubException(kind, response.status)
        }
        val json = try {
            JSONObject(response.body)
        } catch (_: JSONException) {
            // JSONObject exceptions can contain response fragments. Do not expose them.
            invalidResponse()
        }
        if (response.status == 400 && !json.has("error")) invalidResponse()
        return json
    }

    private fun oauthError(json: JSONObject): GitHubException = GitHubException(
        when (json.requiredString("error")) {
            "incorrect_client_credentials" -> GitHubError.AUTHENTICATION
            "device_flow_disabled", "unsupported_grant_type", "bad_verification_code",
            "incorrect_device_code", "invalid_request" -> GitHubError.VALIDATION
            else -> GitHubError.INVALID_RESPONSE
        },
    )

    private fun JSONObject.requiredString(name: String): String =
        (opt(name) as? String)?.takeIf { it.isNotBlank() } ?: invalidResponse()

    private fun JSONObject.positiveInt(name: String): Int {
        val value = opt(name)
        val number = when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> invalidResponse()
        }
        if (number !in 1..Int.MAX_VALUE.toLong()) invalidResponse()
        return number.toInt()
    }

    private fun form(vararg fields: Pair<String, String>): String =
        fields.joinToString("&") { (name, value) ->
            "${URLEncoder.encode(name, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

    private fun invalidResponse(): Nothing = throw GitHubException(GitHubError.INVALID_RESPONSE)

    private companion object {
        const val DEVICE_URL = "https://github.com/login/device/code"
        const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val VERIFICATION_URI = "https://github.com/login/device"
    }
}

internal data class DeviceAuthResponse(val status: Int, val body: String)

internal fun interface DeviceAuthTransport {
    fun post(url: String, encodedForm: String): DeviceAuthResponse
}

internal class HttpDeviceAuthTransport(
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) : DeviceAuthTransport {
    override fun post(url: String, encodedForm: String): DeviceAuthResponse {
        val endpoint = URL(url)
        require(endpoint.protocol == "https" && endpoint.host == "github.com")
        val connection = openConnection(endpoint)
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val bytes = encodedForm.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            // Do not read error or redirect bodies, which may be HTML or contain credentials.
            val body = if (status == 200 || status == 400) {
                val input = if (status == 400) connection.errorStream else connection.inputStream
                input?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                ""
            }
            return DeviceAuthResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
