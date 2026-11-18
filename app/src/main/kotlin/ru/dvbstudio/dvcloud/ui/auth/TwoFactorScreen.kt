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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Экран ввода кода 2FA (TOTP или запасной).
 *
 * Получает ticket от сервера через LoginViewModel (общий экземпляр через viewModel()).
 * Код 6 цифр — фокус на поле, автоотправка при вводе 6 цифр.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state) {
        when (val s = state) {
            is LoginUiState.Success -> {
                onSuccess()
            }
            is LoginUiState.Error -> {
                val msg = context.getString(s.messageId) + (s.details?.let { "\n$it" } ?: "")
                snackbarHostState.showSnackbar(msg)
                viewModel.clearError()
            }
            else -> Unit
        }
    }

    // Автоотправка при 6 цифрах (типичный TOTP)
    LaunchedEffect(viewModel.twoFactorCode) {
        if (viewModel.twoFactorCode.length == 6) {
            viewModel.completeTwoFactor()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.backToLogin()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = ru.dvbstudio.dvcloud.R.string.cancel),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
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
                        text = stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = viewModel.twoFactorCode,
                        onValueChange = {
                            // Только цифры
                            if (it.all(Char::isDigit) && it.length <= 8) {
                                viewModel.updateTwoFactorCode(it)
                            }
                        },
                        label = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_code)) },
                        singleLine = true,
                        enabled = state !is LoginUiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                viewModel.completeTwoFactor()
                            },
                        ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.completeTwoFactor()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state !is LoginUiState.Loading,
                    ) {
                        Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_button))
                    }
                }
            }
        }
    }
}
