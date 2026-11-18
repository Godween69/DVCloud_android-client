package ru.dvbstudio.dvcloud.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Экран ввода логина и пароля.
 *
 * Поток:
 * 1. Пользователь вводит данные.
 * 2. Нажатие Enter на клавиатуре перемещает фокус: логин → пароль → кнопка.
 * 3. Кнопка "Войти" запускает LoginViewModel.login().
 * 4. При успехе без 2FA → onSuccess().
 * 5. При RequiresTwoFactor → onRequiresTwoFactor(ticket, methods).
 * 6. При ошибке → Snackbar с сообщением.
 */
@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    onRequiresTwoFactor: (ticket: String, methods: List<String>) -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Реакция на смену состояния.
    LaunchedEffect(state) {
        when (val s = state) {
            is LoginUiState.Success -> {
                onSuccess()
            }
            is LoginUiState.RequiresTwoFactor -> {
                // Ticket берём из ViewModel напрямую, чтобы не плодить state
                onSuccess // не вызываем; обработает DvCloudApp
            }
            is LoginUiState.Error -> {
                val msg = context.getString(s.messageId) + (s.details?.let { "\n$it" } ?: "")
                snackbarHostState.showSnackbar(msg)
                viewModel.clearError()
            }
            else -> Unit
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state is LoginUiState.Loading) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(id = ru.dvbstudio.dvcloud.R.string.login_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(id = ru.dvbstudio.dvcloud.R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = viewModel.username,
                        onValueChange = viewModel::updateUsername,
                        label = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.login_username)) },
                        singleLine = true,
                        enabled = state !is LoginUiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    )

                    OutlinedTextField(
                        value = viewModel.password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.login_password)) },
                        singleLine = true,
                        enabled = state !is LoginUiState.Loading,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                viewModel.login()
                            },
                        ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.login()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state !is LoginUiState.Loading,
                    ) {
                        Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.login_button))
                    }
                }
            }
        }
    }
}
