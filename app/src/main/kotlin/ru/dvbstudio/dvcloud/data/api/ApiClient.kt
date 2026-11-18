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
 * Настройка:
 * - Base URL из BuildConfig (можно переопределить для теста)
 * - AuthInterceptor подписывает каждый запрос
 * - HttpLoggingInterceptor логирует запросы/ответы (только в debug)
 * - Таймауты: 30s (для больших файлов)
 *
 * Методы:
 * - login() — шаг 1 входа (пароль)
 * - twoFactorCode() — шаг 2а (TOTP/запасной код)
 * - me() — проверка валидности токена
 * - logout() — выход
 */
class ApiClient(context: Context, baseUrl: String) {

    companion object {
        private const val TAG = "ApiClient"
        private val JSON = "application/json".toMediaType()

        // JSON-конфигурация: игнорируем неизвестные поля, не сериализуем null
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(context))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiUrl = baseUrl.trimEnd('/')

    // ===== Вход =====

    // Шаг 1: пароль. Если включён 2FA, возвращает ticket для шага 2.
    suspend fun login(login: String, password: String, devicePubkey: String, deviceName: String): ApiResult<LoginResponse> {
        val request = LoginRequest(
            login = login,
            password = password,
            device = DeviceRequest(name = deviceName, pubkey = devicePubkey),
        )
        return post("/api/auth/login", request)
    }

    // Шаг 2а: код TOTP или запасной код.
    suspend fun twoFactorCode(ticket: String, code: String, devicePubkey: String, deviceName: String): ApiResult<TwoFactorResponse> {
        val request = TwoFactorCodeRequest(
            ticket = ticket,
            code = code,
            device = DeviceRequest(name = deviceName, pubkey = devicePubkey),
        )
        return post("/api/auth/2fa/code", request)
    }

    // Проверка валидности токена (GET /api/auth/me).
    suspend fun me(): ApiResult<MeResponse> {
        return get("/api/auth/me")
    }

    // Выход (POST /api/auth/logout).
    suspend fun logout(): ApiResult<Unit> {
        return post("/api/auth/logout", Unit)
    }

    // ===== HTTP-методы =====

    private suspend fun <T : Any> get(path: String): ApiResult<T> {
        val request = Request.Builder()
            .url("$apiUrl$path")
            .get()
            .build()
        return execute(request)
    }

    private suspend fun <Req : Any, T : Any> post(path: String, body: Req): ApiResult<T> {
        val jsonBody = if (body == Unit) "" else json.encodeToString(serializerFor(body), body)
        val request = Request.Builder()
            .url("$apiUrl$path")
            .post(jsonBody.toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private suspend fun <T : Any> execute(request: Request): ApiResult<T> {
        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            when (response.code) {
                in 200..299 -> {
                    if (responseBody.isBlank()) {
                        @Suppress("UNCHECKED_CAST")
                        ApiResult.Success(Unit as T)
                    } else {
                        val data = json.decodeFromString(deserializerFor<T>(), responseBody)
                        ApiResult.Success(data)
                    }
                }
                401 -> ApiResult.Unauthorized
                else -> {
                    val error = try {
                        json.decodeFromString<ApiError>(responseBody)
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

    // Вспомогательные функции для сериализации (Kotlin reified не работает с inline)
    private fun <T : Any> serializerFor(value: T): kotlinx.serialization.KSerializer<T> {
        @Suppress("UNCHECKED_CAST")
        return when (value) {
            is LoginRequest -> LoginRequest.serializer() as kotlinx.serialization.KSerializer<T>
            is TwoFactorCodeRequest -> TwoFactorCodeRequest.serializer() as kotlinx.serialization.KSerializer<T>
            is Unit -> Unit.serializer() as kotlinx.serialization.KSerializer<T>
            else -> throw IllegalArgumentException("Неизвестный тип: ${value::class}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> deserializerFor(): kotlinx.serialization.KSerializer<T> {
        return when (T::class) {
            LoginResponse::class -> LoginResponse.serializer() as kotlinx.serialization.KSerializer<T>
            TwoFactorResponse::class -> TwoFactorResponse.serializer() as kotlinx.serialization.KSerializer<T>
            MeResponse::class -> MeResponse.serializer() as kotlinx.serialization.KSerializer<T>
            Unit::class -> Unit.serializer() as kotlinx.serialization.KSerializer<T>
            else -> throw IllegalArgumentException("Неизвестный тип: ${T::class}")
        }
    }
}
