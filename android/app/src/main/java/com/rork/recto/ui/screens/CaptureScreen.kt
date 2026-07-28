package com.rork.recto.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.rork.recto.ui.theme.CalibrationCyan
import com.rork.recto.ui.theme.RegistrationMagenta
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun CaptureScreen(
    navController: NavController,
    capturedCount: Int,
    onPageCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var sharpness by remember { mutableIntStateOf(0) }
    var glare by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    if (!hasPermission) {
        CameraPermissionScreen(
            onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = navController::popBackStack,
            modifier = modifier
        )
        return
    }

    LaunchedEffect(hasPermission) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { useCase ->
            useCase.setAnalyzer(executor) { image ->
                val buffer = image.planes.first().buffer
                val step = 16
                var previous = 0
                var differences = 0L
                var samples = 0
                var bright = 0
                var index = buffer.position()
                while (index < buffer.limit()) {
                    val value = buffer.get(index).toInt() and 0xff
                    if (samples > 0) differences += kotlin.math.abs(value - previous)
                    if (value > 245) bright += 1
                    previous = value
                    samples += 1
                    index += step
                }
                if (samples > 1) {
                    sharpness = ((differences / (samples - 1)).coerceIn(0, 30) * 100 / 30).toInt()
                    glare = (bright * 100 / samples).coerceIn(0, 100)
                }
                image.close()
            }
        }
        runCatching {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
            imageCapture = capture
        }.onFailure { error = "Camera could not start. Check camera availability and try again." }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val factory = SurfaceOrientedMeteringPointFactory(view.width.toFloat(), view.height.toFloat())
                        val point = factory.createPoint(event.x, event.y)
                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                        )
                    }
                    true
                }
            }
        )
        RegistrationReticle(isReady = sharpness >= 35 && glare <= 12, Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 150.dp))
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = navController::popBackStack, modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)) { Icon(Icons.Rounded.Close, "Close camera", tint = Color.White) }
            IconButton(onClick = {
                isTorchOn = !isTorchOn
                camera?.cameraControl?.enableTorch(isTorchOn)
            }, modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)) { Icon(if (isTorchOn) Icons.Rounded.Bolt else Icons.Rounded.FlashOff, "Toggle flash", tint = if (isTorchOn) CalibrationCyan else Color.White) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .72f)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric("DETAIL", if (sharpness >= 35) "GOOD" else "HOLD STEADY", sharpness >= 35)
                Metric("GLARE", if (glare <= 12) "CLEAR" else "TILT PAGE", glare <= 12)
                Metric("PAGES", capturedCount.toString(), true)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (capturedCount > 0) navController.navigate("review") }, enabled = capturedCount > 0) { Text("REVIEW $capturedCount") }
                Surface(
                    onClick = {
                        val capture = imageCapture ?: return@Surface
                        val directory = File(context.filesDir, "documents").apply { mkdirs() }
                        val file = File(directory, "page_${System.currentTimeMillis()}.jpg")
                        isCapturing = true
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) { isCapturing = false; onPageCaptured(file.absolutePath) }
                                override fun onError(exception: ImageCaptureException) { isCapturing = false; error = "Capture failed. Keep the app open and try again." }
                            }
                        )
                    },
                    enabled = !isCapturing,
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(76.dp)
                ) { Box(contentAlignment = Alignment.Center) { Surface(shape = CircleShape, color = if (sharpness >= 35 && glare <= 12) CalibrationCyan else RegistrationMagenta, modifier = Modifier.size(60.dp)) {} } }
                Text("AUTO\nOFF", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, good: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = if (good) CalibrationCyan else RegistrationMagenta, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RegistrationReticle(isReady: Boolean, modifier: Modifier = Modifier) {
    val color = if (isReady) CalibrationCyan else RegistrationMagenta
    Canvas(modifier) {
        val length = 54.dp.toPx()
        val stroke = 3.dp.toPx()
        val corners = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
        corners.forEachIndexed { index, point ->
            val xDirection = if (index % 2 == 0) 1f else -1f
            val yDirection = if (index < 2) 1f else -1f
            drawLine(color, point, Offset(point.x + xDirection * length, point.y), stroke, StrokeCap.Square)
            drawLine(color, point, Offset(point.x, point.y + yDirection * length), stroke, StrokeCap.Square)
        }
        drawRect(color.copy(alpha = .25f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun CameraPermissionScreen(onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Camera access is required to scan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Recto only uses the camera while the scanner is open. Existing files can still be imported through Android's system picker.", modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = onRetry) { Text("ALLOW CAMERA") }
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
    }
}
