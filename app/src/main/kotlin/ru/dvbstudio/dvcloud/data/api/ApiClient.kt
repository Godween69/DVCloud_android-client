package ru.dvbstudio.dvcloud.data.api

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * DNS-резолвер: принудительно использует IPv4 (на случай если телефон пытается IPv6).
 */
class IPv4Dns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            // Фильтруем только IPv4 адреса
            val ipv4 = addresses.filter { it is Inet4Address }
            if (ipv4.isEmpty()) {
                Log.w("ApiClient", "Нет IPv4 адресов для $hostname, используем все")
                addresses
            } else {
                Log.d("ApiClient", "Используем IPv4 для $hostname: ${ipv4.first().hostAddress}")
                ipv4
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "DNS lookup error for $hostname", e)
            throw e
        }
    }
}

/**
 * HTTP-клиент для DV Cloud API с принудительным IPv4.
 */
class ApiClient(context: Context, baseUrl: String) {

    companion object {
        private const val TAG = "ApiClient"
        private val JSON = "application/json".toMediaType()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(IPv4Dns())  // Принудительно IPv4
        .addInterceptor(AuthInterceptor(context))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            logger = object : HttpLoggingInterceptor.Logger {
                override fun log(message: String) {
                    Log.d("OkHttp", message)
                }
            }
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiUrl = baseUrl.trimEnd('/')

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

    suspend fun me(): ApiResult<MeResponse> {
        val httpRequest = Request.Builder()
            .url("$apiUrl/api/auth/me")
            .get()
            .build()
        return execute(httpRequest) { responseBody ->
            json.decodeFromString(MeResponse.serializer(), responseBody)
        }
    }

    suspend fun logout(): ApiResult<Unit> {
        val httpRequest = Request.Builder()
            .url("$apiUrl/api/auth/logout")
            .post("{}".toRequestBody(JSON))
            .build()
        return execute(httpRequest) { _ -> Unit }
    }

    private suspend fun <T> execute(
        request: Request,
        decode: (String) -> T,
    ): ApiResult<T> {
        return try {
            Log.d(TAG, "Запрос: ${request.method} ${request.url}")
            Log.d(TAG, "Полный URL: ${request.url}")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "Ответ: ${response.code}, длина: ${responseBody.length}")

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
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS-ошибка: ${e.message}")
            ApiResult.Network(e)
        } catch (e: ConnectException) {
            Log.e(TAG, "Ошибка соединения: ${e.message}")
            ApiResult.Network(e)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Таймаут: ${e.message}")
            ApiResult.Network(e)
        } catch (e: SSLException) {
            Log.e(TAG, "SSL-ошибка: ${e.message}")
            ApiResult.Network(e)
        } catch (e: IOException) {
            Log.e(TAG, "Сетевая ошибка: ${e.message}", e)
            ApiResult.Network(e)
        } catch (e: Exception) {
            Log.e(TAG, "Неожиданная ошибка: ${e.message}", e)
            ApiResult.Network(e)
        }
    }
}
