package com.rork.recto.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.recto.data.OcrResult
import com.rork.recto.data.OcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextExtractScreen(
    document: RectoDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ocr = remember { OcrService() }
    var result by remember { mutableStateOf<OcrResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(document.id) {
        isWorking = true
        error = null
        val outcome = withContext(Dispatchers.Default) { ocr.extractText(document.pagePaths) }
        outcome.fold(
            onSuccess = { result = it },
            onFailure = { error = it.message ?: "Text could not be read from these pages" },
        )
        isWorking = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Extracted text") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val extracted = result
            when {
                isWorking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator()
                        Text("Reading ${document.pages} page${if (document.pages == 1) "" else "s"}…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }

                extracted == null || extracted.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No readable text was found on these pages. Recapture with more light and less blur for a better result.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    Text(
                        "${extracted.characterCount} CHARACTERS · ON-DEVICE OCR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        Text(
                            extracted.fullText,
                            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, extracted.fullText)
                                status = "Text copied to the clipboard"
                            },
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Outlined.ContentCopy, null); Text("  COPY") }
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TITLE, document.title)
                                            putExtra(Intent.EXTRA_TEXT, extracted.fullText)
                                        },
                                        "Share text from ${document.title}",
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Outlined.Share, null); Text("  SHARE") }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Recto extracted text", text))
}
