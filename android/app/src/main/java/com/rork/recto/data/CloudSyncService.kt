package com.rork.recto.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rork.recto.BuildConfig
import com.rork.recto.ui.screens.RectoDocument
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CloudSyncService(private val context: Context) {
    private val client = HttpClient(Android)

    suspend fun upload(session: RectoSession, document: RectoDocument): Result<Unit> = runCatching {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) { "Cloud backup is unavailable" }
        val objectPath = "${session.user.id}/${document.id}.recto"
        val encrypted = encrypt(archive(document.pagePaths))
        val upload = client.put("${BuildConfig.SUPABASE_URL}/storage/v1/object/encrypted-documents/$objectPath") {
            authHeaders(session)
            header(HttpHeaders.ContentType, ContentType.Application.OctetStream)
            header("x-upsert", "true")
            setBody(encrypted)
        }
        check(upload.status.isSuccess()) { "Encrypted backup upload failed" }
        val metadata = client.post("${BuildConfig.SUPABASE_URL}/rest/v1/documents?on_conflict=id") {
            authHeaders(session)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("Prefer", "resolution=merge-duplicates")
            setBody(Json.encodeToString(SyncDocument.serializer(), SyncDocument(document.id, session.user.id, document.pages, objectPath)))
        }
        check(metadata.status.isSuccess()) { "Backup metadata could not be synchronized" }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(session: RectoSession) {
        header("apikey", BuildConfig.SUPABASE_ANON_KEY)
        header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
    }

    private fun archive(paths: List<String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            paths.forEachIndexed { index, path ->
                zip.putNextEntry(ZipEntry("page_${index + 1}.jpg"))
                File(path).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun encrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val ciphertext = cipher.doFinal(bytes)
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext
    }

    private fun encryptionKey(): SecretKey {
        val alias = "recto-document-backup-key"
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

@Serializable
private data class SyncDocument(
    val id: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("page_count") val pageCount: Int,
    @kotlinx.serialization.SerialName("encrypted_object_path") val encryptedObjectPath: String,
    val title: String = "Encrypted Recto document"
)
