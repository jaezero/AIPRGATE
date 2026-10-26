package com.example.aiprgate.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Synchronous disk/Keystore operations: call off the main thread.
 * Missing storage returns null; corrupt storage or a lost key throws instead.
 * The caller must verify a token with GitHub before saving it.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val file = File(context.noBackupFilesDir, "github-session.enc")
    private val atomicFile = AtomicFile(file)

    override fun readToken(): String? = synchronized(lock) {
        val bytes = try {
            atomicFile.openRead().use { it.readBytes() }
        } catch (error: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists()) throw error
            return@synchronized null
        }
        if (bytes.size < 2 + IV_BYTES + TAG_BYTES ||
            bytes[0] != VERSION || bytes[1].toInt() != IV_BYTES
        ) {
            throw IOException("Invalid encrypted session")
        }
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IOException("Session key is unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BYTES * 8, bytes.copyOfRange(2, 2 + IV_BYTES)),
        )
        val token = cipher.doFinal(bytes, 2 + IV_BYTES, bytes.size - 2 - IV_BYTES)
            .toString(Charsets.UTF_8)
        if (token.isBlank()) throw IOException("Invalid encrypted session")
        token
    }

    override fun saveToken(token: String) = synchronized(lock) {
        require(token.isNotBlank()) { "Token must not be blank" }
        val store = keyStore()
        val key = store.getKey(KEY_ALIAS, null) as? SecretKey ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // AndroidKeyStore supplies a fresh random IV; never derive one from the token.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == IV_BYTES)
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val output = atomicFile.startWrite()
        try {
            output.write(byteArrayOf(VERSION, IV_BYTES.toByte()))
            output.write(cipher.iv)
            output.write(encrypted)
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    override fun clearToken() = synchronized(lock) {
        atomicFile.delete()
        if (file.exists() || File(file.path + ".bak").exists() ||
            File(file.path + ".new").exists()
        ) {
            throw IOException("Could not remove encrypted session")
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private companion object {
        // AtomicFile does not provide locking. Share a lock across store instances.
        val lock = Any()
        const val KEY_ALIAS = "com.example.aiprgate.auth.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
