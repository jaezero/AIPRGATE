package com.example.aiprgate.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSessionStoreTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private val token = "invalid-instrumentation-token-not-a-real-credential"

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(target.noBackupFilesDir, "auth-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
    }

    @After
    fun tearDown() {
        check(directory.deleteRecursively())
    }

    // Synchronous operations complete before assertions; no polling or timing delays.
    @Test(timeout = 15_000)
    fun realKeystoreRoundTripSurvivesNewStoreWithoutPlaintextFiles() {
        val store = KeystoreSessionStore(context)
        assertNull(store.readToken())

        store.saveToken(token)

        assertEquals(token, KeystoreSessionStore(context).readToken())
        val files = directory.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, files.size)
        assertTrue(files.single().canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
        assertFalse(files.single().readBytes().containsSequence(token.toByteArray()))
        assertFalse(files.single().readText(Charsets.ISO_8859_1).contains(token))
    }

    @Test(timeout = 15_000)
    fun atomicReplacementUsesFreshIvAndLeavesOnlyEncryptedLatestToken() {
        val store = KeystoreSessionStore(context)
        store.saveToken(token)
        val original = directory.listFiles()!!.single().readBytes()

        store.saveToken(token)

        val replaced = directory.listFiles()!!.single().readBytes()
        assertFalse(original.copyOfRange(2, 14).contentEquals(replaced.copyOfRange(2, 14)))
        assertEquals(token, KeystoreSessionStore(context).readToken())
        assertFalse(replaced.containsSequence(token.toByteArray()))
    }

    @Test(timeout = 15_000)
    fun replacementPersistsNewTokenAndRejectingBlankPreservesPreviousSession() {
        val store = KeystoreSessionStore(context)
        store.saveToken(token)
        store.saveToken("another-invalid-test-token")

        try {
            store.saveToken(" ")
            fail("Expected blank-token rejection")
        } catch (_: IllegalArgumentException) {
            assertEquals("another-invalid-test-token", KeystoreSessionStore(context).readToken())
        }
    }

    @Test(timeout = 15_000)
    fun logoutRemovesCiphertextAndDoesNotRestoreAtomicBackup() {
        val store = KeystoreSessionStore(context)
        store.saveToken(token)
        val encrypted = directory.listFiles()!!.single()
        encrypted.copyTo(File(encrypted.path + ".bak"))

        store.clearToken()

        assertTrue(directory.listFiles()!!.isEmpty())
        assertNull(KeystoreSessionStore(context).readToken())
        store.clearToken()
        assertNull(store.readToken())
    }

    @Test(timeout = 15_000)
    fun tamperedCiphertextFailsAuthenticationInsteadOfReturningSession() {
        val store = KeystoreSessionStore(context)
        store.saveToken(token)
        val file = directory.listFiles()!!.single()
        val corrupted = file.readBytes().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        file.writeBytes(corrupted)

        try {
            KeystoreSessionStore(context).readToken()
            fail("Expected authenticated-decryption failure")
        } catch (_: GeneralSecurityException) {
            store.clearToken()
            assertNull(store.readToken())
        }
    }

    @Test(timeout = 15_000)
    fun truncatedCiphertextThrowsAndCanBeCleared() {
        val store = KeystoreSessionStore(context)
        store.saveToken(token)
        directory.listFiles()!!.single().writeBytes(byteArrayOf(1, 12))

        try {
            store.readToken()
            fail("Expected corrupt-session failure")
        } catch (_: IOException) {
            store.clearToken()
            assertNull(store.readToken())
        }
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        (0..size - sequence.size).any { offset ->
            sequence.indices.all { this[offset + it] == sequence[it] }
        }
}
