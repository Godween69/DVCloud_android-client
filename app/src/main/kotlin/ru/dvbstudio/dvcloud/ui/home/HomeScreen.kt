package ru.dvbstudio.dvcloud.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import ru.dvbstudio.dvcloud.domain.model.AuthState

/**
 * Главный экран после успешного входа.
 *
 * Показывает:
 * - Карточку с приветствием и статусом авторизации
 * - Информацию об устройстве и сервере
 * - Кнопки выхода и сброса
 */
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    authState: AuthState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthRepository(context) }
    var showWipeDialog by remember { mutableStateOf(false) }

    val displayName = (authState as? AuthState.Authenticated)
        ?.deviceToken?.deviceName
        ?: "Пользователь"
    val serverUrl = (authState as? AuthState.Authenticated)
        ?.deviceToken?.serverUrl
        ?: "неизвестен"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(id = ru.dvbstudio.dvcloud.R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Карточка приветствия
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        id = ru.dvbstudio.dvcloud.R.string.home_welcome,
                        displayName,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(id = ru.dvbstudio.dvcloud.R.string.home_status_authenticated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Информация о статусе
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        id = ru.dvbstudio.dvcloud.R.string.home_status_device,
                        displayName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        id = ru.dvbstudio.dvcloud.R.string.home_status_server,
                        serverUrl,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    repository.initApiClient()
                    repository.logout()
                    onLogout()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.home_logout))
        }

        OutlinedButton(
            onClick = { showWipeDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(id = ru.dvbstudio.dvcloud.R.string.home_wipe))
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
