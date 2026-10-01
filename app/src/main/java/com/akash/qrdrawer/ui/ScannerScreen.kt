package com.akash.qrdrawer.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akash.qrdrawer.qr.QrImageDecoder
import android.os.Handler
import android.os.Looper
import android.util.Size
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Live camera scan; hands the first decoded payload to [onScanned] exactly once. */
@Composable
fun ScannerScreen(onScanned: (String) -> Unit, onPickImage: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val delivered = AtomicBoolean(false)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .build()
            val mainHandler = Handler(Looper.getMainLooper())
            analysis.setAnalyzer(executor) { proxy ->
                analyze(proxy, delivered) { payload -> mainHandler.post { currentOnScanned(payload) } }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            delivered.set(true)
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(3.dp, Color.White, RoundedCornerShape(20.dp)),
        )
        Text(
            "Point the camera at a QR code",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 48.dp),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", color = Color.White) }
                OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) { Text("From gallery", color = Color.White) }
            }
        }
    }
}

/** Runs on the analysis thread; reads only the Y plane, which is all a QR decoder needs. */
private fun analyze(proxy: ImageProxy, delivered: AtomicBoolean, onFound: (String) -> Unit) {
    proxy.use {
        if (delivered.get()) return
        val plane = proxy.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining()).also(buffer::get)
        // The last row may be shorter than rowStride; pad so the luminance source can index every row.
        val needed = plane.rowStride * proxy.height
        val data = if (bytes.size >= needed) bytes else bytes.copyOf(needed)
        val payload = QrImageDecoder.decodeLuminance(data, plane.rowStride, proxy.width, proxy.height)
        if (payload != null && delivered.compareAndSet(false, true)) onFound(payload)
    }
}
