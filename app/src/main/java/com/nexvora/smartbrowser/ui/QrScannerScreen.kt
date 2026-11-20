package com.nexvora.smartbrowser.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** Scans QR codes with CameraX + ZXing (pure Java). The camera permission is requested only when this screen opens. */
@Composable
fun QrScannerScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val found = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Column(Modifier.fillMaxSize()) {
        NxTopBar("Scan QR code", { nav.pop() })
        if (!granted) {
            EmptyState(Icons.Filled.QrCodeScanner, if (asked) "Camera permission needed" else "Requesting camera…", "NexVora only uses the camera while this screen is open, and nothing is recorded or uploaded.") {
                if (asked) GradientButton("Allow camera") { launcher.launch(Manifest.permission.CAMERA) }
            }
        } else {
            val previewView = remember { PreviewView(ctx) }
            DisposableEffect(granted, owner) {
                val future = ProcessCameraProvider.getInstance(ctx)
                var provider: ProcessCameraProvider? = null
                val exec = Executors.newSingleThreadExecutor()
                val reader = MultiFormatReader().apply {
                    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
                }
                future.addListener({
                    runCatching {
                        val p = future.get(); provider = p
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(exec) { proxy ->
                            try {
                                val plane = proxy.planes[0]; val buf = plane.buffer
                                val data = ByteArray(buf.remaining()); buf.get(data)
                                val src = PlanarYUVLuminanceSource(data, plane.rowStride, proxy.height, 0, 0, proxy.width, proxy.height, false)
                                val text = reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
                                if (!text.isNullOrBlank() && found.compareAndSet(false, true)) previewView.post { result = text }
                            } catch (_: NotFoundException) {
                            } catch (_: Exception) {
                            } finally { reader.reset(); proxy.close() }
                        }
                        p.unbindAll()
                        p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                onDispose { runCatching { provider?.unbindAll() }; exec.shutdown() }
            }
            AndroidView({ previewView }, Modifier.fillMaxSize())
        }
    }

    result?.let { text ->
        AlertDialog(onDismissRequest = { result = null; found.set(false) }, title = { Text("QR code found") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { result = null; openInput(app, nav, text) }) { Text("Open") } },
            dismissButton = { Row { TextButton(onClick = { copyText(ctx, text); result = null; found.set(false) }) { Text("Copy") }; TextButton(onClick = { result = null; found.set(false) }) { Text("Scan again") } } })
    }
}
