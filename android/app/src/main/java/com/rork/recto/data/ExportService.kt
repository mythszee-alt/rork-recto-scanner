package com.rork.recto.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rork.recto.ui.screens.RectoDocument
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    SEARCHABLE_PDF("pdf", "application/pdf"),
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    ZIP("zip", "application/zip")
}

enum class ExportColorMode { COLOR, GRAYSCALE, BLACK_WHITE }

class ExportService(private val context: Context) {
    suspend fun create(
        document: RectoDocument,
        format: ExportFormat,
        quality: Int,
        colorMode: ExportColorMode
    ): Result<File> = runCatching {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = document.title.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Recto_scan" }
        val isMultiPageImage = document.pagePaths.size > 1 && format in setOf(ExportFormat.JPEG, ExportFormat.PNG, ExportFormat.WEBP)
        val output = File(directory, "$safeName.${if (isMultiPageImage) "zip" else format.extension}")
        when (format) {
            ExportFormat.PDF -> createPdf(document.pagePaths, output, colorMode, false)
            ExportFormat.SEARCHABLE_PDF -> createPdf(document.pagePaths, output, colorMode, true)
            ExportFormat.JPEG -> createImageOrZip(document.pagePaths, output, Bitmap.CompressFormat.JPEG, quality, colorMode, "jpg")
            ExportFormat.PNG -> createImageOrZip(document.pagePaths, output, Bitmap.CompressFormat.PNG, 100, colorMode, "png")
            ExportFormat.WEBP -> {
                val webp = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                createImageOrZip(document.pagePaths, output, webp, quality, colorMode, "webp")
            }
            ExportFormat.ZIP -> createZip(document.pagePaths, output, quality, colorMode)
        }
        output
    }

    private suspend fun createPdf(paths: List<String>, output: File, mode: ExportColorMode, searchable: Boolean) {
        val pdf = PdfDocument()
        paths.forEachIndexed { index, path ->
            val source = BitmapFactory.decodeFile(path) ?: error("Page ${index + 1} could not be read")
            val bitmap = applyColorMode(source, mode)
            val pageWidth = 1240
            val pageHeight = 1754
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            val scale = minOf(pageWidth.toFloat() / bitmap.width, pageHeight.toFloat() / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = (pageWidth - width) / 2f
            val top = (pageHeight - height) / 2f
            page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG))
            if (searchable) drawRecognizedText(page.canvas, bitmap, left, top, scale)
            pdf.finishPage(page)
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        }
        FileOutputStream(output).use(pdf::writeTo)
        pdf.close()
    }

    private suspend fun drawRecognizedText(canvas: Canvas, bitmap: Bitmap, left: Float, top: Float, scale: Float) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(1, 0, 0, 0) }
        result?.textBlocks?.flatMap { it.lines }?.flatMap { it.elements }?.forEach { element ->
            val box = element.boundingBox ?: return@forEach
            paint.textSize = (box.height() * scale).coerceAtLeast(6f)
            canvas.drawText(element.text, left + box.left * scale, top + box.bottom * scale, paint)
        }
        recognizer.close()
    }

    private fun createImageOrZip(paths: List<String>, output: File, format: Bitmap.CompressFormat, quality: Int, mode: ExportColorMode, extension: String) {
        if (paths.size == 1) {
            val source = BitmapFactory.decodeFile(paths.first()) ?: error("Page could not be read")
            val bitmap = applyColorMode(source, mode)
            FileOutputStream(output).use { bitmap.compress(format, quality.coerceIn(20, 100), it) }
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        } else {
            createEncodedZip(paths, output, format, quality, mode, extension)
        }
    }

    private fun createZip(paths: List<String>, output: File, quality: Int, mode: ExportColorMode) {
        createEncodedZip(paths, output, Bitmap.CompressFormat.JPEG, quality, mode, "jpg")
    }

    private fun createEncodedZip(paths: List<String>, output: File, format: Bitmap.CompressFormat, quality: Int, mode: ExportColorMode, extension: String) {
        ZipOutputStream(FileOutputStream(output)).use { zip ->
            paths.forEachIndexed { index, path ->
                val source = BitmapFactory.decodeFile(path) ?: return@forEachIndexed
                val bitmap = applyColorMode(source, mode)
                zip.putNextEntry(ZipEntry("page_${(index + 1).toString().padStart(3, '0')}.$extension"))
                bitmap.compress(format, quality.coerceIn(20, 100), zip)
                zip.closeEntry()
                if (bitmap !== source) bitmap.recycle()
                source.recycle()
            }
        }
    }

    private fun applyColorMode(source: Bitmap, mode: ExportColorMode): Bitmap {
        if (mode == ExportColorMode.COLOR) return source
        val target = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            if (mode == ExportColorMode.BLACK_WHITE) postConcat(ColorMatrix(floatArrayOf(2.6f,0f,0f,0f,-200f, 0f,2.6f,0f,0f,-200f, 0f,0f,2.6f,0f,-200f, 0f,0f,0f,1f,0f)))
        }
        Canvas(target).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return target
    }
}
