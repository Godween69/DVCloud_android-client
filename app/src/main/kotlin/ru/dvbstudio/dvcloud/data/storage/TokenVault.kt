package ru.dvbstudio.dvcloud.data.storage

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import ru.dvbstudio.dvcloud.domain.model.DeviceToken
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// DataStore: асинхронное хранилище настроек (замена SharedPreferences).
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dv_cloud_prefs")

// Ключи для DataStore.
private val KEY_ENCRYPTED_TOKEN = stringPreferencesKey("encrypted_token")
private val KEY_TOKEN_IV = stringPreferencesKey("token_iv")
private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
private val KEY_PUBLIC_KEY = stringPreferencesKey("public_key_spki")
private val KEY_SERVER_URL = stringPreferencesKey("server_url")
private val KEY_USER_ID = stringPreferencesKey("user_id")
private val KEY_CREATED_AT = longPreferencesKey("created_at")

// Алиасы ключей в Android Keystore.
private const val ALIAS_SIGNING = "dv_device_signing"    // ECDSA P-256 для подписи запросов
private const val ALIAS_TOKEN = "dv_device_token"        // AES-256-GCM для шифрования токена

/**
 * TokenVault — хранилище токенов устройств на Android Keystore.
 *
 * Два ключа:
 * - Асимметричный (ECDSA P-256): для подписи запросов. Приватный ключ не извлекается.
 * - Симметричный (AES-256-GCM): для шифрования токена в DataStore.
 *
 * Безопасность:
 * - Приватные ключи никогда не покидают Keystore (аппаратная защита при наличии StrongBox).
 * - Токен хранится зашифрованным; plaintext существует только в памяти процесса.
 * - Подпись запросов в DER-формате (совместимо с Node.js crypto на сервере).
 * - No userAuthenticationRequired: автовыгрузка работает в фоне при заблокированном экране.
 */
class TokenVault(private val context: Context) {

    // ===== Управление ключами =====

    // Проверяет наличие ключей и создаёт их, если нет.
    // StrongBox (специализированный чип безопасности) используется при наличии.
    fun ensureKeys() {
        if (!hasSigningKey()) generateSigningKey()
        if (!hasTokenKey()) generateTokenKey()
    }

    private fun hasSigningKey(): Boolean = keystore().containsAlias(ALIAS_SIGNING)
    private fun hasTokenKey(): Boolean = keystore().containsAlias(ALIAS_TOKEN)

    // Генерация пары ECDSA P-256 для подписи запросов.
    private fun generateSigningKey() {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS_SIGNING,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            // StrongBox: аппаратная защита, если чип есть.
            .setIsStrongBoxBacked(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            // Валидность ключа не привязана к разблокировке экрана (иначе фон умрёт).
            .build()

        try {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(spec)
                generateKeyPair()
            }
        } catch (e: StrongBoxUnavailableException) {
            // Fallback: StrongBox недоступен, повторяем без него.
            val fallback = KeyGenParameterSpec.Builder(
                ALIAS_SIGNING,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(fallback)
                generateKeyPair()
            }
        }
    }

