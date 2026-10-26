package com.example.aiprgate.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = object : AccountStore {
        var account: StoredAccount? = null
        override fun read() = account
        override fun save(account: StoredAccount) { this.account = account }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun model() = LoginViewModel(LocalAuth(store, iterations = 1_000), dispatcher)

    @Test
    fun invalidInputShowsErrorWithoutCreatingAccount() = runTest(dispatcher) {
        val model = model()
        model.onIdChange("ab")
        model.onPasswordChange("long-enough")
        model.submit()
        advanceUntilIdle()

        assertEquals(LoginError.ID_FORMAT, model.uiState.value.error)
        assertNull(store.account)
        assertFalse(model.uiState.value.loggedIn)
    }

    @Test
    fun firstSubmitRegistersThenWrongPasswordIsRejectedAndCleared() = runTest(dispatcher) {
        val first = model()
        first.onIdChange("user_01")
        first.onPasswordChange("correct-pass")
        first.submit()
        advanceUntilIdle()
        assertTrue(first.uiState.value.loggedIn)
        assertEquals("", first.uiState.value.password)

        val next = model()
        assertTrue(next.uiState.value.hasAccount)
        next.onIdChange("user_01")
        next.onPasswordChange("wrong-pass")
        next.submit()
        advanceUntilIdle()

        assertEquals(LoginError.INVALID_CREDENTIALS, next.uiState.value.error)
        assertEquals("", next.uiState.value.password)
        assertFalse(next.uiState.value.loggedIn)
    }

    @Test
    fun registeredUserLogsInAndDuplicateSubmitIsIgnored() = runTest(dispatcher) {
        LocalAuth(store, iterations = 1_000).register("user_01", "correct-pass")
        val model = model()
        model.onIdChange("user_01")
        model.onPasswordChange("correct-pass")
        model.submit()
        assertTrue(model.uiState.value.isBusy)
        model.submit()
        advanceUntilIdle()

        assertTrue(model.uiState.value.loggedIn)
        assertFalse(model.uiState.value.isBusy)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun corruptedStoredAccountShowsStorageErrorInsteadOfCrashing() = runTest(dispatcher) {
        store.account = StoredAccount("user_01", "not-hex", "zz")
        val model = model()
        model.onIdChange("user_01")
        model.onPasswordChange("correct-pass")
        model.submit()
        advanceUntilIdle()

        assertEquals(LoginError.STORAGE_FAILED, model.uiState.value.error)
        assertFalse(model.uiState.value.loggedIn)
        assertFalse(model.uiState.value.isBusy)
    }

    @Test
    fun accountRemovedWhileOpenSwitchesToRegistration() = runTest(dispatcher) {
        LocalAuth(store, iterations = 1_000).register("user_01", "correct-pass")
        val model = model()
        store.account = null
        model.onIdChange("user_01")
        model.onPasswordChange("correct-pass")
        model.submit()
        advanceUntilIdle()

        assertEquals(LoginError.ACCOUNT_CHANGED, model.uiState.value.error)
        assertFalse(model.uiState.value.hasAccount)
    }
}
