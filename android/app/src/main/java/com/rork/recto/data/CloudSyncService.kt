package com.rork.recto.data

import android.content.Context
import com.rork.recto.BuildConfig
import com.rork.recto.ui.screens.DocumentAccent
import com.rork.recto.ui.screens.RectoDocument
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Uploads encrypted document backups and (unlike before) can actually list
 * and download them back down, using the account-level key resolved by
 * [EncryptionKeyRepository] rather than a device-locked one.
 */
class CloudSyncService(private val context: Context) {
    private val client = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upload(session: RectoSession, document: RectoDocument, key: SecretKey): Result<Unit> = runCatching {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) { "Cloud backup is unavailable" }
        val objectPath = "${session.user.id}/${document.id}.recto"
        val encrypted = encrypt(archive(document.pagePaths), key)
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
            setBody(
                Json.encodeToString(
                    SyncDocument.serializer(),
                    SyncDocument(document.id, session.user.id, document.pages, objectPath, document.title),
                ),
            )
        }
        check(metadata.status.isSuccess()) { "Backup metadata could not be synchronized" }
    }

    /** Lists this account's non-deleted cloud documents (metadata only, no blob download). */
    suspend fun listCloudDocuments(session: RectoSession): Result<List<SyncDocument>> = runCatching {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) { "Cloud backup is unavailable" }
        val response = client.get(
            "${BuildConfig.SUPABASE_URL}/rest/v1/documents" +
                "?user_id=eq.${session.user.id}&deleted_at=is.null" +
                "&select=id,title,page_count,encrypted_object_path",
        ) { authHeaders(session) }
        check(response.status.isSuccess()) { "Could not list cloud documents" }
        json.decodeFromString<List<SyncDocument>>(response.body<String>())
    }

    /** Downloads and decrypts one cloud document into local page files, returning a ready-to-use [RectoDocument]. */
    suspend fun download(session: RectoSession, remote: SyncDocument, key: SecretKey): Result<RectoDocument> = runCatching {
        check(remote.encryptedObjectPath.isNotBlank()) { "Backup has no stored object path" }
        val response = client.get(
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/encrypted-documents/${remote.encryptedObjectPath}",
        ) { authHeaders(session) }
        check(response.status.isSuccess()) { "Could not download backup for ${remote.title}" }
        val encrypted: ByteArray = response.body()
        val archiveBytes = decrypt(encrypted, key)
        val directory = File(context.filesDir, "documents/synced/${remote.id}").apply { mkdirs() }
        val pagePaths = unarchive(archiveBytes, directory)
        RectoDocument(
            id = remote.id,
            title = remote.title,
            detail = "RESTORED · ENCRYPTED BACKUP",
            pages = pagePaths.size,
            quality = 90,
            isVerified = false,
            accent = DocumentAccent.NEUTRAL,
            pagePaths = pagePaths,
        )
    }

    /** Marks (or unmarks) a document deleted in the cloud so trash state matches across devices. */
    suspend fun syncDeletion(session: RectoSession, documentId: String, deleted: Boolean): Result<Unit> = runCatching {
        if (BuildConfig.SUPABASE_URL.isBlank()) return@runCatching
        val nowIso = isoNow()
        val response = client.patch(
            "${BuildConfig.SUPABASE_URL}/rest/v1/documents?id=eq.$documentId&user_id=eq.${session.user.id}",
        ) {
            authHeaders(session)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(DeletionPatch.serializer(), DeletionPatch(if (deleted) nowIso else null, nowIso)))
        }
        check(response.status.isSuccess()) { "Could not sync deletion state" }
    }

    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())

    private fun HttpRequestBuilder.authHeaders(session: RectoSession) {
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

    private fun unarchive(bytes: ByteArray, directory: File): List<String> {
        val paths = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(directory, entry.name)
                FileOutputStream(file).use { output -> zip.copyTo(output) }
                paths += file.absolutePath
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return paths.sorted()
    }

    private fun encrypt(bytes: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(bytes)
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext
    }

    private fun decrypt(payload: ByteArray, key: SecretKey): ByteArray {
        val ivSize = payload[0].toInt() and 0xff
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}

@Serializable
data class SyncDocument(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("page_count") val pageCount: Int = 0,
    @SerialName("encrypted_object_path") val encryptedObjectPath: String = "",
    val title: String = "Encrypted Recto document",
)

@Serializable
private data class DeletionPatch(
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("updated_at") val updatedAt: String,
)
