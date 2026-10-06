package kr.baraplt.material.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun rememberBarcodeScan(onError: (String) -> Unit = {}, onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    return remember(context) {
        {
            GmsBarcodeScanning.getClient(context).startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue?.trim().orEmpty()
                    if (raw.isNotEmpty()) result(raw)
                }
                .addOnFailureListener {
                    error("바코드 스캐너를 준비 중입니다. 잠시 뒤 다시 눌러 주세요")
                }
        }
    }
}
