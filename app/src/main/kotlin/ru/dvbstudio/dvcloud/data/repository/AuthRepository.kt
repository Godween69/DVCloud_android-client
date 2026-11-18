package ru.dvbstudio.dvcloud.data.repository

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.dvbstudio.dvcloud.data.api.ApiClient
import ru.dvbstudio.dvcloud.data.api.ApiResult
import ru.dvbstudio.dvcloud.data.api.LoginResponse
import ru.dvbstudio.dvcloud.data.api.TwoFactorResponse
import ru.dvbstudio.dvcloud.data.storage.TokenVaultProvider
import ru.dvbstudio.dvcloud.domain.model.AuthState
import ru.dvbstudio.dvcloud.domain.model.DeviceRegistration
import ru.dvbstudio.dvcloud.domain.model.DeviceToken

/**
 * Репозиторий аутентификации: связывает API, хранилище и доменные модели.
 *
 * Поток входа:
 * 1. login() — пароль → ticket (если 2FA) или успех
 * 2. completeTwoFactor() — код TOTP → DeviceRegistration
 * 3. saveToken() — шифрование токена в Keystore
 *
 * Поток автовыгрузки:
 * 1. authStateFlow() — реактивное наблюдение за состоянием
 * 2. Если Authenticated — ApiClient автоматически подписывает запросы
 */
class AuthRepository(private val context: Context) {

    private val vault = TokenVaultProvider.get(context)
    private var apiClient: ApiClient? = null

    // Инициализация API-клиента с нужным URL (можно переопределить для теста)
    fun initApiClient(serverUrl: String? = null) {
        val url = serverUrl ?: ru.dvbstudio.dvcloud.BuildConfig.API_BASE_URL
        apiClient = ApiClient(context, url)
    }

    private fun requireClient(): ApiClient = apiClient ?: throw IllegalStateException("ApiClient не инициализирован")

    // ===== Вход =====

    // Шаг 1: пароль. Возвращает либо DeviceRegistration (успех без 2FA), либо ticket (нужен 2FA).
    suspend fun login(login: String, password: String): ApiResult<LoginStep> {
        vault.ensureKeys()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val pubkey = vault.getPublicKeySpkiBase64()

        return when (val result = requireClient().login(login, password, pubkey, deviceName)) {
            is ApiResult.Success -> {
                val response = result.data
                if (response.user != null && response.deviceToken != null) {
                    // Успех без 2FA (или 2FA не включён)
                    val registration = DeviceRegistration(
                        token = response.deviceToken,
                        userId = response.user.id,
                        userLogin = response.user.login,
                        displayName = response.user.displayName,
                    )
                    ApiResult.Success(LoginStep.Completed(registration))
                } else if (response.twoFactor != null) {
                    // Нужен 2FA
                    ApiResult.Success(LoginStep.RequiresTwoFactor(response.twoFactor.ticket, response.twoFactor.methods))
                } else {
                    ApiResult.Error(500, "Неожиданный ответ сервера")
                }
            }
            is ApiResult.Error -> ApiResult.Error(result.code, result.message)
            is ApiResult.Network -> ApiResult.Network(result.exception)
            is ApiResult.Unauthorized -> ApiResult.Unauthorized
        }
    }

    // Шаг 2: код TOTP или запасной код.
    suspend fun completeTwoFactor(ticket: String, code: String): ApiResult<DeviceRegistration> {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val pubkey = vault.getPublicKeySpkiBase64()

        return when (val result = requireClient().twoFactorCode(ticket, code, pubkey, deviceName)) {
            is ApiResult.Success -> {
                val response = result.data
                val token = response.deviceToken ?: return ApiResult.Error(500, "Сервер не вернул токен устройства")
                val registration = DeviceRegistration(
                    token = token,
                    userId = response.user.id,
                    userLogin = response.user.login,
                    displayName = response.user.displayName,
                )
                ApiResult.Success(registration)
            }
            is ApiResult.Error -> ApiResult.Error(result.code, result.message)
            is ApiResult.Network -> ApiResult.Network(result.exception)
            is ApiResult.Unauthorized -> ApiResult.Unauthorized
        }
    }

    // Сохранение токена в хранилище (после успешного входа).
    suspend fun saveRegistration(registration: DeviceRegistration, serverUrl: String) {
        val token = DeviceToken(
            token = registration.token,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            publicKeySpki = vault.getPublicKeySpkiBase64(),
            serverUrl = serverUrl,
            userId = registration.userId,
            createdAt = System.currentTimeMillis(),
        )
        vault.saveToken(token)
    }

    // ===== Состояние =====

    // Flow для реактивного наблюдения за состоянием аутентификации.
    fun authStateFlow(): Flow<AuthState> = vault.authStateFlow().map { token ->
        if (token != null) AuthState.Authenticated(token) else AuthState.NotAuthenticated
    }

    // Проверка валидности токена (запрос /api/auth/me).
    suspend fun verifyToken(): Boolean {
        return when (requireClient().me()) {
            is ApiResult.Success -> true
            else -> false
        }
    }

    // ===== Выход =====

    // Выход: отзыв токена на сервере + очистка хранилища.
    suspend fun logout() {
        try {
            requireClient().logout()
        } catch (e: Exception) {
            // Игнорируем ошибки сети — всё равно очищаем локально
        }
        vault.clearToken()
    }

    // Полный сброс (при компрометации устройства).
    suspend fun wipeAll() {
        vault.wipeAll()
    }
}

// Результат шага входа: либо завершён, либо требует 2FA.
sealed class LoginStep {
    data class Completed(val registration: DeviceRegistration) : LoginStep()
    data class RequiresTwoFactor(val ticket: String, val methods: List<String>) : LoginStep()
}
