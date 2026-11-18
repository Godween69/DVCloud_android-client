package ru.dvbstudio.dvcloud.data.api

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP-клиент для DV Cloud API.
 *
 * Каждый метод (login, twoFactorCode, me, logout) имеет свой тип запроса и ответа.
 * Это проще и безопаснее, чем generic-рефлексия.
 */
class ApiClient(context: Context, baseUrl: String) {

    companion object {
        private const val TAG = "ApiClient"
        private val JSON = "application/json".toMediaType()

        // JSON-конфигурация: игнорируем неизвестные поля
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(context))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiUrl = baseUrl.trimEnd('/')

    // ===== Шаг 1 входа: логин + пароль + устройство =====
    suspend fun login(
        login: String,
        password: String,
        devicePubkey: String,
        deviceName: String,
    ): ApiResult<LoginResponse> {
        val request = LoginRequest(
            login = login,
            password = password,
            device = DeviceRequest(name = deviceName, pubkey = devicePubkey),
        )
        val body = json.encodeToString(LoginRequest.serializer(), request)
        val httpRequest = Request.Builder()
            .url("$apiUrl/api/auth/login")
            .post(body.toRequestBody(JSON))
            .build()
        return execute(httpRequest) { responseBody ->
            json.decodeFromString(LoginResponse.serializer(), responseBody)
        }
    }

    // ===== Шаг 2 входа: подтверждение 2FA кодом =====
    suspend fun twoFactorCode(
        ticket: String,
        code: String,
        devicePubkey: String,
        deviceName: String,
    ): ApiResult<TwoFactorResponse> {
        val request = TwoFactorCodeRequest(
            ticket = ticket,
            code = code,
            device = DeviceRequest(name = deviceName, pubkey = devicePubkey),
        )
        val body = json.encodeToString(TwoFactorCodeRequest.serializer(), request)
        val httpRequest = Request.Builder()
            .url("$apiUrl/api/auth/2fa/code")
            .post(body.toRequestBody(JSON))
            .build()
        return execute(httpRequest) { responseBody ->
            json.decodeFromString(TwoFactorResponse.serializer(), responseBody)
        }
    }

    // ===== Проверка валидности токена (GET /api/auth/me) =====
    suspend fun me(): ApiResult<MeResponse> {
        val httpRequest = Request.Builder()
            .url("$apiUrl/api/auth/me")
            .get()
            .build()
        return execute(httpRequest) { responseBody ->
            json.decodeFromString(MeResponse.serializer(), responseBody)
        }
    }

    // ===== Выход (POST /api/auth/logout) =====
    suspend fun logout(): ApiResult<Unit> {
        val httpRequest = Request.Builder()
            .url("$apiUrl/api/auth/logout")
            .post("{}".toRequestBody(JSON))
            .build()
        return execute(httpRequest) { _ -> Unit }
    }

    // ===== Общий исполнитель запросов =====

    /**
     * Выполняет HTTP-запрос и десериализует ответ через переданную функцию.
     * Это позволяет каждому методу указать свой тип без generic-трюков.
     */
    private suspend fun <T> execute(
        request: Request,
        decode: (String) -> T,
    ): ApiResult<T> {
        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            when (response.code) {
                in 200..299 -> {
                    if (responseBody.isBlank()) {
                        @Suppress("UNCHECKED_CAST")
                        ApiResult.Success(Unit as T)
                    } else {
                        ApiResult.Success(decode(responseBody))
                    }
                }
                401 -> ApiResult.Unauthorized
                else -> {
                    val error = try {
                        json.decodeFromString(ApiError.serializer(), responseBody)
                    } catch (e: Exception) {
                        ApiError(response.code, responseBody, null)
                    }
                    ApiResult.Error(error.statusCode, error.message)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Сетевая ошибка", e)
            ApiResult.Network(e)
        } catch (e: Exception) {
            Log.e(TAG, "Неожиданная ошибка", e)
            ApiResult.Network(e)
        }
    }
}
