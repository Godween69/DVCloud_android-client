package ru.dvbstudio.dvcloud.data.api

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import ru.dvbstudio.dvcloud.data.storage.TokenVaultProvider
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * OkHttp-интерсептор: подписывает каждый запрос токеном устройства.
 *
 * Поток:
 * 1. Читает токен из TokenVault
 * 2. Строит каноническую строку: METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256(body)
 * 3. Подписывает через TokenVault.sign() (приватный ключ в Keystore)
 * 4. Добавляет заголовки: Authorization, X-DV-Signature, X-DV-Timestamp, X-DV-Nonce
 * 5. Пропускает запрос дальше
 *
 * Если токен отозван (401), триггерит выход (очищает TokenVault).
 */
class AuthInterceptor(private val context: Context) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val vault = TokenVaultProvider.get(context)

        // Если токена нет — пропускаем запрос без подписи (для анонимных эндпоинтов)
        val token = kotlinx.coroutines.runBlocking { vault.loadToken() }
        if (token == null) {
            return chain.proceed(original)
        }

        // Извлекаем тело запроса в строку (для SHA256)
        val bodyString = original.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } ?: ""

        // Каноническая строка для подписи
        val method = original.method
        val path = original.url.encodedPath + (original.url.encodedQuery?.let { "?$it" } ?: "")
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val bodyHash = sha256Hex(bodyString)
        val signatureBase = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"

        // Подпись через Keystore (приватный ключ не покидает чип)
        val signatureBytes = vault.sign(signatureBase.toByteArray(Charsets.UTF_8))
        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)

        // Добавляем заголовки
        val signedRequest = original.newBuilder()
            .header("Authorization", "Bearer ${token.token}")
            .header("X-DV-Signature", signatureBase64)
            .header("X-DV-Timestamp", timestamp)
            .header("X-DV-Nonce", nonce)
            .build()

        val response = chain.proceed(signedRequest)

        // Если 401 — токен отозван, очищаем хранилище
        if (response.code == 401) {
            Log.w(TAG, "Токен отозван (401), очищаю TokenVault")
            kotlinx.coroutines.runBlocking { vault.clearToken() }
            // Возвращаем ошибку в читаемом формате
            return response.newBuilder()
                .body("Токен устройства отозван. Войдите снова.".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        return response
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
