package kr.baraplt.material.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.domain.MaterialSnapshot
import kr.baraplt.material.domain.MovementType
import kr.baraplt.material.domain.StockStatus
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.domain.parseNumber
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppListCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.AppSearchField
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.KeyValue
import kr.baraplt.material.ui.components.LockedBanner
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.StatusChip
import kr.baraplt.material.ui.components.TextFieldPlain

@Composable
fun MaterialListScreen(
    state: AppUiState,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean
) {
    var query by remember { mutableStateOf("") }
    var onlyAlert by remember { mutableStateOf(false) }
    val items = state.workspace?.materials.orEmpty()
        .filter { if (!onlyAlert) true else it.status != StockStatus.OK }
        .filter {
            query.isBlank() ||
                it.name.contains(query, true) ||
                it.codeNo.toString().contains(query)
        }
    AppScreenScaffold(
        title = "자재",
        floatingActionButton = {
            if (canAdd) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "추가")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            AppSearchField(query, { query = it }, "자재명 / 번호 검색")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !onlyAlert, onClick = { onlyAlert = false }, label = { Text("전체 ${state.workspace?.materials?.size ?: 0}") })
                FilterChip(selected = onlyAlert, onClick = { onlyAlert = true }, label = { Text("경보") })
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { it.id }) { m ->
                    MaterialRow(m) { onOpen(m.id) }
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(m: MaterialSnapshot, onClick: () -> Unit) {
    AppListCard(
        title = "NO.${m.codeNo}  ${m.name}",
        subtitle = "현재고 ${formatQty(m.current)} ${m.unit} · 단가 ${formatMoney(m.unitPrice)}원",
        onClick = onClick,
        trailing = { StatusChip(m.status) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailScreen(
    state: AppUiState,
    vm: AppViewModel,
    materialId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onInbound: () -> Unit,
    onScrap: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val m = state.workspace?.materials?.firstOrNull { it.id == materialId }
    AppScreenScaffold(title = m?.name ?: "자재", onBack = onBack) { padding ->
        if (m == null) {
            Text("자재를 찾을 수 없습니다", Modifier.padding(padding).padding(16.dp))
            return@AppScreenScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { LockedBanner(state.closed) }
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("NO.${m.codeNo}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Text(m.name, style = MaterialTheme.typography.headlineMedium)
                        }
                        StatusChip(m.status)
                    }
                    Spacer(Modifier.height(12.dp))
                    KeyValue("현재고", "${formatQty(m.current)} ${m.unit}")
                    KeyValue("시작재고", formatQty(m.opening))
                    KeyValue("입고", formatQty(m.inbound))
                    KeyValue("생산투입", formatQty(m.usage))
                    KeyValue("반출", formatQty(m.outbound))
                    KeyValue("폐기", formatQty(m.scrap))
                    KeyValue("조정", formatQty(m.adjust))
                    KeyValue("안전재고", formatQty(m.safetyStock))
                    KeyValue("리드타임", "${m.leadTimeDays}일")
                    KeyValue("단가", "${formatMoney(m.unitPrice)}원 / ${m.unit}")
                    if (m.packUnit.isNotBlank()) KeyValue("포장단위", m.packUnit)
                    KeyValue("입고금액", "${formatMoney(m.purchaseAmount)}원")
                    KeyValue("사용금액", "${formatMoney(m.usageAmount)}원")
                }
            }
            item {
                PrimaryButton("입고", enabled = state.canEditOps, onClick = onInbound)
                Spacer(Modifier.height(8.dp))
                GhostButton("폐기 / 반출", enabled = state.canEditOps, onClick = onScrap)
                Spacer(Modifier.height(8.dp))
                if (state.role.name == "MANAGER") {
                    GhostButton("기초정보 수정", onClick = onEdit)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("자재 삭제") { confirmDelete = true }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 자재를 삭제할까요?") },
            text = { Text("이름이나 단가만 바꾸면 「기초정보 수정」을 쓰면 됩니다. 입고나 단품에 쓰인 자재는 지울 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteMaterial(materialId) { ok -> if (ok) onBack() }
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialEditScreen(
    state: AppUiState,
    vm: AppViewModel,
    existingId: Long?,
    onBack: () -> Unit
) {
    val existing = state.workspace?.materials?.firstOrNull { it.id == existingId }
    var code by remember { mutableStateOf(existing?.codeNo?.toString().orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit ?: "ea") }
    var pack by remember { mutableStateOf(existing?.packUnit.orEmpty()) }
    var price by remember { mutableStateOf(existing?.unitPrice?.let { if (it == 0.0) "" else it.toLong().toString() }.orEmpty()) }
    var safety by remember { mutableStateOf(existing?.safetyStock?.let { if (it == 0.0) "" else formatQty(it) }.orEmpty()) }
    var lead by remember { mutableStateOf(existing?.leadTimeDays?.takeIf { it > 0 }?.toString().orEmpty()) }
    var opening by remember { mutableStateOf(existing?.opening?.let { if (it == 0.0) "" else formatQty(it) }.orEmpty()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(existingId, existing) {
        if (existing == null && existingId == null && code.isEmpty()) {
            code = vm.nextMaterialNo().toString()
        }
    }
    AppScreenScaffold(title = if (existing == null) "자재 등록" else "자재 수정", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { NumberField(code, { code = it }, "자재번호 (1~500)") }
            item { TextFieldPlain(name, { name = it }, "자재명(품목)") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ea", "kg", "m", "roll").forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
            }
            item { TextFieldPlain(pack, { pack = it }, "포장단위 (선택)") }
            item { NumberField(price, { price = it }, "단위당 단가", suffix = "원") }
            item { NumberField(safety, { safety = it }, "안전재고") }
            item { NumberField(lead, { lead = it }, "리드타임", suffix = "일") }
            item { NumberField(opening, { opening = it }, "이달 시작재고") }
            item {
                PrimaryButton("저장", onClick = {
                    val no = parseNumber(code)?.toInt()
                    if (no == null || no !in 1..500) {
                        vm.show("자재번호는 1~500입니다")
                        return@PrimaryButton
                    }
                    if (name.isBlank()) {
                        vm.show("자재명을 입력하세요")
                        return@PrimaryButton
                    }
                    scope.launch {
                        val ok = vm.saveMaterial(
                            MaterialEntity(
                                id = existing?.id ?: 0,
                                codeNo = no,
                                name = name.trim(),
                                unit = unit,
                                packUnit = pack.trim(),
                                unitPrice = parseNumber(price) ?: 0.0,
                                safetyStock = parseNumber(safety) ?: 0.0,
                                leadTimeDays = parseNumber(lead)?.toInt() ?: 0
                            ),
                            opening = parseNumber(opening)
                        )
                        if (ok) onBack()
                    }
                }, enabled = state.role.name == "MANAGER")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementScreen(
    state: AppUiState,
    vm: AppViewModel,
    typeName: String,
    presetMaterialId: Long?,
    onBack: () -> Unit
) {
    val types = if (typeName == "SCRAP") listOf(MovementType.SCRAP, MovementType.OUTBOUND) else listOf(MovementType.INBOUND)
    val materials = state.workspace?.materials.orEmpty()
    var type by remember { mutableStateOf(types.first()) }
    var materialId by remember { mutableStateOf(presetMaterialId) }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(vm.todayInMonth()) }
    LaunchedEffect(presetMaterialId, materials.map { it.id }) {
        if (materialId == null || materials.none { it.id == materialId }) {
            materialId = presetMaterialId?.takeIf { id -> materials.any { it.id == id } }
                ?: materials.firstOrNull()?.id
        }
    }
    val selected = materials.firstOrNull { it.id == materialId }
    AppScreenScaffold(title = if (typeName == "INBOUND") "자재 입고" else "폐기 / 반출", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { LockedBanner(state.closed) }
            if (types.size > 1) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                        }
                    }
                }
            }
            item { SectionTitle("자재 선택") }
            if (materials.isEmpty()) {
                item {
                    Text(
                        "등록된 자재가 없습니다. 자재 탭에서 품목을 먼저 등록하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            items(materials, key = { it.id }) { m ->
                AppCard(onClick = { materialId = m.id }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("NO.${m.codeNo}  ${m.name}", style = MaterialTheme.typography.titleSmall)
                        if (m.id == materialId) Text("선택됨", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (selected != null) {
                item {
                    Text(
                        "선택: NO.${selected.codeNo}  ${selected.name}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item { NumberField(qty, { qty = it }, "수량", suffix = selected?.unit) }
            item { TextFieldPlain(date, { date = it }, "일자 (YYYY-MM-DD)") }
            item { TextFieldPlain(note, { note = it }, "메모") }
            item {
                PrimaryButton("반영", onClick = {
                    val q = parseNumber(qty)
                    val id = materialId
                    when {
                        materials.isEmpty() -> vm.show("먼저 자재를 등록하세요")
                        id == null -> vm.show("자재를 선택하세요")
                        q == null || q == 0.0 -> vm.show("수량을 입력하세요")
                        else -> {
                            vm.addMovement(id, type, q, date, note)
                            onBack()
                        }
                    }
                }, enabled = state.canEditOps)
            }
        }
    }
}
