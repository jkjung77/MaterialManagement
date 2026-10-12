package kr.baraplt.material.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel

/**
 * 기초정보 추가·수정 전에 부른다. 권한이 있으면 바로 실행하고,
 * 서버 연결된 담당자면 관리자 암호를 받아 연 다음 실행한다.
 */
@Composable
fun rememberMastersGuard(state: AppUiState, vm: AppViewModel): (() -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("관리자 암호") },
            text = {
                Column {
                    Text(
                        "자재·단품·완제품 추가·수정은 관리자 암호가 필요합니다. 맞으면 1시간 동안 열립니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    PasswordField(password, { password = it; error = null }, "관리자 암호")
                    error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true
                        vm.unlockMasters(password) { err ->
                            busy = false
                            if (err == null) {
                                pending = null
                                action()
                            } else {
                                error = err
                            }
                        }
                    },
                    enabled = !busy && password.isNotBlank()
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, enabled = !busy) { Text("취소") }
            }
        )
    }

    return { action ->
        when {
            state.canEditMasters -> action()
            !state.serverLinked -> vm.show("기초정보는 관리책임자만 수정합니다. 서버에 연결하거나 PIN으로 관리책임자 전환을 하세요")
            else -> {
                password = ""
                error = null
                pending = action
            }
        }
    }
}
