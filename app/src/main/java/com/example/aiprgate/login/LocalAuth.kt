package com.example.aiprgate.login

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class CredentialError { ID_FORMAT, PASSWORD_TOO_SHORT }

enum class AuthResult { SUCCESS, INVALID_CREDENTIALS, NO_ACCOUNT, ACCOUNT_EXISTS }

/** Only the salt and derived hash are stored; the password itself never is. */
data class StoredAccount(val id: String, val saltHex: String, val hashHex: String)

interface AccountStore {
    fun read(): StoredAccount?
    fun save(account: StoredAccount)
}

object LoginValidator {
    private val idPattern = Regex("[A-Za-z0-9_]{4,20}")
    const val MIN_PASSWORD_LENGTH = 8

    fun validate(id: String, password: String): CredentialError? = when {
        !idPattern.matches(id) -> CredentialError.ID_FORMAT
        password.length < MIN_PASSWORD_LENGTH -> CredentialError.PASSWORD_TOO_SHORT
        else -> null
    }
}

/**
 * Local-only sample login for the verification target app: one account per device,
 * no server. PBKDF2 keeps a copied preferences file from revealing the password.
 */
class LocalAuth(
    private val store: AccountStore,
    private val random: SecureRandom = SecureRandom(),
    private val iterations: Int = 120_000,
) {
    fun hasAccount(): Boolean = store.read() != null

    fun register(id: String, password: String): AuthResult {
        if (store.read() != null) return AuthResult.ACCOUNT_EXISTS
        val salt = ByteArray(16).also(random::nextBytes)
        store.save(StoredAccount(id, salt.toHex(), derive(password, salt).toHex()))
        return AuthResult.SUCCESS
    }

    fun login(id: String, password: String): AuthResult {
        val account = store.read() ?: return AuthResult.NO_ACCOUNT
        val actual = derive(password, account.saltHex.hexToBytes())
        // Compare the hash in constant time; report a wrong id and a wrong password alike.
        val matches = MessageDigest.isEqual(actual, account.hashHex.hexToBytes())
        return if (matches && account.id == id) AuthResult.SUCCESS else AuthResult.INVALID_CREDENTIALS
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
