package ru.dvbstudio.dvcloud.domain.model

// Доменная модель: токен устройства и метаданные.
// Хранится в DataStore в зашифрованном виде через TokenVault.
data class DeviceToken(
    val token: String,              // plaintext dvc_... (только в памяти, не логируем)
    val deviceName: String,         // модель телефона (для отображения в списке устройств)
    val publicKeySpki: String,      // публичный ключ в формате SPKI base64 (не секрет, отправляется на сервер)
    val serverUrl: String,          // URL сервера, на котором выпущен токен
    val userId: String,             // ID пользователя (из ответа /api/auth/me)
    val createdAt: Long,            // Unix timestamp в миллисекундах
)

// Состояние аутентификации: либо есть токен, либо пользователь не вошёл.
sealed class AuthState {
    data object NotAuthenticated : AuthState()
    data class Authenticated(val deviceToken: DeviceToken) : AuthState()
}

// Результат регистрации устройства: сервер возвращает plaintext-токен один раз.
data class DeviceRegistration(
    val token: String,              // dvc_... — показать один раз, больше не повторяется сервером
    val userId: String,
    val userLogin: String,
    val displayName: String,
)
