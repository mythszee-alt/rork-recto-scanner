package com.rork.recto.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.rork.recto.data.ExportColorMode
import com.rork.recto.data.ExportFormat
import com.rork.recto.data.ExportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    document: RectoDocument,
    onAction: (RectoAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { ExportService(context) }
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var colorMode by remember { mutableStateOf(ExportColorMode.COLOR) }
    var quality by remember { mutableFloatStateOf(90f) }
    var isExporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val destination = result.data?.data
        val source = pendingFile
        if (result.resultCode == Activity.RESULT_OK && destination != null && source != null) {
            runCatching { context.contentResolver.openOutputStream(destination)?.use { output -> source.inputStream().use { it.copyTo(output) } } }
                .onSuccess { message = "Saved with Android's file picker" }
                .onFailure { message = "The selected location could not be written" }
        }
        pendingFile = null
    }

    fun generate(onReady: (File) -> Unit) {
        isExporting = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { exporter.create(document, format, quality.toInt(), colorMode) }
            isExporting = false
            result.fold(onSuccess = onReady, onFailure = { message = it.message ?: "Export failed" })
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(document.title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(
                model = document.pagePaths.firstOrNull()?.let(::File),
                contentDescription = "First page of ${document.title}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(18.dp))
            )
            Text("${document.pages} pages · Quality ${document.quality} · Stored on device", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("EXPORT FORMAT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { item -> FilterChip(selected = format == item, onClick = { format = item }, label = { Text(item.name.replace('_', ' ')) }) }
            }
            Text("COLOR", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportColorMode.entries.forEach { item -> FilterChip(selected = colorMode == item, onClick = { colorMode = item }, label = { Text(item.name.replace('_', ' ')) }) }
            }
            Text("Image quality ${quality.toInt()}%")
            Slider(value = quality, onValueChange = { quality = it }, valueRange = 40f..100f, enabled = format !in setOf(ExportFormat.PDF, ExportFormat.SEARCHABLE_PDF, ExportFormat.PNG))
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    generate { file ->
                        pendingFile = file
                        val mime = if (file.extension == "zip") "application/zip" else format.mimeType
                        saveLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = mime; putExtra(Intent.EXTRA_TITLE, file.name); addCategory(Intent.CATEGORY_OPENABLE) })
                    }
                }, enabled = !isExporting, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Download, null); Text("  SAVE") }
                Button(onClick = {
                    generate { file ->
                        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = if (file.extension == "zip") "application/zip" else format.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share ${document.title}"))
                    }
                }, enabled = !isExporting, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Share, null); Text(if (isExporting) "  WORKING" else "  SHARE") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onAction(RectoAction.Duplicate(document.id)); message = "Document duplicated" }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.ContentCopy, null); Text("  DUPLICATE") }
                OutlinedButton(onClick = { onAction(RectoAction.Delete(document.id)); onBack() }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Delete, null); Text("  DELETE") }
            }
        }
    }
}
