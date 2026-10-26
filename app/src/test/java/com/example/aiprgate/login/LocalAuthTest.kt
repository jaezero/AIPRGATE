package com.example.aiprgate.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryAccountStore : AccountStore {
    var account: StoredAccount? = null
    override fun read() = account
    override fun save(account: StoredAccount) { this.account = account }
}

class LocalAuthTest {
    private val store = MemoryAccountStore()
    // Fewer iterations keep the JVM test fast; the derivation path is the same.
    private val auth = LocalAuth(store, iterations = 1_000)

    @Test
    fun validatorChecksIdFormatAndPasswordLengthBoundaries() {
        assertEquals(CredentialError.ID_FORMAT, LoginValidator.validate("abc", "password"))
        assertNull(LoginValidator.validate("abcd", "password"))
        assertNull(LoginValidator.validate("a".repeat(20), "password"))
        assertEquals(CredentialError.ID_FORMAT, LoginValidator.validate("a".repeat(21), "password"))
        assertEquals(CredentialError.ID_FORMAT, LoginValidator.validate("user name", "password"))
        assertEquals(CredentialError.PASSWORD_TOO_SHORT, LoginValidator.validate("user_01", "1234567"))
        assertNull(LoginValidator.validate("user_01", "12345678"))
    }

    @Test
    fun registeredAccountStoresHashInsteadOfPassword() {
        assertFalse(auth.hasAccount())
        assertEquals(AuthResult.SUCCESS, auth.register("user_01", "correct-pass"))

        val saved = store.account!!
        assertTrue(auth.hasAccount())
        assertFalse(saved.hashHex.contains("correct-pass"))
        assertEquals(64, saved.hashHex.length)
        assertEquals(32, saved.saltHex.length)
    }

    @Test
    fun loginSucceedsOnlyWithMatchingIdAndPassword() {
        auth.register("user_01", "correct-pass")

        assertEquals(AuthResult.SUCCESS, auth.login("user_01", "correct-pass"))
        assertEquals(AuthResult.INVALID_CREDENTIALS, auth.login("user_01", "wrong-pass"))
        assertEquals(AuthResult.INVALID_CREDENTIALS, auth.login("user_02", "correct-pass"))
    }

    @Test
    fun loginWithoutAccountAndSecondRegistrationAreRejected() {
        assertEquals(AuthResult.NO_ACCOUNT, auth.login("user_01", "correct-pass"))
        auth.register("user_01", "correct-pass")

        assertEquals(AuthResult.ACCOUNT_EXISTS, auth.register("other_1", "another-pass"))
        assertEquals("user_01", store.account!!.id)
    }

    @Test
    fun samePasswordGetsDifferentHashWithNewSalt() {
        auth.register("user_01", "correct-pass")
        val first = store.account!!
        val otherStore = MemoryAccountStore()
        LocalAuth(otherStore, iterations = 1_000).register("user_01", "correct-pass")

        assertNotEquals(first.saltHex, otherStore.account!!.saltHex)
        assertNotEquals(first.hashHex, otherStore.account!!.hashHex)
    }
}
