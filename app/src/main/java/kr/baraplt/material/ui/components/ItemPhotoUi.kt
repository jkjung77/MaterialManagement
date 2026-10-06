package kr.baraplt.material.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.baraplt.material.data.ItemPhotoStore

@Composable
fun photoLeading(
    workspaceId: String,
    kind: String,
    codeNo: Int,
    epoch: Int
): (@Composable () -> Unit)? {
    val context = LocalContext.current
    val exists = remember(workspaceId, kind, codeNo, epoch) {
        workspaceId.isNotBlank() && ItemPhotoStore.forWorkspace(context, workspaceId).exists(kind, codeNo)
    }
    if (!exists) return null
    return { ItemPhotoThumb(workspaceId, kind, codeNo, epoch) }
}

@Composable
fun ItemPhotoThumb(
    workspaceId: String,
    kind: String,
    codeNo: Int,
    epoch: Int,
    size: Dp = 56.dp
) {
    val bitmap = rememberPhotoBitmap(workspaceId, kind, codeNo, epoch, 128)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun ItemPhotoLarge(
    workspaceId: String,
    kind: String,
    codeNo: Int,
    epoch: Int
) {
    val bitmap = rememberPhotoBitmap(workspaceId, kind, codeNo, epoch, 720)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ItemPhotoEditor(
    workspaceId: String,
    kind: String,
    savedCode: Int?,
    epoch: Int,
    pending: ByteArray?,
    removed: Boolean,
    enabled: Boolean,
    onPicked: (ByteArray) -> Unit,
    onRemove: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureFile = remember {
        File(context.cacheDir, "camera").mkdirs()
        File(context.cacheDir, "camera/capture.jpg")
    }
    val captureUri = remember(captureFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                if (!captureFile.isFile) byteArrayOf() else JpegPhoto.compress(captureFile.readBytes())
            }
            if (jpeg.isEmpty()) onError("사진을 읽지 못했습니다") else onPicked(jpeg)
        }
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(captureUri) else onError("카메라 권한이 필요합니다")
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
                JpegPhoto.compress(bytes)
            }
            if (jpeg.isEmpty()) onError("사진을 읽지 못했습니다") else onPicked(jpeg)
        }
    }
    var saved by remember(savedCode, epoch) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(workspaceId, kind, savedCode, epoch) {
        saved = if (workspaceId.isBlank() || savedCode == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                ItemPhotoStore.forWorkspace(context, workspaceId).read(kind, savedCode)
            }
        }
    }
    val display = when {
        removed -> null
        pending != null -> pending
        else -> saved
    }
    var preview by remember(display) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(display) {
        preview = if (display == null) null else withContext(Dispatchers.IO) { JpegPhoto.thumbnail(display, 720) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("사진", style = MaterialTheme.typography.titleMedium)
        if (preview != null) {
            Image(
                bitmap = preview!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("사진이 없습니다. 찍거나 앨범에서 고르면 목록에 작게 보입니다.", style = MaterialTheme.typography.bodyMedium)
        }
        if (enabled) {
            GhostButton("사진 찍기") {
                requestCamera.launch(android.Manifest.permission.CAMERA)
            }
            GhostButton("앨범에서 고르기") {
                pickImage.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            if (display != null) GhostButton("사진 삭제", onClick = onRemove)
        }
    }
}

@Composable
private fun rememberPhotoBitmap(
    workspaceId: String,
    kind: String,
    codeNo: Int,
    epoch: Int,
    maxEdge: Int
): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(workspaceId, kind, codeNo, epoch, maxEdge) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(workspaceId, kind, codeNo, epoch, maxEdge) {
        bitmap = if (workspaceId.isBlank() || codeNo !in 1..500) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val bytes = ItemPhotoStore.forWorkspace(context, workspaceId).read(kind, codeNo) ?: return@withContext null
                JpegPhoto.thumbnail(bytes, maxEdge)
            }
        }
    }
    return bitmap
}
