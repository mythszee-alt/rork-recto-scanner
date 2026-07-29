package com.rork.recto.data

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class OcrPage(val pageNumber: Int, val text: String)

data class OcrResult(val pages: List<OcrPage>) {
    /** All pages joined, with a separator between them — what Copy/Share sends. */
    val fullText: String
        get() = pages.joinToString("\n\n") { page ->
            if (pages.size > 1) "— Page ${page.pageNumber} —\n${page.text}" else page.text
        }

    val characterCount: Int get() = pages.sumOf { it.text.length }
    val isEmpty: Boolean get() = pages.all { it.text.isBlank() }
}

/**
 * Extracts text from captured page images using ML Kit's on-device Latin text
 * recognizer — no network, nothing leaves the device. The same recognizer
 * already backs the searchable-PDF export in [ExportService].
 */
class OcrService {
    suspend fun extractText(pagePaths: List<String>): Result<OcrResult> = runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val pages = pagePaths.mapIndexed { index, path ->
                val bitmap = BitmapFactory.decodeFile(path)
                    ?: return@mapIndexed OcrPage(index + 1, "")
                val text = suspendCancellableCoroutine { continuation ->
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { continuation.resume(it.text) }
                        .addOnFailureListener { continuation.resume("") }
                }
                bitmap.recycle()
                OcrPage(index + 1, text)
            }
            OcrResult(pages)
        } finally {
            recognizer.close()
        }
    }
}
