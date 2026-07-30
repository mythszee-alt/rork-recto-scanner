package com.rork.recto.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.rork.recto.ui.theme.AppTheme
import com.rork.recto.ui.theme.CalibrationCyan
import com.rork.recto.ui.theme.Hairline
import com.rork.recto.ui.theme.RegistrationMagenta
import com.rork.recto.ui.theme.SignalGreen

@Composable
fun HomeScreen(
    navController: NavController,
    uiState: RectoUiState,
    onAction: (RectoAction) -> Unit,
    onImportImages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf("ALL", "VERIFIED", "RECENT")
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("capture") },
                containerColor = CalibrationCyan,
                contentColor = Color.Black,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(68.dp)
            ) { Icon(Icons.Rounded.Add, "Scan a document", modifier = Modifier.size(30.dp)) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text("RECTO", style = MaterialTheme.typography.labelLarge, color = CalibrationCyan)
                        Text("Paper, measured.", style = MaterialTheme.typography.headlineLarge)
                    }
                    Row {
                        IconButton(onClick = { navController.navigate("trash") }) { Icon(Icons.Rounded.Delete, "Trash") }
                        IconButton(onClick = { navController.navigate("account") }) { Icon(Icons.Rounded.Person, "Account") }
                    }
                }
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { onAction(RectoAction.SetSearch(it)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    placeholder = { Text("Search local documents") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.height(22.dp))
                if (uiState.isSyncing || uiState.message != null) {
                    SyncBanner(isSyncing = uiState.isSyncing, isError = uiState.isSyncError, message = uiState.message)
                    Spacer(Modifier.height(14.dp))
                }
                ProofCheckHero(uiState.proofScore)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        icon = Icons.Rounded.QrCodeScanner,
                        label = "SCAN CODE",
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("barcode") },
                    )
                    QuickAction(
                        icon = Icons.Rounded.PhotoLibrary,
                        label = "IMPORT",
                        modifier = Modifier.weight(1f),
                        onClick = onImportImages,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEach { filter ->
                        FilterChip(filter, filter == uiState.selectedFilter) { onAction(RectoAction.SetFilter(filter)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("DOCUMENTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudOff, null, tint = SignalGreen, modifier = Modifier.size(15.dp))
                        Text("  ON DEVICE", style = MaterialTheme.typography.labelMedium, color = SignalGreen)
                    }
                }
            }
            items(
                items = uiState.documents.filter { document ->
                    !document.isDeleted && (uiState.selectedFilter != "VERIFIED" || document.isVerified) &&
                        document.title.contains(uiState.searchQuery, ignoreCase = true)
                },
                key = { it.id }
            ) { document ->
                DocumentRow(document, onClick = { navController.navigate("document/${document.id}") })
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = CalibrationCyan, modifier = Modifier.size(20.dp))
            Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SyncBanner(isSyncing: Boolean, isError: Boolean, message: String?) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                isSyncing -> {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Syncing with your account…", style = MaterialTheme.typography.labelMedium)
                }
                isError -> {
                    Icon(Icons.Rounded.CloudOff, null, tint = RegistrationMagenta, modifier = Modifier.size(16.dp))
                    Text("  ${message.orEmpty()}", style = MaterialTheme.typography.labelMedium)
                }
                else -> {
                    Icon(Icons.Rounded.CloudDone, null, tint = SignalGreen, modifier = Modifier.size(16.dp))
                    Text("  ${message.orEmpty()}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ProofCheckHero(score: Int) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Hairline, -90f, 360f, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(CalibrationCyan, -90f, 360f * score / 100f, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(RegistrationMagenta, 4.dp.toPx(), Offset(size.width / 2f, 2.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("READY", style = MaterialTheme.typography.labelMedium, color = SignalGreen)
                }
            }
            Column(Modifier.padding(start = 20.dp)) {
                Text("ProofCheck", style = MaterialTheme.typography.titleLarge)
                Text("Quality gate is calibrated", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("BLUR · GLARE · SKEW · DPI", style = MaterialTheme.typography.labelMedium, color = CalibrationCyan)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (isSelected) CalibrationCyan else MaterialTheme.colorScheme.surfaceVariant, label = "filter")
    Surface(color = color, shape = CircleShape, modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, Modifier.padding(horizontal = 15.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium, color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DocumentRow(document: RectoDocument, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(78.dp).aspectRatio(.76f).background(Color(0xFFE6EAEB), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Description, null, tint = when (document.accent) {
                    DocumentAccent.CYAN -> CalibrationCyan
                    DocumentAccent.MAGENTA -> RegistrationMagenta
                    DocumentAccent.NEUTRAL -> Color(0xFF667277)
                })
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(document.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(document.detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (document.isVerified) Icon(Icons.Rounded.CheckCircle, null, tint = SignalGreen, modifier = Modifier.size(15.dp))
                    Text(" ${document.pages}P  ·  Q${document.quality}", style = MaterialTheme.typography.labelMedium, color = if (document.isVerified) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onClick) { Icon(Icons.Rounded.MoreHoriz, "Open document options") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    val mockDocuments = listOf(
        RectoDocument(
            id = "1",
            title = "Tax Receipt 2026",
            detail = "JUST NOW · ON DEVICE",
            pages = 1,
            quality = 98,
            isVerified = true,
            accent = DocumentAccent.CYAN
        ),
        RectoDocument(
            id = "2",
            title = "Passport Scan",
            detail = "2 DAYS AGO · ENCRYPTED BACKUP",
            pages = 2,
            quality = 92,
            isVerified = false,
            accent = DocumentAccent.NEUTRAL
        )
    )
    AppTheme {
        HomeScreen(
            navController = rememberNavController(),
            uiState = RectoUiState(documents = mockDocuments),
            onAction = {},
            onImportImages = {}
        )
    }
}
