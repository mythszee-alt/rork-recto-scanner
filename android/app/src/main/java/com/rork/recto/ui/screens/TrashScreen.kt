package com.rork.recto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rork.recto.ui.theme.RegistrationMagenta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    documents: List<RectoDocument>,
    onAction: (RectoAction) -> Unit,
    onBack: () -> Unit
) {
    var documentToDeleteForever by remember { mutableStateOf<RectoDocument?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Trash is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents) { document ->
                    TrashRow(
                        document = document,
                        onRestore = { onAction(RectoAction.Restore(document.id)) },
                        onDeleteForever = { documentToDeleteForever = document }
                    )
                }
            }
        }
    }

    documentToDeleteForever?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDeleteForever = null },
            title = { Text("Delete Forever?") },
            text = { Text("This will permanently remove \"${doc.title}\" and all its pages from this device and your cloud backup. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(RectoAction.DeleteForever(doc.id))
                        documentToDeleteForever = null
                    },
                ) { Text("DELETE FOREVER", color = RegistrationMagenta, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { documentToDeleteForever = null }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun TrashRow(
    document: RectoDocument,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${document.pages} pages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onRestore) { Icon(Icons.Rounded.RestoreFromTrash, "Restore document") }
                IconButton(onClick = onDeleteForever) { Icon(Icons.Rounded.DeleteForever, "Delete forever", tint = RegistrationMagenta) }
            }
        }
    }
}
