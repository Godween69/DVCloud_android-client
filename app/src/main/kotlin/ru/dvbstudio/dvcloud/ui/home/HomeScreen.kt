package ru.dvbstudio.dvcloud.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.dvbstudio.dvcloud.data.repository.AuthRepository

/**
 * Главный экран после успешного входа.
 *
 * MVP: показывает имя пользователя и кнопки выхода/сброса.
 * В следующих шагах: лента, автовыгрузка, настройки.
 */
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthRepository(context) }
    val authState by repository.authStateFlow().collectAsState(initial = null)
    var showWipeDialog by remember { mutableStateOf(false) }

    val displayName = (authState as? ru.dvbstudio.dvcloud.domain.model.AuthState.Authenticated)
        ?.deviceToken?.deviceName
        ?: "Пользователь"

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(id = ru.dvbstudio.dvcloud.R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(id = ru.dvbstudio.dvcloud.R.string.home_welcome, displayName),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        repository.initApiClient()
                        repository.logout()
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.home_logout))
            }

            OutlinedButton(
                onClick = { showWipeDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.home_wipe))
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Сброс устройства") },
            text = {
                Text(
                    "Все ключи и токен будут удалены с устройства. " +
                        "Потребуется повторная регистрация. Продолжить?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeDialog = false
                    scope.launch {
                        repository.wipeAll()
                        onLogout()
                    }
                }) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.cancel))
                }
            },
        )
    }
}
