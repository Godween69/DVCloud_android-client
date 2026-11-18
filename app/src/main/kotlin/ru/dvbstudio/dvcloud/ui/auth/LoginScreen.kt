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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
 * Экран входа.
 *
 * Ключ к работе с клавиатурой:
 * - Манифест: windowSoftInputMode="adjustResize" — Android уменьшает размер окна
 *   при появлении клавиатуры, а не сдвигает весь экран целиком.
 * - Compose: imePadding() на корневом Box добавляет отступ снизу под клавиатуру.
 * - contentAlignment = Alignment.TopCenter — контент прижимается к верху,
 *   а не центрируется в растянутой области (иначе кнопка «Войти» могла бы
 *   уйти за нижнюю границу доступной области).
 * - verticalScroll — страховка: если экран всё-таки не помещается, можно прокрутить.
 * - statusBarsPadding — отступ от статус-бара сверху.
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

    LaunchedEffect(state) {
        when (state) {
            is LoginUiState.RequiresTwoFactor -> onRequiresTwoFactor()
            is LoginUiState.Authenticated -> onSuccess()
            else -> Unit
        }
    }

    LaunchedEffect(state) {
        if (state is LoginUiState.Loading) keyboardController?.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Отступы системных зон: статус-бар сверху, клавиатура снизу, навигация снизу
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        // Контент прижат к верху — при появлении клавиатуры всё сдвигается вверх
        contentAlignment = Alignment.TopCenter,
    ) {
        if (state is LoginUiState.Loading) {
            // Спиннер в центре видимой области (после сдвига)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 80.dp),
                ) {
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
