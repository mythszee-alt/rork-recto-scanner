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
import javax.crypto.spec.SecretKeySpec

/**
 * Resolves the account-level document encryption key: generated once per
 * account and stored server-side in `encryption_keys` (RLS-protected — only
 * the owning authenticated user can read their own row), so any device
 * signed into the same account can fetch it and decrypt that account's
 * backups. Contrast with the old per-device Android Keystore key, which
 * could never decrypt anything on a second device or after reinstall.
 *
 * A local cache — itself protected by a (non-exportable, device-bound)
 * Keystore key — avoids re-fetching the account key on every launch. The
 * cache is just a performance/offline convenience; the server row is the
 * source of truth.
 */
class EncryptionKeyRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
    }
    private val localCache = LocalKeyCache(context)

    suspend fun resolve(session: RectoSession): Result<SecretKey> = runCatching {
        // The cache is keyed to the signed-in user id so switching accounts
        // on the same device (sign out, sign in as someone else) can never
        // reuse the previous account's key.
        localCache.read(session.user.id)?.let { return@runCatching SecretKeySpec(it, "AES") }

        val existing = fetchExisting(session)
        if (existing != null) {
            localCache.write(session.user.id, existing)
            return@runCatching SecretKeySpec(existing, "AES")
        }

        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        uploadNew(session, generated)
        localCache.write(session.user.id, generated)
        SecretKeySpec(generated, "AES")
    }

    fun clearLocalCache() = localCache.clear()

    private suspend fun fetchExisting(session: RectoSession): ByteArray? {
        val response = client.get(
            "${BuildConfig.SUPABASE_URL}/rest/v1/encryption_keys?user_id=eq.${session.user.id}&select=wrapped_key"
        ) {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
        }
        if (!response.status.isSuccess()) return null
        val text = response.body<String>()
        val rows = runCatching { json.decodeFromString<List<KeyRow>>(text) }.getOrNull().orEmpty()
        val encoded = rows.firstOrNull()?.wrappedKey ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private suspend fun uploadNew(session: RectoSession, rawKey: ByteArray) {
        val response = client.post("${BuildConfig.SUPABASE_URL}/rest/v1/encryption_keys?on_conflict=user_id") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Prefer", "resolution=merge-duplicates")
            setBody(KeyRow(userId = session.user.id, wrappedKey = Base64.encodeToString(rawKey, Base64.NO_WRAP)))
        }
        check(response.status.isSuccess()) { "Could not register the account encryption key" }
    }
}

@Serializable
private data class KeyRow(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("wrapped_key") val wrappedKey: String? = null,
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
