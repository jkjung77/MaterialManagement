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
import androidx.compose.material3.OutlinedButton
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
import kr.baraplt.material.data.entity.StockMovementEntity
import kr.baraplt.material.domain.BarcodeLookup
import kr.baraplt.material.domain.MaterialSnapshot
import kr.baraplt.material.ui.components.rememberBarcodeScan
import kr.baraplt.material.domain.MovementType
import kr.baraplt.material.domain.StockStatus
import kr.baraplt.material.domain.ItemMatch
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.domain.formatQtyExact
import kr.baraplt.material.domain.parseNumber
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.ItemPhotoEdit
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppListCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.AppSearchField
import kr.baraplt.material.ui.components.ItemPhotoEditor
import kr.baraplt.material.ui.components.ItemPhotoLarge
import kr.baraplt.material.ui.components.photoLeading
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
    canAdd: Boolean,
    onMessage: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val scan = rememberBarcodeScan(onError = onMessage) { code ->
        val hit = BarcodeLookup.pick(code, state.workspace?.materials.orEmpty(), { it.barcode }, { it.codeNo })
        if (hit != null) onOpen(hit.id) else {
            query = code
            onMessage("바코드 $code 자재를 찾지 못했습니다")
        }
    }
    var onlyAlert by remember { mutableStateOf(false) }
    val items = state.workspace?.materials.orEmpty()
        .filter { if (!onlyAlert) true else it.status != StockStatus.OK }
        .filter {
            ItemMatch.matches(it.codeNo, it.name, query, it.barcode)
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
            AppSearchField(query, { query = it }, "자재명 / 번호 / 바코드", onScan = scan)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !onlyAlert, onClick = { onlyAlert = false }, label = { Text("전체 ${state.workspace?.materials?.size ?: 0}") })
                FilterChip(selected = onlyAlert, onClick = { onlyAlert = true }, label = { Text("경보") })
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { it.id }) { m ->
                    MaterialRow(state.workspaceId, state.photoEpoch, m) { onOpen(m.id) }
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(workspaceId: String, epoch: Int, m: MaterialSnapshot, onClick: () -> Unit) {
    AppListCard(
        title = "NO.${m.codeNo}  ${m.name}",
        subtitle = "현재고 ${formatQty(m.current)} ${m.unit} · 단가 ${formatMoney(m.unitPrice)}원 · 필요 ${formatQty(m.planNeed)}",
        onClick = onClick,
        leading = photoLeading(workspaceId, "material", m.codeNo, epoch),
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
    var editing by remember { mutableStateOf<StockMovementEntity?>(null) }
    var editQty by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<StockMovementEntity?>(null) }
    val m = state.workspace?.materials?.firstOrNull { it.id == materialId }
    val lines = state.workspace?.movements.orEmpty()
        .filter { it.materialId == materialId && it.type in EDITABLE_MOVEMENT_TYPES }
        .sortedWith(compareByDescending<StockMovementEntity> { it.occurredOn }.thenByDescending { it.id })
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
                    ItemPhotoLarge(state.workspaceId, "material", m.codeNo, state.photoEpoch)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("NO.${m.codeNo}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Text(m.name, style = MaterialTheme.typography.headlineMedium)
                        }
                        StatusChip(m.status)
                    }
                    Spacer(Modifier.height(12.dp))
                    KeyValue("현재고", "${formatQty(m.current)} ${m.unit}")
                    KeyValue("계획 필요수량", "${formatQty(m.planNeed)} ${m.unit}")
                    KeyValue("시작재고", formatQty(m.opening))
                    KeyValue("입고", formatQty(m.inbound))
                    KeyValue("생산투입", formatQty(m.usage))
                    KeyValue("반출", formatQty(m.outbound))
                    KeyValue("폐기", formatQty(m.scrap))
                    KeyValue("조정", formatQty(m.adjust))
                    KeyValue("안전재고", formatQty(m.safetyStock))
                    KeyValue("리드타임", "${m.leadTimeDays}일")
                    KeyValue("단가", "${formatMoney(m.unitPrice)}원 / ${m.unit}")
                    if (m.barcode.isNotBlank()) KeyValue("바코드", m.barcode)
                    if (m.packUnit.isNotBlank()) KeyValue("포장단위", m.packUnit)
                    KeyValue("입고금액", "${formatMoney(m.purchaseAmount)}원")
                    KeyValue("사용금액", "${formatMoney(m.usageAmount)}원")
                }
            }
            item { SectionTitle("${state.month.display()} 입고 · 폐기 · 반출") }
            item {
                Text(
                    "잘못 넣은 수량은 아래 줄에서 고칩니다. 기초정보 수정은 이름과 단가만 바꿉니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (lines.isEmpty()) {
                item { Text("이달 입고·폐기·반출이 없습니다.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(lines, key = { "mv-${it.id}" }) { mv ->
                val label = runCatching { MovementType.valueOf(mv.type).label }.getOrDefault(mv.type)
                AppCard {
                    Text("$label  ${formatQty(mv.qty)} ${m.unit}", style = MaterialTheme.typography.titleMedium)
                    Text(mv.occurredOn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (mv.note.isNotBlank()) {
                        Text(mv.note, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.canEditOps) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editing = mv
                                    editQty = plainQty(mv.qty)
                                    editDate = mv.occurredOn
                                    editNote = mv.note
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("수정") }
                            OutlinedButton(
                                onClick = { deleting = mv },
                                modifier = Modifier.weight(1f)
                            ) { Text("삭제") }
                        }
                    }
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
    val line = editing
    if (line != null) {
        val label = runCatching { MovementType.valueOf(line.type).label }.getOrDefault(line.type)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("$label 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (line.note.startsWith("생산불량")) {
                        Text("생산 불량으로 생긴 폐기입니다. 그 날 생산실적을 다시 저장하면 이 수량이 덮입니다.")
                    }
                    NumberField(editQty, { editQty = it }, "수량", suffix = m?.unit)
                    TextFieldPlain(editDate, { editDate = it }, "일자 (YYYY-MM-DD)")
                    TextFieldPlain(editNote, { editNote = it }, "메모")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = parseNumber(editQty)
                    if (q == null) {
                        vm.show("수량을 입력하세요")
                        return@TextButton
                    }
                    vm.replaceMovement(line, q, editDate.trim(), editNote.trim())
                    editing = null
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("취소") }
            }
        )
    }
    val doomed = deleting
    if (doomed != null) {
        val label = runCatching { MovementType.valueOf(doomed.type).label }.getOrDefault(doomed.type)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("이 $label 기록을 지울까요?") },
            text = { Text("${doomed.occurredOn} · ${formatQty(doomed.qty)} ${m?.unit.orEmpty()}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMovement(doomed)
                    deleting = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("취소") }
            }
        )
    }
}