    // Генерация симметричного ключа AES-256-GCM для шифрования токена.
    private fun generateTokenKey() {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS_TOKEN,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setIsStrongBoxBacked(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .build()

        try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(spec)
                generateKey()
            }
        } catch (e: StrongBoxUnavailableException) {
            val fallback = KeyGenParameterSpec.Builder(
                ALIAS_TOKEN,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(fallback)
                generateKey()
            }
        }
    }

    // ===== Публичный ключ для регистрации устройства =====

    // Публичный ключ в формате SPKI base64 (для отправки на сервер при регистрации).
    fun getPublicKeySpkiBase64(): String {
        ensureKeys()
        val keyPair = getSigningKeyPair()
        // encoded публичного ключа — это X.509/SPKI DER
        val spkiBytes = keyPair.public.encoded
        return Base64.encodeToString(spkiBytes, Base64.NO_WRAP)
    }

    // ===== Подпись запросов =====

    // Подписывает сообщение приватным ключом ECDSA.
    // Возвращает подпись в DER-формате (совместимо с Node.js crypto.verify).
    fun sign(message: ByteArray): ByteArray {
        ensureKeys()
        val keyPair = getSigningKeyPair()
        return try {
            val signature = Signature.getInstance("SHA256withECDSA", "AndroidKeyStore")
            signature.initSign(keyPair.private)
            signature.update(message)
            signature.sign()
        } catch (e: Exception) {
            Log.e("TokenVault", "Ошибка подписи: ${e.message}", e)
            Log.e("TokenVault", "Алгоритм: SHA256withECDSA, провайдер: AndroidKeyStore")
            Log.e("TokenVault", "Ключ существует: ${keystore().containsAlias(ALIAS_SIGNING)}")
            throw e
        }
    }

    // ===== Шифрование/дешифрование токена =====

    // Шифрует plaintext-токен AES-GCM и сохраняет в DataStore вместе с метаданными.
    suspend fun saveToken(token: DeviceToken) {
        ensureKeys()
        val (ciphertext, iv) = encryptToken(token.token)
        context.dataStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_TOKEN] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            prefs[KEY_TOKEN_IV] = Base64.encodeToString(iv, Base64.NO_WRAP)
            prefs[KEY_DEVICE_NAME] = token.deviceName
            prefs[KEY_PUBLIC_KEY] = token.publicKeySpki
            prefs[KEY_SERVER_URL] = token.serverUrl
            prefs[KEY_USER_ID] = token.userId
            prefs[KEY_CREATED_AT] = token.createdAt
        }
    }

    // Дешифрует токен из DataStore. Возвращает null, если токена нет.
    suspend fun loadToken(): DeviceToken? {
        if (!hasTokenKey()) return null
        val prefs = context.dataStore.data.first()
        val ciphertextStr = prefs[KEY_ENCRYPTED_TOKEN] ?: return null
        val ivStr = prefs[KEY_TOKEN_IV] ?: return null
        val deviceName = prefs[KEY_DEVICE_NAME] ?: return null
        val publicKey = prefs[KEY_PUBLIC_KEY] ?: return null
        val serverUrl = prefs[KEY_SERVER_URL] ?: return null
        val userId = prefs[KEY_USER_ID] ?: return null
        val createdAt = prefs[KEY_CREATED_AT] ?: return null

        val ciphertext = Base64.decode(ciphertextStr, Base64.NO_WRAP)
        val iv = Base64.decode(ivStr, Base64.NO_WRAP)
        val token = decryptToken(ciphertext, iv)
        return DeviceToken(
            token = token,
            deviceName = deviceName,
            publicKeySpki = publicKey,
            serverUrl = serverUrl,
            userId = userId,
            createdAt = createdAt,
        )
    }

    // Flow для реактивного наблюдения за состоянием аутентификации.
    fun authStateFlow() = context.dataStore.data.map { prefs ->
        val ciphertextStr = prefs[KEY_ENCRYPTED_TOKEN]
        if (ciphertextStr == null || !hasTokenKey()) {
            null
        } else {
            try {
                val iv = Base64.decode(prefs[KEY_TOKEN_IV] ?: return@map null, Base64.NO_WRAP)
                val ciphertext = Base64.decode(ciphertextStr, Base64.NO_WRAP)
                DeviceToken(
                    token = decryptToken(ciphertext, iv),
                    deviceName = prefs[KEY_DEVICE_NAME] ?: "",
                    publicKeySpki = prefs[KEY_PUBLIC_KEY] ?: "",
                    serverUrl = prefs[KEY_SERVER_URL] ?: "",
                    userId = prefs[KEY_USER_ID] ?: "",
                    createdAt = prefs[KEY_CREATED_AT] ?: 0L,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    // Удаляет токен и метаданные (выход).
    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ENCRYPTED_TOKEN)
            prefs.remove(KEY_TOKEN_IV)
            prefs.remove(KEY_DEVICE_NAME)
            prefs.remove(KEY_PUBLIC_KEY)
            prefs.remove(KEY_SERVER_URL)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_CREATED_AT)
        }
    }

    // ===== Сброс ключей (используется при утере устройства или компрометации) =====
    suspend fun wipeAll() {
        clearToken()
        keystore().apply {
            if (containsAlias(ALIAS_SIGNING)) deleteEntry(ALIAS_SIGNING)
            if (containsAlias(ALIAS_TOKEN)) deleteEntry(ALIAS_TOKEN)
        }
    }

    // ===== Внутренние методы =====

    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getSigningKeyPair(): KeyPair {
        val ks = keystore()
        val privateKey = ks.getKey(ALIAS_SIGNING, null) as java.security.PrivateKey
        val publicKey = ks.getCertificate(ALIAS_SIGNING).publicKey
        return KeyPair(publicKey, privateKey)
    }

    private fun getTokenKey(): SecretKey {
        return keystore().getKey(ALIAS_TOKEN, null) as SecretKey
    }

    private fun encryptToken(plaintext: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getTokenKey())
        val iv = cipher.iv  // 12 байт для GCM, генерируется автоматически
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return ciphertext to iv
    }

    private fun decryptToken(ciphertext: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getTokenKey(), GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }
}

// Синглтон для простого доступа из любого места приложения.
object TokenVaultProvider {
    @Volatile
    private var instance: TokenVault? = null

    fun get(context: Context): TokenVault =
        instance ?: synchronized(this) {
            instance ?: TokenVault(context.applicationContext).also { instance = it }
        }
}
