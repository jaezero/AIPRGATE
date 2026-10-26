package com.example.aiprgate.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoginError { ID_FORMAT, PASSWORD_TOO_SHORT, INVALID_CREDENTIALS, ACCOUNT_CHANGED, STORAGE_FAILED }

data class LoginUiState(
    val hasAccount: Boolean = false,
    val id: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val error: LoginError? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(
    private val auth: LocalAuth,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState(hasAccount = auth.hasAccount()))
    val uiState = mutableState.asStateFlow()

    fun onIdChange(id: String) = mutableState.update { it.copy(id = id, error = null) }

    fun onPasswordChange(password: String) =
        mutableState.update { it.copy(password = password, error = null) }

    fun submit() {
        val current = uiState.value
        if (current.isBusy) return
        when (LoginValidator.validate(current.id, current.password)) {
            CredentialError.ID_FORMAT -> return fail(LoginError.ID_FORMAT)
            CredentialError.PASSWORD_TOO_SHORT -> return fail(LoginError.PASSWORD_TOO_SHORT)
            null -> Unit
        }
        mutableState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            // Key derivation is deliberately slow, so keep it off the main thread.
            val result = try {
                withContext(worker) {
                    if (current.hasAccount) auth.login(current.id, current.password)
                    else auth.register(current.id, current.password)
                }
            } catch (error: Exception) {
                // A corrupted stored salt/hash or a missing algorithm must not crash the app.
                if (error is CancellationException) throw error
                null
            }
            mutableState.update {
                val done = it.copy(isBusy = false, password = "")
                when (result) {
                    AuthResult.SUCCESS -> done.copy(hasAccount = true, loggedIn = true)
                    AuthResult.INVALID_CREDENTIALS -> done.copy(error = LoginError.INVALID_CREDENTIALS)
                    // The stored account changed while this screen was open: switch to the right mode.
                    AuthResult.NO_ACCOUNT -> done.copy(hasAccount = false, error = LoginError.ACCOUNT_CHANGED)
                    AuthResult.ACCOUNT_EXISTS -> done.copy(hasAccount = true, error = LoginError.ACCOUNT_CHANGED)
                    null -> done.copy(error = LoginError.STORAGE_FAILED)
                }
            }
        }
    }

    private fun fail(error: LoginError) = mutableState.update { it.copy(error = error) }
}
