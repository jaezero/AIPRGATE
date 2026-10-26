package com.example.aiprgate.auth

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
    data class Authorized(val token: String) : DevicePoll
    data object Denied : DevicePoll
    data object Expired : DevicePoll
}

interface DeviceAuth {
    suspend fun start(clientId: String): DeviceCode
    suspend fun poll(clientId: String, deviceCode: String): DevicePoll
}
