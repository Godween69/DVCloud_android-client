package ru.dvbstudio.dvcloud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.dvbstudio.dvcloud.domain.model.AuthState
import ru.dvbstudio.dvcloud.ui.auth.LoginScreen
import ru.dvbstudio.dvcloud.ui.auth.LoginViewModel
import ru.dvbstudio.dvcloud.ui.auth.TwoFactorScreen
import ru.dvbstudio.dvcloud.ui.home.HomeScreen

/**
 * Корневой Compose-компонент: переключает экраны в зависимости от состояния аутентификации.
 *
 * Состояния:
 * - NotAuthenticated → LoginScreen
 * - RequiresTwoFactor → TwoFactorScreen
 * - Authenticated → HomeScreen
 *
 * LoginViewModel создаётся один раз и переживает смену экранов,
 * поэтому ticket 2FA сохраняется между переходами.
 */
@Composable
fun DvCloudApp(
    initialAuthState: AuthState,
    loginViewModel: LoginViewModel = viewModel(),
) {
    // Состояние навигации: какое окно сейчас видно
    var screen by remember {
        mutableStateOf<Screen>(
            when (initialAuthState) {
                is AuthState.Authenticated -> Screen.Home
                is AuthState.NotAuthenticated -> Screen.Login
            },
        )
    }

    when (screen) {
        is Screen.Login -> LoginScreen(
            onSuccess = { screen = Screen.Home },
            onRequiresTwoFactor = { ticket, methods ->
                screen = Screen.TwoFactor
            },
            viewModel = loginViewModel,
        )
        is Screen.TwoFactor -> TwoFactorScreen(
            onBack = { screen = Screen.Login },
            onSuccess = { screen = Screen.Home },
            viewModel = loginViewModel,
        )
        is Screen.Home -> HomeScreen(
            onLogout = { screen = Screen.Login },
        )
    }
}

// Возможные экраны приложения.
private sealed class Screen {
    data object Login : Screen()
    data object TwoFactor : Screen()
    data object Home : Screen()
}
