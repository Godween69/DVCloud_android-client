package ru.dvbstudio.dvcloud.ui.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.dvbstudio.dvcloud.data.api.ApiResult
import ru.dvbstudio.dvcloud.data.repository.AuthRepository
import ru.dvbstudio.dvcloud.data.repository.LoginStep

/**
 * ViewModel экрана входа.
 *
 * Ключевое изменение: поля username, password, twoFactorCode теперь
 * обёрнуты в mutableStateOf, чтобы Compose перерисовывал текстовые поля
 * при каждом изменении значения.
 *
 * Состояния:
 * - Idle — пользователь вводит данные
 * - Loading — идёт запрос к серверу
 * - Error — ошибка (показываем Snackbar)
 * - RequiresTwoFactor — сервер вернул ticket, нужен код
 * - Success — вход прошёл, переходим на главный экран
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // Поля ввода: mutableStateOf для перерисовки Compose при каждом изменении
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var twoFactorCode by mutableStateOf("")
        private set

    // Ticket 2FA (не в UI, просто храним между шагами)
    private var currentTicket: String? = null
    private var currentMethods: List<String> = emptyList()

    init {
        repository.initApiClient()
    }

    fun updateUsername(value: String) {
        username = value
    }

    fun updatePassword(value: String) {
        password = value
    }

    fun updateTwoFactorCode(value: String) {
        twoFactorCode = value
    }

    fun login() {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error(
                messageId = ru.dvbstudio.dvcloud.R.string.login_error_empty,
            )
            return
        }

        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = repository.login(username, password)) {
                is ApiResult.Success -> handleLoginStep(result.data)
                is ApiResult.Error -> handleError(result.code, result.message)
                is ApiResult.Network -> _state.value = LoginUiState.Error(
                    messageId = ru.dvbstudio.dvcloud.R.string.login_error_network,
                )
                is ApiResult.Unauthorized -> _state.value = LoginUiState.Error(
                    messageId = ru.dvbstudio.dvcloud.R.string.login_error_invalid,
                )
            }
        }
    }

    fun completeTwoFactor() {
        val ticket = currentTicket
        if (ticket == null) {
            _state.value = LoginUiState.Error(
                messageId = ru.dvbstudio.dvcloud.R.string.twofactor_error_expired,
            )
            return
        }
        if (twoFactorCode.isBlank()) {
            _state.value = LoginUiState.Error(
                messageId = ru.dvbstudio.dvcloud.R.string.twofactor_error_empty,
            )
            return
        }

        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = repository.completeTwoFactor(ticket, twoFactorCode)) {
                is ApiResult.Success -> {
                    repository.saveRegistration(
                        result.data,
                        ru.dvbstudio.dvcloud.BuildConfig.API_BASE_URL,
                    )
                    _state.value = LoginUiState.Success
                }
                is ApiResult.Error -> {
                    val messageId = if (result.code == 401) {
                        ru.dvbstudio.dvcloud.R.string.twofactor_error_invalid
                    } else if (result.code == 429) {
                        ru.dvbstudio.dvcloud.R.string.login_error_invalid
                    } else {
                        ru.dvbstudio.dvcloud.R.string.login_error_unknown
                    }
                    _state.value = LoginUiState.Error(messageId = messageId)
                }
                is ApiResult.Network -> _state.value = LoginUiState.Error(
                    messageId = ru.dvbstudio.dvcloud.R.string.login_error_network,
                )
                is ApiResult.Unauthorized -> _state.value = LoginUiState.Error(
                    messageId = ru.dvbstudio.dvcloud.R.string.twofactor_error_invalid,
                )
            }
        }
    }

    fun clearError() {
        if (currentTicket != null) {
            _state.value = LoginUiState.RequiresTwoFactor(currentMethods)
        } else {
            _state.value = LoginUiState.Idle
        }
    }

    fun backToLogin() {
        currentTicket = null
        currentMethods = emptyList()
        twoFactorCode = ""
        _state.value = LoginUiState.Idle
    }

    private fun handleLoginStep(step: LoginStep) {
        when (step) {
            is LoginStep.Completed -> {
                viewModelScope.launch {
                    repository.saveRegistration(
                        step.registration,
                        ru.dvbstudio.dvcloud.BuildConfig.API_BASE_URL,
                    )
                    _state.value = LoginUiState.Success
                }
            }
            is LoginStep.RequiresTwoFactor -> {
                currentTicket = step.ticket
                currentMethods = step.methods
                _state.value = LoginUiState.RequiresTwoFactor(step.methods)
            }
        }
    }

    private fun handleError(code: Int, message: String) {
        val messageId = when (code) {
            401 -> ru.dvbstudio.dvcloud.R.string.login_error_invalid
            429 -> ru.dvbstudio.dvcloud.R.string.login_error_invalid
            in 500..599 -> ru.dvbstudio.dvcloud.R.string.login_error_unknown
            else -> ru.dvbstudio.dvcloud.R.string.login_error_unknown
        }
        _state.value = LoginUiState.Error(messageId = messageId, details = message)
    }
}

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class Error(
        val messageId: Int,
        val details: String? = null,
    ) : LoginUiState()
    data class RequiresTwoFactor(
        val methods: List<String>,
    ) : LoginUiState()
    data object Success : LoginUiState()
}
