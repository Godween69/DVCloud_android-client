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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state) {
        when (state) {
            is LoginUiState.Authenticated -> onSuccess()
            else -> Unit
        }
    }

    // Автоотправка при 6 цифрах
    LaunchedEffect(viewModel.twoFactorCode) {
        if (viewModel.twoFactorCode.length == 6 && state !is LoginUiState.Loading) {
            viewModel.completeTwoFactor()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.backToLogin()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = ru.dvbstudio.dvcloud.R.string.cancel),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
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
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = viewModel.twoFactorCode,
                        onValueChange = {
                            if (it.all(Char::isDigit) && it.length <= 8) {
                                viewModel.updateTwoFactorCode(it)
                            }
                        },
                        label = { Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_code)) },
                        singleLine = true,
                        isError = viewModel.codeError != null,
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
                    if (viewModel.codeError != null) {
                        Text(
                            text = viewModel.codeError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }

                    // Общая ошибка
                    if (state is LoginUiState.Error) {
                        Text(
                            text = (state as LoginUiState.Error).details,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.completeTwoFactor()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(
                            text = stringResource(id = ru.dvbstudio.dvcloud.R.string.twofactor_button),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
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
