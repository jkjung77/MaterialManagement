package kr.baraplt.material.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.baraplt.material.domain.UserRole
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppListCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PasswordField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.TextFieldPlain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    state: AppUiState,
        onProducts: () -> Unit,
        onFinished: () -> Unit,
    onHistory: () -> Unit,
    onStocktake: () -> Unit,
    onSettings: () -> Unit,
    onGuide: () -> Unit,
    onClose: () -> Unit
) {
    AppScreenScaffold(title = "더보기") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MenuRow("단품 기초정보", "단품 등록, BOM(US), 판매단가", onProducts)
            }
            item {
                MenuRow("완제품 · 월계획", "고객사 아이템 구성과 월 생산계획", onFinished)
            }
            item {
                MenuRow("입출고 이력", "입고 / 반출 / 폐기 / 재고조정", onHistory)
            }
            item {
                MenuRow("재고조사", "실사 후 차이 조정 · 승인", onStocktake)
            }
            item {
                MenuRow(
                    "월 마감",
                    if (state.closed) "이 달은 마감됨 · 다음 달 시작재고 확정" else "마감하면 실적 수정이 잠깁니다",
                    onClose
                )
            }
            item {
                MenuRow("설정 · 백업", "권한, 이름, 샘플데이터, 백업/복원", onSettings)
            }
            item {
                MenuRow("사용자 설명서", "공장 입장, 권한, 입고, 서버와 맞추기", onGuide)
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    AppListCard(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthCloseScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    AppScreenScaffold(title = "월 마감", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.month.display(), style = MaterialTheme.typography.headlineMedium)
            Text(
                "마감은 월 시작일에 관리책임자가 확정합니다. 마감 후 실적은 수정하지 않아도 됩니다. 현재고가 다음 달 시작재고가 됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppCard {
                Text(if (state.closed) "상태: 마감됨" else "상태: 진행 중", style = MaterialTheme.typography.titleMedium)
                Text("권한: ${if (state.role == UserRole.MANAGER) "관리책임자" else "담당자"}", style = MaterialTheme.typography.bodyMedium)
            }
            if (!state.closed) {
                PrimaryButton("이 달 마감하기", onClick = { vm.closeMonth(); onBack() }, enabled = state.canClose)
            } else {
                GhostButton("마감 해제", onClick = { vm.reopenMonth() }, enabled = state.canClose)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var workspaceDraft by remember(state.workspaceId) { mutableStateOf(state.workspaceId) }
    var workspacePassword by remember { mutableStateOf("") }
    var workspaceConfirm by remember { mutableStateOf("") }
    var name by remember(state.staffName) { mutableStateOf(state.staffName) }
    var pin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var currentFactoryPassword by remember { mutableStateOf("") }
    var newFactoryPassword by remember { mutableStateOf("") }
    var newFactoryConfirm by remember { mutableStateOf("") }
    var confirmSeed by remember { mutableStateOf(false) }

    val exportXlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = vm.exportXlsx()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                vm.show("엑셀 파일을 저장했습니다")
            }.onFailure { vm.show("엑셀 저장 실패: ${it.message}") }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                vm.show("백업 파일을 저장했습니다")
            }.onFailure { vm.show("백업 실패: ${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                }
                vm.importJson(text)
            }.onFailure { vm.show("복원 실패: ${it.message}") }
        }
    }

    AppScreenScaffold(title = "설정", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionTitle("공장")
                Text(
                    "같은 공장 사람은 같은 ID와 같은 암호를 씁니다. ID만 알면 들어올 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TextFieldPlain(workspaceDraft, { workspaceDraft = it }, "공장 ID")
                Spacer(Modifier.height(8.dp))
                PasswordField(workspacePassword, { workspacePassword = it }, "공장 암호")
                Spacer(Modifier.height(8.dp))
                PasswordField(workspaceConfirm, { workspaceConfirm = it }, "공장 암호 확인 (새 공장일 때)")
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    if (state.serverLinked) "서버와 지금 맞추기" else "서버 미연결 · 공장 재입장 필요"
                ) { vm.syncNow() }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("공장 전환") {
                    vm.joinWorkspace(workspaceDraft, workspacePassword, workspaceConfirm.ifBlank { null }) { err ->
                        if (err == null) {
                            workspacePassword = ""
                            workspaceConfirm = ""
                        }
                        vm.show(err ?: "공장을 전환했습니다")
                    }
                }
            }
            item {
                TextFieldPlain(name, { name = it }, "사용자 이름")
                Spacer(Modifier.height(8.dp))
                PrimaryButton("이름 저장") { vm.setStaffName(name) }
            }
            item { SectionTitle("권한") }
            item {
                Text(
                    "수정 권한: 관리책임자 1명. 실적 입력: 생산·자재·출하 담당자. 기본 PIN 0000",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (state.role == UserRole.MANAGER) {
                    GhostButton("담당자로 전환") { vm.switchToStaff() }
                    Spacer(Modifier.height(8.dp))
                    NumberField(oldPin, { oldPin = it }, "현재 PIN")
                    NumberField(newPin, { newPin = it }, "새 PIN (4~8자리)")
                    GhostButton("PIN 변경") {
                        vm.changePin(oldPin, newPin) { ok ->
                            vm.show(if (ok) "PIN을 변경했습니다" else "PIN을 확인하세요")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "공장 암호 변경. 바꾸면 다른 폰은 새 암호로 다시 입장해야 합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PasswordField(currentFactoryPassword, { currentFactoryPassword = it }, "현재 공장 암호")
                    Spacer(Modifier.height(8.dp))
                    PasswordField(newFactoryPassword, { newFactoryPassword = it }, "새 공장 암호 (4자 이상)")
                    Spacer(Modifier.height(8.dp))
                    PasswordField(newFactoryConfirm, { newFactoryConfirm = it }, "새 공장 암호 확인")
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("공장 암호 변경") {
                        vm.changeWorkspacePassword(
                            currentFactoryPassword,
                            newFactoryPassword,
                            newFactoryConfirm
                        ) { err ->
                            if (err == null) {
                                currentFactoryPassword = ""
                                newFactoryPassword = ""
                                newFactoryConfirm = ""
                            }
                        }
                    }
                } else {
                    NumberField(pin, { pin = it }, "관리책임자 PIN")
                    PrimaryButton("관리책임자로 전환") {
                        vm.switchToManager(pin) { ok ->
                            vm.show(if (ok) "관리책임자로 전환했습니다" else "PIN이 올바르지 않습니다")
                        }
                    }
                }
            }
            item { SectionTitle("백업") }
            item {
                Text("통신이 되면 서버에 맞춥니다. 통신이 약하면 이 폰에 쌓아 두었다가 「서버와 맞추기」를 누르세요. 퇴근 전 JSON 백업도 권장합니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("엑셀로 저장") {
                    val factory = state.workspaceId.ifBlank { "공장" }
                    exportXlsxLauncher.launch("자재관리-$factory-${state.month.value}.xlsx")
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("백업 파일 내보내기") {
                    val factory = state.workspaceId.ifBlank { "공장" }
                    exportLauncher.launch("자재관리-$factory-${state.month.value}.json")
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("백업 파일 가져오기") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
            item { SectionTitle("샘플") }
            item {
                GhostButton("엑셀 샘플 데이터 다시 넣기") { confirmSeed = true }
            }
            item {
                Text("버전 1.1.4 · 서버 동기화 · Material 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmSeed) {
        AlertDialog(
            onDismissRequest = { confirmSeed = false },
            title = { Text("샘플 데이터를 다시 넣을까요?") },
            text = { Text("현재 입력한 내용이 모두 지워지고 APP 엑셀 샘플로 돌아갑니다. 이 폰에만 넣고, 서버에는 바로 올리지 않습니다.") },
            confirmButton = {
                TextButton({
                    confirmSeed = false
                    vm.reloadSample()
                }) { Text("다시 넣기") }
            },
            dismissButton = { TextButton({ confirmSeed = false }) { Text("취소") } }
        )
    }
}
