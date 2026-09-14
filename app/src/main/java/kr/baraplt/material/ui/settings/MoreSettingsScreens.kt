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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.TextFieldPlain
import kr.baraplt.material.ui.theme.Card
import kr.baraplt.material.ui.theme.InkMute

@Composable
fun MoreScreen(
    state: AppUiState,
        onProducts: () -> Unit,
        onFinished: () -> Unit,
    onHistory: () -> Unit,
    onStocktake: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("더보기", style = MaterialTheme.typography.headlineMedium) }
        item {
            AppCard(onClick = onProducts) {
                Text("단품 기초정보", style = MaterialTheme.typography.titleMedium)
                Text("단품 등록, BOM(US), 판매단가", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            AppCard(onClick = onFinished) {
                Text("완제품 · 월계획", style = MaterialTheme.typography.titleMedium)
                Text("고객사 아이템 구성과 월 생산계획", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            AppCard(onClick = onHistory) {
                Text("입출고 이력", style = MaterialTheme.typography.titleMedium)
                Text("입고 / 반출 / 폐기 / 재고조정", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            AppCard(onClick = onStocktake) {
                Text("재고조사", style = MaterialTheme.typography.titleMedium)
                Text("실사 후 차이 조정 · 승인", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            AppCard(onClick = onClose) {
                Text("월 마감", style = MaterialTheme.typography.titleMedium)
                Text(if (state.closed) "이 달은 마감됨 · 다음 달 시작재고 확정" else "마감하면 실적 수정이 잠깁니다", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            AppCard(onClick = onSettings) {
                Text("설정 · 백업", style = MaterialTheme.typography.titleMedium)
                Text("권한, 이름, 샘플데이터, 백업/복원", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthCloseScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("월 마감") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Card)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.month.display(), style = MaterialTheme.typography.headlineMedium)
            Text(
                "마감은 월 시작일에 관리책임자가 확정합니다. 마감 후 실적은 수정하지 않아도 됩니다. 현재고가 다음 달 시작재고가 됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMute
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
    var name by remember(state.staffName) { mutableStateOf(state.staffName) }
    var pin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmSeed by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Card)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    color = InkMute
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
                Text("공장 통신이 약하므로 데이터는 이 단말에 저장됩니다. 퇴근 전 백업을 다른 폰/PC로 복사하세요.", style = MaterialTheme.typography.bodyMedium, color = InkMute)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("백업 파일 내보내기") {
                    exportLauncher.launch("자재관리-${state.month.value}.json")
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
                Text("버전 1.0.0 · 오프라인 우선 · 회신 머티리얼", style = MaterialTheme.typography.labelMedium, color = InkMute)
            }
        }
    }

    if (confirmSeed) {
        AlertDialog(
            onDismissRequest = { confirmSeed = false },
            title = { Text("샘플 데이터를 다시 넣을까요?") },
            text = { Text("현재 입력한 내용이 모두 지워지고 APP 엑셀 샘플로 돌아갑니다.") },
            confirmButton = {
                TextButton({
                    confirmSeed = false
                    vm.reloadSample()
                }) { Text("다시 넣기") }
            },
            dismissButton = { TextButton({ confirmSeed = false }) { Text("취소") } },
            containerColor = Card
        )
    }
}
