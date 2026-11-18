package ru.dvbstudio.dvcloud.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ===== Публичная модель пользователя (что отдаёт сервер) =====
@Serializable
data class PublicUser(
    val id: String,
    val login: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_admin") val isAdmin: Boolean,
    @SerialName("is_child") val isChild: Boolean,
)

// ===== Запросы входа =====
@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
    val device: DeviceRequest? = null,
)

@Serializable
data class DeviceRequest(
    val name: String,      // модель телефона
    val pubkey: String,    // SPKI base64 из TokenVault
)

// ===== Ответы входа =====
// Сервер возвращает либо {user} (успех), либо {twoFactor} (нужен 2FA).
// kotlinx-serialization не поддерживает union types напрямую, поэтому используем nullable поля.
@Serializable
data class LoginResponse(
    val user: PublicUser? = null,
    val twoFactor: TwoFactorChallenge? = null,
    val deviceToken: String? = null,   // если передан device, сервер вернёт токен
)

@Serializable
data class TwoFactorChallenge(
    val ticket: String,        // одноразовый талон на 5 минут
    val methods: List<String>, // доступные методы: ["chat", "totp", "recovery"]
)

// ===== Запросы 2FA =====
@Serializable
data class TwoFactorCodeRequest(
    val ticket: String,
    val code: String,          // TOTP или запасной код
    val device: DeviceRequest? = null,
)

@Serializable
data class TwoFactorResponse(
    val user: PublicUser,
    val deviceToken: String? = null,
)

// ===== Ответ /api/auth/me =====
@Serializable
data class MeResponse(
    val user: PublicUser,
)

// ===== Список устройств (GET /api/auth/devices) =====
@Serializable
data class DeviceInfo(
    val id: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("created_at") val createdAt: String,    // ISO 8601
    @SerialName("last_seen_at") val lastSeenAt: String, // ISO 8601
    @SerialName("last_ip") val lastIp: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
)

// ===== Ошибки API =====
@Serializable
data class ApiError(
    val statusCode: Int,
    val message: String,
    val error: String? = null,
)

// ===== Результат API-вызова =====
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
    data class Network(val exception: Throwable) : ApiResult<Nothing>()
    data object Unauthorized : ApiResult<Nothing>()  // 401 — токен отозван
}
