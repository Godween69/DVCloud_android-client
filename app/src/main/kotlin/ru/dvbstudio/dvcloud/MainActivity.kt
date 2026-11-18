package ru.dvbstudio.dvcloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.dvbstudio.dvcloud.data.storage.TokenVaultProvider
import ru.dvbstudio.dvcloud.domain.model.AuthState
import ru.dvbstudio.dvcloud.ui.DvCloudApp

/**
 * Точка входа приложения.
 *
 * Задачи:
 * - Инициализировать TokenVault (генерация ключей, если их нет)
 * - Загрузить текущее состояние аутентификации
 * - Передать его в DvCloudApp для выбора стартового экрана
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация ключей Keystore (быстрая операция, можно в onCreate)
        TokenVaultProvider.get(this).ensureKeys()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Наблюдаем за состоянием аутентификации из TokenVault
                    val vault = TokenVaultProvider.get(this@MainActivity)
                    val token by vault.authStateFlow().collectAsState(initial = null)

                    val authState: AuthState = if (token != null) {
                        AuthState.Authenticated(token!!)
                    } else {
                        AuthState.NotAuthenticated
                    }

                    DvCloudApp(initialAuthState = authState)
                }
            }
        }
    }
}
