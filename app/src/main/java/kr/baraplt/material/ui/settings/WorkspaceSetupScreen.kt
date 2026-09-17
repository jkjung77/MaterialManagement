package kr.baraplt.material.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.PasswordField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.TextFieldPlain

@Composable
fun WorkspaceSetupScreen(vm: AppViewModel, presetId: String = "") {
    var input by remember(presetId) { mutableStateOf(presetId) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val settingPasswordOnly = presetId.isNotBlank()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (settingPasswordOnly) "공장 암호" else "공장 입장", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "공장 ID와 암호로 입장합니다. 통신이 되면 서버(material.jayoo.kr)에 같은 공장으로 붙고, 여러 폰의 입고·생산이 맞춰집니다. 통신이 없으면 이 폰에서만 이어갑니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        TextFieldPlain(input, { input = it; error = null }, "공장 ID", enabled = !settingPasswordOnly)
        Spacer(Modifier.height(8.dp))
        PasswordField(password, { password = it; error = null }, "공장 암호 (4자 이상)")
        Spacer(Modifier.height(8.dp))
        PasswordField(confirm, { confirm = it; error = null }, "공장 암호 확인")
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (settingPasswordOnly) "암호 저장 후 시작" else "이 공장으로 시작") {
            if (settingPasswordOnly) {
                vm.setWorkspacePassword(password, confirm) { err -> error = err }
            } else {
                vm.joinWorkspace(input, password, confirm) { err -> error = err }
            }
        }
        if (!settingPasswordOnly) {
            Spacer(Modifier.height(12.dp))
            Text("공장 ID 예", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            listOf("경주1공장", "경주2공장", "성남공장").forEach { sample ->
                GhostButton(sample) {
                    input = sample
                    error = null
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    }
}
