package kr.baraplt.material.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kr.baraplt.material.domain.AimBox
import kr.baraplt.material.domain.AutoConfirm
import kr.baraplt.material.domain.BarcodeAim
import kr.baraplt.material.domain.SeenBarcode

private const val PREFS = "barcode_scan"
private const val KEY_MANUAL = "manual"
private const val TARGET_HOLD_MS = 800L

@Composable
fun rememberBarcodeScan(onError: (String) -> Unit = {}, onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    var open by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) open = true else error("바코드를 읽으려면 카메라 권한을 허용해 주세요")
    }
    if (open) {
        BarcodeScannerDialog(
            onDismiss = { open = false },
            onResult = {
                open = false
                result(it)
            }
        )
    }
    return remember(context, permission) {
        {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) open = true else permission.launch(Manifest.permission.CAMERA)
        }
    }
}

/** 분석 프레임 하나에서 찾은 바코드(세운 방향 프레임 좌표)와 그 프레임 크기. */
private class FrameCodes(val codes: List<SeenBarcode>, val width: Int, val height: Int)

private class AimAnalyzer(
    private val scanner: BarcodeScanner,
    private val onFrame: (FrameCodes) -> Unit
) : ImageAnalysis.Analyzer {
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val upright = rotation % 180 == 0
        val width = if (upright) proxy.width else proxy.height
        val height = if (upright) proxy.height else proxy.width
        scanner.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { barcodes ->
                val codes = barcodes.mapNotNull { code ->
                    val raw = code.rawValue?.trim().orEmpty()
                    val box = code.boundingBox
                    if (raw.isEmpty() || box == null) null
                    else SeenBarcode(raw, AimBox(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()))
                }
                onFrame(FrameCodes(codes, width, height))
            }
            .addOnCompleteListener { proxy.close() }
    }
}

@Composable
private fun BarcodeScannerDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var manual by remember { mutableStateOf(prefs.getBoolean(KEY_MANUAL, false)) }
    var aim by remember { mutableStateOf<AimBox?>(null) }
    var target by remember { mutableStateOf<SeenBarcode?>(null) }
    var targetAt by remember { mutableLongStateOf(0L) }
    var hint by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onResult)
    val confirm = remember { AutoConfirm() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    fun deliver(value: String) {
        if (done) return
        done = true
        finish(value)
    }

    fun readTarget() {
        val current = target
        if (current != null && System.currentTimeMillis() - targetAt < TARGET_HOLD_MS) deliver(current.value)
        else hint = "네모 가운데 십자에 바코드를 맞춰 주세요"
    }

    fun onFrame(frame: FrameCodes) {
        val box = aim ?: return
        if (done) return
        val seen = frame.codes.map {
            it.copy(box = BarcodeAim.toView(it.box, frame.width, frame.height, previewView.width, previewView.height))
        }
        val picked = BarcodeAim.pick(seen, box)
        val now = System.currentTimeMillis()
        if (picked != null) {
            target = picked
            targetAt = now
            hint = null
        } else if (now - targetAt >= TARGET_HOLD_MS) {
            target = null
        }
        if (!manual) confirm.feed(picked?.value)?.let(::deliver)
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val aspect = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val controller = LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            previewResolutionSelector = ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build()
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspect)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            setImageAnalysisAnalyzer(executor, AimAnalyzer(scanner) { onFrame(it) })
        }
        previewView.controller = controller
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            previewView.controller = null
            scanner.close()
            executor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        val w = size.width * 0.84f
                        val h = minOf(size.width * 0.42f, size.height * 0.3f)
                        val left = (size.width - w) / 2f
                        val top = (size.height - h) / 2f
                        aim = AimBox(left, top, left + w, top + h)
                    }
                    .pointerInput(Unit) { detectTapGestures { if (manual) readTarget() } }
            ) {
                val box = aim ?: return@Canvas
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset.Zero, GeoSize(size.width, box.top))
                drawRect(dim, Offset(0f, box.bottom), GeoSize(size.width, size.height - box.bottom))
                drawRect(dim, Offset(0f, box.top), GeoSize(box.left, box.bottom - box.top))
                drawRect(dim, Offset(box.right, box.top), GeoSize(size.width - box.right, box.bottom - box.top))
                drawRect(
                    Color.White,
                    Offset(box.left, box.top),
                    GeoSize(box.right - box.left, box.bottom - box.top),
                    style = Stroke(width = 3.dp.toPx())
                )
                if (manual) {
                    val arm = 18.dp.toPx()
                    val stroke = 2.dp.toPx()
                    drawLine(Color.Red, Offset(box.centerX - arm, box.centerY), Offset(box.centerX + arm, box.centerY), stroke)
                    drawLine(Color.Red, Offset(box.centerX, box.centerY - arm), Offset(box.centerX, box.centerY + arm), stroke)
                }
                target?.box?.let { t ->
                    drawRect(
                        Color(0xFF4CAF50),
                        Offset(t.left, t.top),
                        GeoSize(t.right - t.left, t.bottom - t.top),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                }
                Text("바코드 스캔", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !manual,
                            onClick = {
                                manual = false
                                prefs.edit().putBoolean(KEY_MANUAL, false).apply()
                            },
                            label = { Text("자동") }
                        )
                        FilterChip(
                            selected = manual,
                            onClick = {
                                manual = true
                                prefs.edit().putBoolean(KEY_MANUAL, true).apply()
                            },
                            label = { Text("수동 (터치해서 읽기)") }
                        )
                    }
                    val shown = target?.value
                    Text(
                        when {
                            hint != null -> hint.orEmpty()
                            manual && shown != null -> "읽을 바코드: $shown"
                            manual -> "십자에 바코드를 맞추고 화면이나 [읽기]를 누르세요"
                            else -> "네모 안에 바코드를 비추면 바로 읽습니다"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (manual) {
                        Button(onClick = { readTarget() }, modifier = Modifier.fillMaxWidth()) {
                            Text("읽기")
                        }
                    }
                }
            }
        }
    }
}
