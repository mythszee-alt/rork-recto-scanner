package com.rork.recto.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies images chosen with Android's system photo picker into the app's own
 * storage, so imported pages behave exactly like camera-captured ones (and
 * keep working after the picker's temporary URI permission expires).
 *
 * The picker used by the UI (`PickMultipleVisualMedia`) grants scoped, one-off
 * access without any storage permission, so nothing extra is requested here.
 */
class ImportService(private val context: Context) {
    fun importImages(uris: List<Uri>): List<String> {
        if (uris.isEmpty()) return emptyList()
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        return uris.mapIndexedNotNull { index, uri ->
            runCatching {
                val file = File(directory, "import_${System.currentTimeMillis()}_$index.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                file.absolutePath
            }.getOrNull()
        }
    }
}
