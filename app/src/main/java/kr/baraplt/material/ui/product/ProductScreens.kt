package kr.baraplt.material.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.baraplt.material.data.entity.ProductBomEntity
import kr.baraplt.material.data.entity.ProductEntity
import kr.baraplt.material.domain.BarcodeLookup
import kr.baraplt.material.domain.ItemMatch
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatPct
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.domain.formatQtyExact
import kr.baraplt.material.domain.parseNumber
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.ItemPhotoEdit
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.ItemPhotoEditor
import kr.baraplt.material.ui.components.ItemPhotoLarge
import kr.baraplt.material.ui.components.photoLeading
import kr.baraplt.material.ui.components.AppListCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.AppSearchField
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.KeyValue
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.TextFieldPlain
import kr.baraplt.material.ui.components.rememberBarcodeScan

@Composable
fun ProductListScreen(
    state: AppUiState,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: (() -> Unit)? = null,
    onMessage: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val items = state.workspace?.products.orEmpty().filter {
        ItemMatch.matches(it.codeNo, it.name, query, it.barcode)
    }
    val scan = rememberBarcodeScan(onError = onMessage) { code ->
        val hit = BarcodeLookup.pick(code, state.workspace?.products.orEmpty(), { it.barcode }, { it.codeNo })
        if (hit != null) onOpen(hit.id) else {
            query = code
            onMessage("바코드 $code 단품을 찾지 못했습니다")
        }
    }
    AppScreenScaffold(
        title = "단품",
        onBack = onBack,
        floatingActionButton = {
            if (state.role.name == "MANAGER") {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "추가")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { AppSearchField(query, { query = it }, "단품명 / 번호 / 바코드", onScan = scan) }
            items(items, key = { it.id }) { p ->
                AppListCard(
                    title = "NO.${p.codeNo}  ${p.name}",
                    subtitle = listOfNotNull(
                        "이달 ${p.produced} / 계획 ${p.monthPlan}",
                        "현재고 ${p.current}",
                        p.finishedRefLabel.takeIf { it.isNotBlank() },
                        "자재비 ${formatMoney(p.materialCost)}원 · ${formatPct(p.materialRatio)}"
                    ).joinToString(" · "),
                    onClick = { onOpen(p.id) },
                    leading = photoLeading(state.workspaceId, "product", p.codeNo, state.photoEpoch)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    state: AppUiState,
    vm: AppViewModel,
    productId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit = {},
    onProduction: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val p = state.workspace?.products?.firstOrNull { it.id == productId }
    AppScreenScaffold(title = p?.name ?: "단품", onBack = onBack) { padding ->
        if (p == null) return@AppScreenScaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AppCard {
                    ItemPhotoLarge(state.workspaceId, "product", p.codeNo, state.photoEpoch)
                    Text("NO.${p.codeNo}  ${p.name}", style = MaterialTheme.typography.headlineMedium)
                    if (p.barcode.isNotBlank()) KeyValue("바코드", p.barcode)
                    Spacer(Modifier.height(8.dp))
                    KeyValue("판매단가", "${formatMoney(p.sellPrice)}원")
                    KeyValue("단품자재비용", "${formatMoney(p.materialCost)}원")
                    KeyValue("자재비율", formatPct(p.materialRatio))
                    KeyValue("월 생산계획", p.monthPlan.toString())
                    if (p.finishedRefLabel.isNotBlank()) {
                        KeyValue("완제품 참고", "${p.finishedRefQty} (${p.finishedRefNote})")
                    }
                    KeyValue("이달 실적", p.produced.toString())
                    KeyValue("시작재고", p.opening.toString())
                    KeyValue("완성품 투입", p.consumed.toString())
                    KeyValue("현재고", p.current.toString())
                    KeyValue("안전재고", if (p.safetyStock > 0) p.safetyStock.toString() else "-")
                    KeyValue("투입자재비용", "${formatMoney(p.usageAmount)}원")
                }
            }
            item { SectionTitle("투입자재 BOM (${p.bom.size}/30)") }
            items(p.bom, key = { it.materialId }) { line ->
                AppCard {
                    Text("NO.${line.materialNo}  ${line.materialName}", style = MaterialTheme.typography.titleSmall)
                    Text("US ${formatQtyExact(line.usQty)} ${line.unit} · ${formatMoney(line.lineCost)}원", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                PrimaryButton("생산실적 입력", onClick = onProduction)
                if (state.role.name == "MANAGER") {
                    Spacer(Modifier.height(8.dp))
                    GhostButton("기초정보 수정", onClick = onEdit)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("이 단품 복사해서 새로 만들기", onClick = onCopy)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("단품 삭제") { confirmDelete = true }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 단품을 삭제할까요?") },
            text = { Text("이름이나 단가만 바꾸면 「기초정보 수정」을 쓰면 됩니다. 생산실적이나 완제품에 쓰인 단품은 지울 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteProduct(productId) { ok -> if (ok) onBack() }
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
}

private data class BomDraft(
    val uid: Long = System.nanoTime(),
    var materialId: Long = 0,
    var us: String = "",
    var query: String = "",
    var picking: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    state: AppUiState,
    vm: AppViewModel,
    existingId: Long?,
    copyFromId: Long? = null,
    onBack: () -> Unit
) {
    val existing = state.workspace?.products?.firstOrNull { it.id == existingId }
    val source = existing ?: state.workspace?.products?.firstOrNull { it.id == copyFromId }
    val materials = state.workspace?.materials.orEmpty()
    var code by remember { mutableStateOf(existing?.codeNo?.toString().orEmpty()) }
    var name by remember { mutableStateOf(source?.name.orEmpty()) }
    var barcode by remember { mutableStateOf(existing?.barcode.orEmpty()) }
    var price by remember { mutableStateOf(source?.sellPrice?.takeIf { it > 0 }?.let(::formatQtyExact).orEmpty()) }
    var plan by remember { mutableStateOf(existing?.monthPlan?.takeIf { it > 0 }?.toString().orEmpty()) }
    var safety by remember { mutableStateOf(source?.safetyStock?.takeIf { it > 0 }?.toString().orEmpty()) }
    var opening by remember { mutableStateOf(existing?.opening?.takeIf { it != 0 }?.toString().orEmpty()) }
    var pendingPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val bom = remember {
        mutableStateListOf<BomDraft>().also { list ->
            source?.bom?.forEach { list.add(BomDraft(materialId = it.materialId, us = formatQtyExact(it.usQty))) }
            if (list.isEmpty()) list.add(BomDraft())
        }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(existingId) {
        if (existing == null && code.isEmpty()) code = vm.nextProductNo().toString()
    }
    val copying = existing == null && source != null
    AppScreenScaffold(
        title = when {
            existing != null -> "단품 수정"
            copying -> "단품 복사 등록"
            else -> "단품 등록"
        },
        onBack = onBack
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (copying) {
                item {
                    Text(
                        "NO.${source!!.codeNo} ${source.name}의 이름·단가·투입자재를 복사했습니다. 이름을 고치고 자재를 넣고 뺀 뒤 저장하세요. 원본 단품은 바뀌지 않습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { NumberField(code, { code = it }, "단품번호 (1~500)") }
            item { TextFieldPlain(name, { name = it }, "제품명(단품)") }
            item { TextFieldPlain(barcode, { barcode = it.trim().uppercase() }, "바코드 (예: VN05L)") }
            item { NumberField(price, { price = it }, "단품판매단가", suffix = "원") }
            item { NumberField(plan, { plan = it }, "이달 생산계획") }
            item { NumberField(safety, { safety = it }, "안전재고") }
            item { NumberField(opening, { opening = it }, "이달 시작재고") }
            item {
                ItemPhotoEditor(
                    workspaceId = state.workspaceId,
                    kind = "product",
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
            item { SectionTitle("투입자재 (최대 30)") }
            items(bom.size, key = { index -> bom[index].uid }) { index ->
                val row = bom[index]
                val chosen = materials.firstOrNull { it.id == row.materialId }
                AppCard {
                    Text("자재 ${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    if (chosen != null) {
                        Text("선택: NO.${chosen.codeNo}  ${chosen.name}", style = MaterialTheme.typography.titleSmall)
                    }
                    TextFieldPlain(row.query, { bom[index] = row.copy(query = it) }, "자재검색 (번호 또는 품명)")
                    val found = if (row.query.isBlank()) {
                        emptyList()
                    } else {
                        materials.filter { ItemMatch.matches(it.codeNo, it.name, row.query, it.barcode) }
                            .sortedBy { it.codeNo }
                    }
                    found.take(8).forEach { m ->
                        OutlinedButton(
                            onClick = { bom[index] = row.copy(materialId = m.id, query = "") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("NO.${m.codeNo}  ${m.name}", maxLines = 2) }
                    }
                    if (row.query.isNotBlank() && found.isEmpty()) {
                        Text("찾는 자재가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    } else if (found.size > 8) {
                        Text("더 입력하면 목록이 줄어듭니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    NumberField(row.us, { bom[index] = row.copy(us = it) }, "US 사용량")
                    OutlinedButton(
                        onClick = {
                            if (bom.size == 1) bom[index] = BomDraft() else bom.removeAt(index)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("삭제") }
                }
            }
            item {
                if (bom.size < 30) GhostButton("투입자재 추가") {
                    bom.add(BomDraft())
                }
            }
            item {
                PrimaryButton("저장", onClick = {
                    val no = parseNumber(code)?.toInt()
                    if (no == null || no !in 1..500 || name.isBlank()) {
                        vm.show("번호와 단품명을 확인하세요")
                        return@PrimaryButton
                    }
                    val lines = bom.mapNotNull { d ->
                        val us = parseNumber(d.us) ?: return@mapNotNull null
                        if (d.materialId == 0L || us <= 0.0) null
                        else ProductBomEntity(productId = existing?.id ?: 0, materialId = d.materialId, usQty = us)
                    }
                    if (saving) return@PrimaryButton
                    saving = true
                    scope.launch {
                        vm.saveProduct(
                            ProductEntity(
                                id = existing?.id ?: 0,
                                codeNo = no,
                                name = name.trim(),
                                sellPrice = parseNumber(price) ?: 0.0,
                                barcode = barcode.trim().uppercase(),
                                safetyStock = parseNumber(safety)?.toInt() ?: 0
                            ),
                            lines,
                            parseNumber(plan)?.toInt(),
                            photo = ItemPhotoEdit("product", existing?.codeNo, no, pendingPhoto, photoRemoved),
                            opening = parseNumber(opening)?.toInt()
                        ) { ok -> if (ok) onBack() else saving = false }
                    }
                }, enabled = state.role.name == "MANAGER" && !saving)
            }
        }
    }
}
