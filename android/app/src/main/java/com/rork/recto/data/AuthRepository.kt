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
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class RectoSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: AuthUser
)

@Serializable
data class AuthUser(val id: String, val email: String? = null)

@Serializable
private data class AuthError(val message: String? = null, @SerialName("error_description") val description: String? = null)

class AuthRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
    }
    private val sessionStore = SecureSessionStore(context)

    fun restoredSession(): RectoSession? = sessionStore.read()?.let {
        runCatching { json.decodeFromString<RectoSession>(it) }.getOrNull()
    }

    suspend fun signUp(email: String, password: String): Result<RectoSession?> = request(
        path = "/auth/v1/signup",
        body = Credentials(email, password)
    )

    suspend fun signIn(email: String, password: String): Result<RectoSession?> = request(
        path = "/auth/v1/token?grant_type=password",
        body = Credentials(email, password)
    )

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return runCatching {
            requireConfigured()
            val response = client.post("${BuildConfig.SUPABASE_URL}/auth/v1/recover") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(ResetRequest(email))
            }
            if (!response.status.isSuccess()) throw IllegalStateException(errorMessage(response))
        }
    }

    fun googleAuthorizeUrl(): String {
        requireConfigured()
        val redirect = java.net.URLEncoder.encode("recto://auth/callback", Charsets.UTF_8.name())
        return "${BuildConfig.SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=$redirect&scopes=email%20profile"
    }

    fun acceptOAuthCallback(fragmentOrQuery: String): Result<RectoSession> = runCatching {
        val values = fragmentOrQuery.removePrefix("?").removePrefix("#").split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], Charsets.UTF_8.name()) else null
        }.toMap()
        values["error_description"]?.let { error(it) }
        values["error"]?.let { error("Google sign-in failed: $it") }
        val accessToken = values["access_token"] ?: error("Google sign-in returned no session. Check the authorized redirect URL and try again.")
        val refreshToken = values["refresh_token"] ?: error("Google sign-in returned no refresh token. Please try again.")
        val userId = decodeUserId(accessToken) ?: error("Unable to read account identity")
        val session = RectoSession(accessToken, refreshToken, user = AuthUser(userId))
        save(session)
        session
    }

    suspend fun requestAccountDeletion(session: RectoSession): Result<Unit> = runCatching {
        requireConfigured()
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val now = System.currentTimeMillis()
        val dueAt = formatter.format(java.util.Date(now + 30L * 24L * 60L * 60L * 1000L))
        val response = client.post("${BuildConfig.SUPABASE_URL}/rest/v1/account_deletion_requests?on_conflict=user_id") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Prefer", "resolution=merge-duplicates")
            setBody(DeletionRequest(session.user.id, dueAt))
        }
        if (!response.status.isSuccess()) throw IllegalStateException(errorMessage(response))
        val profile = client.patch("${BuildConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.${session.user.id}") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(DeletionProfile(deletionDueAt = dueAt, deletionRequestedAt = formatter.format(java.util.Date(now))))
        }
        if (!profile.status.isSuccess()) throw IllegalStateException(errorMessage(profile))
    }

    suspend fun signOut(session: RectoSession?) {
        if (session != null && BuildConfig.SUPABASE_URL.isNotBlank()) {
            runCatching {
                client.post("${BuildConfig.SUPABASE_URL}/auth/v1/logout") {
                    header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                }
            }
        }
        sessionStore.clear()
    }

    private suspend fun request(path: String, body: Credentials): Result<RectoSession?> = runCatching {
        requireConfigured()
        val response = client.post("${BuildConfig.SUPABASE_URL}$path") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) throw IllegalStateException(errorMessage(response))
        val text = response.body<String>()
        if (!text.contains("access_token")) return@runCatching null
        val session = json.decodeFromString<RectoSession>(text)
        save(session)
        session
    }

    private fun save(session: RectoSession) {
        sessionStore.write(json.encodeToString(RectoSession.serializer(), session))
    }

    private suspend fun errorMessage(response: HttpResponse): String {
        val raw = runCatching { response.body<String>() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString<AuthError>(raw) }.getOrNull()
        return when (response.status.value) {
            400 -> parsed?.message ?: parsed?.description ?: "Check your account details and try again"
            401 -> "The email or password is incorrect"
            429 -> "Too many attempts. Wait a moment and try again"
            else -> parsed?.message ?: parsed?.description ?: "Account service is temporarily unavailable"
        }
    }

    private fun requireConfigured() {
        check(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "Account service is still being configured. Please try again shortly."
        }
    }

    private fun decodeUserId(jwt: String): String? {
        val payload = jwt.split('.').getOrNull(1) ?: return null
        val normalized = payload.padEnd((payload.length + 3) / 4 * 4, '=')
        val decoded = String(Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP))
        return Regex("\\\"sub\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(decoded)?.groupValues?.get(1)
    }
}

@Serializable
private data class Credentials(val email: String, val password: String)

@Serializable
private data class ResetRequest(val email: String)

@Serializable
private data class DeletionRequest(@SerialName("user_id") val userId: String, @SerialName("scheduled_for") val scheduledFor: String)

@Serializable
private data class DeletionProfile(
    @SerialName("deletion_due_at") val deletionDueAt: String,
    @SerialName("deletion_requested_at") val deletionRequestedAt: String
)

private class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("recto_secure_session", Context.MODE_PRIVATE)
    private val alias = "recto-session-key"

    fun write(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("value", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String? = runCatching {
        val encrypted = Base64.decode(preferences.getString("value", null) ?: return null, Base64.NO_WRAP)
        val iv = Base64.decode(preferences.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun key(): SecretKey {
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