private val EDITABLE_MOVEMENT_TYPES = setOf(
    MovementType.INBOUND.name,
    MovementType.SCRAP.name,
    MovementType.OUTBOUND.name
)

private fun plainQty(value: Double): String = formatQtyExact(value)

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
    var barcode by remember { mutableStateOf(existing?.barcode.orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit ?: "ea") }
    var pack by remember { mutableStateOf(existing?.packUnit.orEmpty()) }
    var price by remember { mutableStateOf(existing?.unitPrice?.let { if (it == 0.0) "" else formatQtyExact(it) }.orEmpty()) }
    var safety by remember { mutableStateOf(existing?.safetyStock?.let { if (it == 0.0) "" else formatQtyExact(it) }.orEmpty()) }
    var lead by remember { mutableStateOf(existing?.leadTimeDays?.takeIf { it > 0 }?.toString().orEmpty()) }
    var opening by remember { mutableStateOf(existing?.opening?.let { if (it == 0.0) "" else formatQtyExact(it) }.orEmpty()) }
    var pendingPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
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
            item { TextFieldPlain(barcode, { barcode = it.trim().uppercase() }, "바코드 (예: VN05L)") }
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
                ItemPhotoEditor(
                    workspaceId = state.workspaceId,
                    kind = "material",
                    savedCode = existing?.codeNo,
                    epoch = state.photoEpoch,
                    pending = pendingPhoto,
                    removed = photoRemoved,
                    enabled = state.role.name == "MANAGER",
                    onPicked = {
                        pendingPhoto = it
                        photoRemoved = false
                    },
                    onRemove = {
                        pendingPhoto = null
                        photoRemoved = true
                    },
                    onError = { vm.show(it) }
                )
            }
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
                    if (saving) return@PrimaryButton
                    saving = true
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
                                leadTimeDays = parseNumber(lead)?.toInt() ?: 0,
                                barcode = barcode.trim().uppercase()
                            ),
                            opening = parseNumber(opening),
                            photo = ItemPhotoEdit("material", existing?.codeNo, no, pendingPhoto, photoRemoved)
                        )
                        if (ok) onBack() else saving = false
                    }
                }, enabled = state.role.name == "MANAGER" && !saving)
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
    var materialQuery by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(presetMaterialId, materials.map { it.id }) {
        if (materialId == null || materials.none { it.id == materialId }) {
            materialId = presetMaterialId?.takeIf { id -> materials.any { it.id == id } }
                ?: materials.firstOrNull()?.id
        }
    }
    val selected = materials.firstOrNull { it.id == materialId }
    val scan = rememberBarcodeScan(onError = vm::show) { code ->
        val hit = BarcodeLookup.pick(code, materials, { it.barcode }, { it.codeNo })
        materialQuery = code
        if (hit != null) materialId = hit.id else vm.show("바코드 $code 자재를 찾지 못했습니다")
    }
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
            item { AppSearchField(materialQuery, { materialQuery = it }, "자재명 / 번호 / 바코드", onScan = scan) }
            val shown = materials.filter { ItemMatch.matches(it.codeNo, it.name, materialQuery, it.barcode) }
            if (materials.isEmpty()) {
                item {
                    Text(
                        "등록된 자재가 없습니다. 자재 탭에서 품목을 먼저 등록하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (shown.isEmpty() && materials.isNotEmpty()) {
                item { Text("찾는 자재가 없습니다.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(shown, key = { it.id }) { m ->
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
                        saving -> Unit
                        materials.isEmpty() -> vm.show("먼저 자재를 등록하세요")
                        id == null -> vm.show("자재를 선택하세요")
                        q == null || q <= 0.0 -> vm.show("수량은 0보다 크게 입력하세요")
                        else -> {
                            saving = true
                            vm.addMovement(id, type, q, date.trim(), note) { ok ->
                                if (ok) onBack() else saving = false
                            }
                        }
                    }
                }, enabled = state.canEditOps && !saving)
            }
        }
    }
}
