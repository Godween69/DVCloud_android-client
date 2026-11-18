package ru.dvbstudio.dvcloud.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
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
 * Ключевые UX-решения:
 * - imePadding() — клавиатура сдвигает контент вверх, кнопка "Войти" не прячется
 * - verticalScroll — можно скроллить, если экран всё-таки не помещается
 * - Ошибки показываются под каждым полем красным текстом (не Snackbar)
 * - Успех — диалог с галочкой и кнопкой "Продолжить"
 * - Общая ошибка (сеть, сервер) — в красной карточке над кнопкой
 */
@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    onRequiresTwoFactor: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Реакция на смену состояния
    LaunchedEffect(state) {
        when (state) {
            is LoginUiState.RequiresTwoFactor -> onRequiresTwoFactor()
            is LoginUiState.Authenticated -> onSuccess()
            else -> Unit
        }
    }

    // Скрываем клавиатуру при переходе в Loading
    LaunchedEffect(state) {
        if (state is LoginUiState.Loading) keyboardController?.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()                    // отступ под клавиатуру
            .navigationBarsPadding()         // отступ под системную панель
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state is LoginUiState.Loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = ru.dvbstudio.dvcloud.R.string.login_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(id = ru.dvbstudio.dvcloud.R.string.login_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(id = ru.dvbstudio.dvcloud.R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Поле логина + ошибка под ним
                OutlinedTextField(
                    value = viewModel.username,
                    onValueChange = viewModel::updateUsername,
                    label = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.login_username)) },
                    singleLine = true,
                    isError = viewModel.usernameError != null,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
                if (viewModel.usernameError != null) {
                    Text(
                        text = viewModel.usernameError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                // Поле пароля + ошибка под ним
                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.login_password)) },
                    singleLine = true,
                    isError = viewModel.passwordError != null,
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
                if (viewModel.passwordError != null) {
                    Text(
                        text = viewModel.passwordError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                // Общая ошибка (сеть, сервер) — красная карточка
                if (state is LoginUiState.Error) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (state as LoginUiState.Error).details,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.login()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = stringResource(id = ru.dvbstudio.dvcloud.R.string.login_button),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    // Диалог успеха
    if (state is LoginUiState.Success) {
        val name = (state as LoginUiState.Success).displayName
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp),
                )
            },
            title = {
                Text(
                    text = stringResource(id = ru.dvbstudio.dvcloud.R.string.auth_success_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.auth_success_subtitle, name))
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissSuccess() }) {
                    Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.auth_success_button))
                }
            },
        )
    }
}
