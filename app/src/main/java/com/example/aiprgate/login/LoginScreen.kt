package com.example.aiprgate.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aiprgate.R

@Composable
fun LoginScreen(
    state: LoginUiState,
    onIdChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val enabled = !state.isBusy
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(if (state.hasAccount) R.string.login_heading else R.string.login_register_heading),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.login_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.id,
                    onValueChange = onIdChange,
                    modifier = Modifier.fillMaxWidth().testTag("login_id"),
                    enabled = enabled,
                    label = { Text(stringResource(R.string.login_id_label)) },
                    singleLine = true,
                    isError = state.error == LoginError.ID_FORMAT,
                    supportingText = { Text(stringResource(R.string.login_id_rule)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth().testTag("login_password"),
                    enabled = enabled,
                    label = { Text(stringResource(R.string.login_password_label)) },
                    singleLine = true,
                    isError = state.error == LoginError.PASSWORD_TOO_SHORT,
                    supportingText = { Text(stringResource(R.string.login_password_rule)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (enabled) onSubmit() }),
                )
                state.error?.let { error ->
                    Text(
                        stringResource(when (error) {
                            LoginError.ID_FORMAT -> R.string.login_error_id
                            LoginError.PASSWORD_TOO_SHORT -> R.string.login_error_password
                            LoginError.INVALID_CREDENTIALS -> R.string.login_error_invalid
                            LoginError.ACCOUNT_CHANGED -> R.string.login_error_account_changed
                            LoginError.STORAGE_FAILED -> R.string.login_error_storage
                        }),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(
                    onClick = onSubmit,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("login_submit"),
                ) {
                    Text(stringResource(when {
                        state.isBusy -> R.string.login_checking
                        state.hasAccount -> R.string.login_submit
                        else -> R.string.login_register_submit
                    }))
                }
            }
        }
    }
}
