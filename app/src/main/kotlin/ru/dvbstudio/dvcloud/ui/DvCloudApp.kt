package ru.dvbstudio.dvcloud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun DvCloudApp(
    initialAuthState: AuthState,
    loginViewModel: LoginViewModel = viewModel(),
) {
    // Реактивно наблюдаем за состоянием аутентификации
    val authState by loginViewModel.repository.authStateFlow().collectAsState(initial = initialAuthState)

    // Локальное состояние экрана (Login / TwoFactor / Home)
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }

    // При изменении authState обновляем экран
    LaunchedEffect(authState) {
        screen = when (authState) {
            is AuthState.Authenticated -> Screen.Home(authState)
            is AuthState.NotAuthenticated -> Screen.Login
        }
    }

    when (val current = screen) {
        is Screen.Login -> LoginScreen(
            onSuccess = {
                // authState изменится автоматически через Flow
            },
            onRequiresTwoFactor = {
                screen = Screen.TwoFactor
            },
            viewModel = loginViewModel,
        )
        is Screen.TwoFactor -> TwoFactorScreen(
            onBack = { screen = Screen.Login },
            onSuccess = {
                // authState изменится автоматически через Flow
            },
            viewModel = loginViewModel,
        )
        is Screen.Home -> HomeScreen(
            onLogout = {
                // authState изменится автоматически через Flow
            },
            authState = authState,
        )
    }
}

private sealed class Screen {
    data object Login : Screen()
    data object TwoFactor : Screen()
    data class Home(val authState: AuthState) : Screen()
}
