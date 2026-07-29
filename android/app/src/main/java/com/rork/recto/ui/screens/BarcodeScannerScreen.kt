package com.rork.recto.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rork.recto.ui.theme.CalibrationCyan
import com.rork.recto.ui.theme.RegistrationMagenta
import java.util.concurrent.Executors

data class ScannedCode(val value: String, val typeLabel: String, val isUrl: Boolean)

@Composable
fun BarcodeScannerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var scanned by remember { mutableStateOf<ScannedCode?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember { BarcodeScanning.getClient() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    // Bound once; the analyzer ignores frames while a result is on screen, so
    // "SCAN NEXT" just clears `scanned` rather than rebinding the camera.
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(executor) { imageProxy ->
                    processFrame(imageProxy, scanner) { barcode ->
                        if (scanned == null) scanned = barcode
                    }
                }
            }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
    }

    if (!hasPermission) {
        Column(
            modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera access is required to scan codes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(
                "Recto only uses the camera while the scanner is open.",
                Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("ALLOW CAMERA") }
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 10.dp)) { Text("BACK") }
        }
        return
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Box(
            Modifier.align(Alignment.Center).size(260.dp)
                .border(3.dp, if (scanned == null) CalibrationCyan else RegistrationMagenta, RoundedCornerShape(22.dp))
        )

        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)) {
                Icon(Icons.Rounded.Close, "Close scanner", tint = Color.White)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .78f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val current = scanned
            if (current == null) {
                Text("POINT AT A QR CODE OR BARCODE", color = CalibrationCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text("Detection runs on-device. Nothing is uploaded.", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(current.typeLabel, color = CalibrationCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(current.value, Modifier.padding(14.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = CalibrationCyan, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            copyText(context, current.value)
                            status = "Copied to the clipboard"
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Icon(Icons.Rounded.ContentCopy, null); Text("  COPY") }
                    if (current.isUrl) {
                        Button(
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.value))) }
                                    .onFailure { status = "No app can open this link" }
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Icon(Icons.Rounded.OpenInNew, null); Text("  OPEN") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scanned = null; status = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Icon(Icons.Rounded.Refresh, null); Text("  SCAN NEXT") }
            }
        }
    }
}

@AndroidXOptIn(ExperimentalGetImage::class)
private fun processFrame(imageProxy: ImageProxy, scanner: BarcodeScanner, onDetected: (ScannedCode) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
            val value = first?.rawValue
            if (first != null && !value.isNullOrBlank()) {
                onDetected(ScannedCode(value = value, typeLabel = labelFor(first), isUrl = first.valueType == Barcode.TYPE_URL))
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}

private fun labelFor(barcode: Barcode): String = when (barcode.valueType) {
    Barcode.TYPE_URL -> "LINK"
    Barcode.TYPE_WIFI -> "WI-FI NETWORK"
    Barcode.TYPE_CONTACT_INFO -> "CONTACT"
    Barcode.TYPE_EMAIL -> "EMAIL"
    Barcode.TYPE_PHONE -> "PHONE"
    Barcode.TYPE_SMS -> "SMS"
    Barcode.TYPE_GEO -> "LOCATION"
    Barcode.TYPE_CALENDAR_EVENT -> "CALENDAR EVENT"
    Barcode.TYPE_DRIVER_LICENSE -> "ID DOCUMENT"
    Barcode.TYPE_ISBN -> "ISBN"
    Barcode.TYPE_PRODUCT -> "PRODUCT CODE"
    else -> "SCANNED CODE"
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Recto scanned code", text))
}
