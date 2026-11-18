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

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var twoFactorCode by mutableStateOf("")
        private set

    private var currentTicket: String? = null
    private var currentMethods: List<String> = emptyList()

    init {
        repository.initApiClient()
    }

    fun updateUsername(value: String) { username = value }
    fun updatePassword(value: String) { password = value }
    fun updateTwoFactorCode(value: String) { twoFactorCode = value }

    fun login() {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error(messageId = ru.dvbstudio.dvcloud.R.string.login_error_empty)
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = repository.login(username, password)) {
                is ApiResult.Success -> handleLoginStep(result.data)
                is ApiResult.Error -> handleError(result.code, result.message)
                is ApiResult.Network -> handleNetworkError(result.exception)
                is ApiResult.Unauthorized -> _state.value = LoginUiState.Error(
                    messageId = ru.dvbstudio.dvcloud.R.string.login_error_invalid,
                )
            }
        }
    }

    fun completeTwoFactor() {
        val ticket = currentTicket
        if (ticket == null) {
            _state.value = LoginUiState.Error(messageId = ru.dvbstudio.dvcloud.R.string.twofactor_error_expired)
            return
        }
        if (twoFactorCode.isBlank()) {
            _state.value = LoginUiState.Error(messageId = ru.dvbstudio.dvcloud.R.string.twofactor_error_empty)
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = repository.completeTwoFactor(ticket, twoFactorCode)) {
                is ApiResult.Success -> {
                    repository.saveRegistration(result.data, ru.dvbstudio.dvcloud.BuildConfig.API_BASE_URL)
                    _state.value = LoginUiState.Success
                }
                is ApiResult.Error -> _state.value = LoginUiState.Error(
                    messageId = if (result.code == 401) ru.dvbstudio.dvcloud.R.string.twofactor_error_invalid
                    else ru.dvbstudio.dvcloud.R.string.login_error_unknown,
                )
                is ApiResult.Network -> handleNetworkError(result.exception)
                is ApiResult.Unauthorized -> _state.value = LoginUiState.Error(
                    messageId = ru.dvbstudio.dvcloud.R.string.twofactor_error_invalid,
                )
            }
        }
    }

    fun clearError() {
        _state.value = if (currentTicket != null) LoginUiState.RequiresTwoFactor(currentMethods)
        else LoginUiState.Idle
    }

    fun backToLogin() {
        currentTicket = null
        currentMethods = emptyList()
        twoFactorCode = ""
        _state.value = LoginUiState.Idle
    }

    private fun handleLoginStep(step: LoginStep) {
        when (step) {
            is LoginStep.Completed -> viewModelScope.launch {
                repository.saveRegistration(step.registration, ru.dvbstudio.dvcloud.BuildConfig.API_BASE_URL)
                _state.value = LoginUiState.Success
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
            401, 429 -> ru.dvbstudio.dvcloud.R.string.login_error_invalid
            in 500..599 -> ru.dvbstudio.dvcloud.R.string.login_error_unknown
            else -> ru.dvbstudio.dvcloud.R.string.login_error_unknown
        }
        _state.value = LoginUiState.Error(messageId = messageId, details = message)
    }

    /**
     * Показываем точный тип исключения в деталях — это ключ к диагностике.
     * Формат: "КлассИсключения: сообщение"
     */
    private fun handleNetworkError(e: Throwable) {
        val details = "${e::class.java.simpleName}: ${e.message ?: "без сообщения"}"
        _state.value = LoginUiState.Error(
            messageId = ru.dvbstudio.dvcloud.R.string.login_error_network,
            details = details,
        )
    }
}

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class Error(val messageId: Int, val details: String? = null) : LoginUiState()
    data class RequiresTwoFactor(val methods: List<String>) : LoginUiState()
    data object Success : LoginUiState()
}
