package com.example.aiprgate.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoginError { ID_FORMAT, PASSWORD_TOO_SHORT, INVALID_CREDENTIALS }

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
            val result = withContext(worker) {
                if (current.hasAccount) auth.login(current.id, current.password)
                else auth.register(current.id, current.password)
            }
            mutableState.update {
                if (result == AuthResult.SUCCESS) {
                    it.copy(isBusy = false, password = "", hasAccount = true, loggedIn = true)
                } else {
                    it.copy(isBusy = false, password = "", error = LoginError.INVALID_CREDENTIALS)
                }
            }
        }
    }

    private fun fail(error: LoginError) = mutableState.update { it.copy(error = error) }
}
