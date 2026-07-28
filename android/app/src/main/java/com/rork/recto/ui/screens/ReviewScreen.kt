package com.rork.recto.ui.screens

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    pages: List<String>,
    onAction: (RectoAction) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("New scan") }
    var selected by remember { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Review pages") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${pages.size} PAGES · DRAG-FREE ORDER CONTROLS", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(pages, key = { _, path -> path }) { index, path ->
                    Surface(onClick = { selected = index }, shape = RoundedCornerShape(16.dp), tonalElevation = if (selected == index) 8.dp else 1.dp) {
                        Column(Modifier.padding(8.dp)) {
                            AsyncImage(model = File(path), contentDescription = "Page ${index + 1}", contentScale = ContentScale.Crop, modifier = Modifier.height(260.dp).fillParentMaxWidth(.72f).clip(RoundedCornerShape(10.dp)))
                            Text("PAGE ${index + 1}", Modifier.padding(8.dp), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { if (selected > 0) { onAction(RectoAction.MovePage(selected, selected - 1)); selected -= 1 } }, enabled = selected > 0) { Icon(Icons.Outlined.ArrowBack, "Move page left") }
                IconButton(onClick = { pages.getOrNull(selected)?.let(::rotateImage) }) { Icon(Icons.Outlined.RotateRight, "Rotate page") }
                IconButton(onClick = { if (selected < pages.lastIndex) { onAction(RectoAction.MovePage(selected, selected + 1)); selected += 1 } }, enabled = selected < pages.lastIndex) { Icon(Icons.Outlined.ArrowForward, "Move page right") }
                IconButton(onClick = { onAction(RectoAction.RemovePage(selected)); selected = (selected - 1).coerceAtLeast(0) }) { Icon(Icons.Outlined.Delete, "Remove page") }
            }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Document name") }, singleLine = true)
                Text("Original color · Auto page size · Quality 98", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onAction(RectoAction.SaveCapture(title)); onSaved() }, enabled = pages.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("SAVE ON DEVICE", fontWeight = FontWeight.Black) }
            }
        }
    }
}

private fun rotateImage(path: String) {
    runCatching {
        val original = BitmapFactory.decodeFile(path) ?: return
        val rotated = android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(90f) }, true)
        FileOutputStream(path).use { rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it) }
        if (rotated !== original) original.recycle()
        rotated.recycle()
    }
}
