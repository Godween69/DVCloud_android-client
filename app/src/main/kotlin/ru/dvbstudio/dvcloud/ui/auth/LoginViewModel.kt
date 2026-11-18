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

    // Ошибки полей для отображения под полями ввода
    var usernameError by mutableStateOf<String?>(null)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var codeError by mutableStateOf<String?>(null)
        private set

    private var currentTicket: String? = null
    private var currentMethods: List<String> = emptyList()

    init {
        repository.initApiClient()
    }

    fun updateUsername(value: String) {
        username = value
        if (value.isNotBlank()) usernameError = null
    }

    fun updatePassword(value: String) {
        password = value
        if (value.isNotBlank()) passwordError = null
    }

    fun updateTwoFactorCode(value: String) {
        twoFactorCode = value
        if (value.isNotBlank()) codeError = null
    }

    fun login() {
        // Валидация полей: ошибки показываем под каждым полем
        var hasError = false
        if (username.isBlank()) {
            usernameError = "Заполните логин"
            hasError = true
        }
        if (password.isBlank()) {
            passwordError = "Заполните пароль"
            hasError = true
        }
        if (hasError) return

        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = repository.login(username, password)) {
                is ApiResult.Success -> handleLoginStep(result.data)
                is ApiResult.Error -> handleError(result.code, result.message)
                is ApiResult.Network -> _state.value = LoginUiState.Error(
                    details = "NetworkError: ${result.exception::class.java.simpleName}\n${result.exception.message}",
                )
                is ApiResult.Unauthorized -> {
                    passwordError = "Неверный логин или пароль"
                    _state.value = LoginUiState.Idle
                }
            }
        }
    }

    fun completeTwoFactor() {
        val ticket = currentTicket
        if (ticket == null) {
            codeError = "Сессия истекла, начните вход заново"
            _state.value = LoginUiState.Idle
            return
        }
        if (twoFactorCode.isBlank()) {
            codeError = "Введите код"
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
                    _state.value = LoginUiState.Success(result.data.displayName)
                }
                is ApiResult.Error -> {
                    codeError = when (result.code) {
                        401 -> "Неверный код"
                        429 -> "Слишком много попыток"
                        else -> "Ошибка: ${result.message}"
                    }
                    _state.value = LoginUiState.Idle
                }
                is ApiResult.Network -> _state.value = LoginUiState.Error(
                    details = "NetworkError: ${result.exception::class.java.simpleName}\n${result.exception.message}",
                )
                is ApiResult.Unauthorized -> {
                    codeError = "Неверный код"
                    _state.value = LoginUiState.Idle
                }
            }
        }
    }

    // Сброс сообщения об успехе (после нажатия "Продолжить")
    fun dismissSuccess() {
        _state.value = LoginUiState.Authenticated
    }

    fun clearError() {
        _state.value = if (currentTicket != null) LoginUiState.RequiresTwoFactor(currentMethods)
        else LoginUiState.Idle
    }

    fun backToLogin() {
        currentTicket = null
        currentMethods = emptyList()
        twoFactorCode = ""
        codeError = null
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
                    _state.value = LoginUiState.Success(step.registration.displayName)
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
        when (code) {
            401 -> passwordError = "Неверный логин или пароль"
            429 -> passwordError = "Слишком много попыток. Подождите."
            500 -> {
                // Специальный случай: сервер 0.17.0 вернул user без deviceToken
                if (message.contains("Неожиданный ответ")) {
                    _state.value = LoginUiState.Error(
                        details = "Сервер устарел: не поддерживаются токены устройств. Обратитесь к администратору.",
                    )
                } else {
                    _state.value = LoginUiState.Error(details = "Ошибка $code: $message")
                }
            }
            in 500..599 -> _state.value = LoginUiState.Error(
                details = "Ошибка сервера ($code): $message",
            )
            else -> _state.value = LoginUiState.Error(details = "Ошибка $code: $message")
        }
        if (code == 401 || code == 429) _state.value = LoginUiState.Idle
    }
}

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    // Ошибка общего характера (не относящаяся к конкретному полю)
    data class Error(val details: String) : LoginUiState()
    data class RequiresTwoFactor(val methods: List<String>) : LoginUiState()
    // Успех — показать диалог приветствия
    data class Success(val displayName: String) : LoginUiState()
    // Пользователь уже аутентифицирован (после dismissSuccess)
    data object Authenticated : LoginUiState()
}
