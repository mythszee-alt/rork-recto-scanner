package com.rork.recto.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rork.recto.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionKeyRepository(context: Context, private val client: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val localCache = LocalKeyCache(context)

    suspend fun resolve(session: RectoSession, password: String? = null): Result<SecretKey> = runCatching {
        localCache.read(session.user.id)?.let { return@runCatching SecretKeySpec(it, "AES") }

        val row = fetchKeyRow(session)
        if (row != null) {
            val salt = Base64.decode(row.salt ?: error("Missing salt"), Base64.NO_WRAP)
            val kek = deriveKek(password ?: error("Password required to unlock your account for the first time on this device"), salt)
            val unwrapped = unwrap(Base64.decode(row.wrappedKey ?: error("Missing key"), Base64.NO_WRAP), kek)
            localCache.write(session.user.id, unwrapped)
            return@runCatching SecretKeySpec(unwrapped, "AES")
        }

        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kek = deriveKek(password ?: error("Password required to set up your account encryption"), salt)
        val wrapped = wrap(generated, kek)
        
        uploadNew(session, wrapped, salt)
        localCache.write(session.user.id, generated)
        SecretKeySpec(generated, "AES")
    }

    private fun deriveKek(password: String, salt: ByteArray): SecretKey {
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 100000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun wrap(rawKey: ByteArray, kek: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val ciphertext = cipher.doFinal(rawKey)
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext
    }

    private fun unwrap(wrapped: ByteArray, kek: SecretKey): ByteArray {
        val ivSize = wrapped[0].toInt() and 0xff
        val iv = wrapped.copyOfRange(1, 1 + ivSize)
        val ciphertext = wrapped.copyOfRange(1 + ivSize, wrapped.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private suspend fun fetchKeyRow(session: RectoSession): KeyRow? {
        val response = client.get(
            "${BuildConfig.SUPABASE_URL}/rest/v1/encryption_keys?user_id=eq.${session.user.id}&select=wrapped_key,salt"
        ) {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }
        if (!response.status.isSuccess()) return null
        val text = response.body<String>()
        val rows = runCatching { json.decodeFromString<List<KeyRow>>(text) }.getOrNull().orEmpty()
        return rows.firstOrNull()
    }

    private suspend fun uploadNew(session: RectoSession, wrappedKey: ByteArray, salt: ByteArray) {
        val response = client.post("${BuildConfig.SUPABASE_URL}/rest/v1/encryption_keys?on_conflict=user_id") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Prefer", "resolution=merge-duplicates")
            setBody(KeyRow(
                userId = session.user.id, 
                wrappedKey = Base64.encodeToString(wrappedKey, Base64.NO_WRAP),
                salt = Base64.encodeToString(salt, Base64.NO_WRAP)
            ))
        }
        check(response.status.isSuccess()) { "Could not register the account encryption key" }
    }
}

@Serializable
private data class KeyRow(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("wrapped_key") val wrappedKey: String? = null,
    @SerialName("salt") val salt: String? = null
)

private class LocalKeyCache(context: Context) {
    private val preferences = context.getSharedPreferences("recto_account_key_cache", Context.MODE_PRIVATE)
    private val alias = "recto-account-key-cache"

    fun write(userId: String, rawKey: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(rawKey)
        preferences.edit()
            .putString("user_id", userId)
            .putString("value", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(userId: String): ByteArray? = runCatching {
        if (preferences.getString("user_id", null) != userId) return null
        val encrypted = Base64.decode(preferences.getString("value", null) ?: return null, Base64.NO_WRAP)
        val iv = Base64.decode(preferences.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted)
    }.getOrNull()

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
