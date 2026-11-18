package ru.dvbstudio.dvcloud.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.dvbstudio.dvcloud.data.api.ApiResult
import ru.dvbstudio.dvcloud.data.repository.AuthRepository
import ru.dvbstudio.dvcloud.data.repository.LoginStep
import ru.dvbstudio.dvcloud.domain.model.DeviceRegistration

/**
 * ViewModel экрана входа.
 *
 * Состояние:
 * - Idle — пользователь вводит данные
 * - Loading — идёт запрос к серверу
 * - Error — ошибка (показываем сообщение)
 * - RequiresTwoFactor — сервер вернул ticket, нужен код
 * - Success — вход прошёл, переходим на главный экран
 *
 * ViewModel переживает смену конфигурации (поворот экрана),
 * поэтому сетевой запрос не теряется.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // Введённые данные (сохраняются при смене конфигурации)
    var username = ""
        private set
    var password = ""
        private set
    var twoFactorCode = ""
        private set

    // Текущий ticket 2FA (передаётся между экранами)
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

    // Шаг 1: проверка пароля.
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

    // Шаг 2: подтверждение 2FA кодом.
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
                        // Лимит попыток
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

    // Сброс состояния после показа ошибки.
    fun clearError() {
        if (currentTicket != null) {
            _state.value = LoginUiState.RequiresTwoFactor(currentMethods)
        } else {
            _state.value = LoginUiState.Idle
        }
    }

    // Возврат с экрана 2FA к экрану логина.
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
            429 -> ru.dvbstudio.dvcloud.R.string.login_error_invalid  // too many attempts
            in 500..599 -> ru.dvbstudio.dvcloud.R.string.login_error_unknown
            else -> ru.dvbstudio.dvcloud.R.string.login_error_unknown
        }
        _state.value = LoginUiState.Error(messageId = messageId, details = message)
    }
}

// Состояние UI экрана входа.
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
