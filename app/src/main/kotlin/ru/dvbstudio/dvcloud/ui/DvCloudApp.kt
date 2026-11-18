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

@Composable
fun DvCloudApp(
    initialAuthState: AuthState,
    loginViewModel: LoginViewModel = viewModel(),
) {
    var screen by remember {
        mutableStateOf<Screen>(
            when (initialAuthState) {
                is AuthState.Authenticated -> Screen.Home(initialAuthState)
                is AuthState.NotAuthenticated -> Screen.Login
            },
        )
    }

    when (val current = screen) {
        is Screen.Login -> LoginScreen(
            onSuccess = {
                // Получаем актуальное состояние из TokenVault
                // (после dismissSuccess в ViewModel)
                screen = Screen.Home(AuthState.NotAuthenticated)
            },
            onRequiresTwoFactor = {
                screen = Screen.TwoFactor
            },
            viewModel = loginViewModel,
        )
        is Screen.TwoFactor -> TwoFactorScreen(
            onBack = { screen = Screen.Login },
            onSuccess = {
                screen = Screen.Home(AuthState.NotAuthenticated)
            },
            viewModel = loginViewModel,
        )
        is Screen.Home -> HomeScreen(
            onLogout = { screen = Screen.Login },
            authState = initialAuthState,
        )
    }
}

private sealed class Screen {
    data object Login : Screen()
    data object TwoFactor : Screen()
    data class Home(val authState: AuthState) : Screen()
}
