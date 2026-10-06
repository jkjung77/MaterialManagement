package kr.baraplt.material.ui.finished

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kr.baraplt.material.data.entity.FinishedGoodEntity
import kr.baraplt.material.domain.ItemMatch
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatPct
import kr.baraplt.material.domain.formatQtyExact
import kr.baraplt.material.domain.parseNumber
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.AppSearchField
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.KeyValue
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.TextFieldPlain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedListScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOutput: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val finished = state.workspace?.finished.orEmpty().filter { item ->
        query.isBlank() ||
            item.name.contains(query, true) ||
            item.codeNo.toString().contains(query) ||
            item.productNames.any { it.contains(query, true) }
    }
    AppScreenScaffold(
        title = "완제품 · 월계획",
        onBack = onBack,
        actions = {
            if (state.role.name == "MANAGER") {
                IconButton(onAdd) { Icon(Icons.Default.Add, contentDescription = "추가") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(
                "월계획과 일일 실적을 관리합니다. 완성품 실적을 넣으면 구성 단품 재고가 빠집니다.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
            GhostButton("완성품 실적 입력", onClick = onOutput)
            Spacer(Modifier.height(8.dp))
            AppSearchField(query, { query = it }, "완제품명 / 번호 / 구성 단품")
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            items(finished, key = { it.id }) { f ->
                AppCard(onClick = { if (state.role.name == "MANAGER") onEdit(f.id) }) {
                    Text("NO.${f.codeNo}  ${f.name}", style = MaterialTheme.typography.titleMedium)
                    KeyValue("판매단가", "${formatMoney(f.sellPrice)}원")
                    KeyValue("완제품 자재비", "${formatMoney(f.materialCost)}원")
                    KeyValue("자재비율", formatPct(f.materialRatio))
                    KeyValue("월 생산계획", f.monthPlan.toString())
                    KeyValue("이달 실적", "${f.produced} · ${formatMoney(f.salesAmount)}원")
                    Text("구성: ${f.productNames.joinToString(" + ").ifBlank { "-"} }", style = MaterialTheme.typography.bodyMedium)
                    if (state.canEditOps && state.role.name == "MANAGER") {
                        Spacer(Modifier.height(8.dp))
                        var plan by remember(f.id, f.monthPlan) { mutableStateOf(f.monthPlan.takeIf { it > 0 }?.toString().orEmpty()) }
                        NumberField(plan, { plan = it }, "이달 월계획 수정")
                        GhostButton("계획 저장") {
                            val q = parseNumber(plan)?.toInt() ?: 0
                            vm.saveFinished(
                                FinishedGoodEntity(id = f.id, codeNo = f.codeNo, name = f.name, sellPrice = f.sellPrice),
                                f.productIds.mapIndexed { i, id -> id to f.qtyAt(i) },
                                q
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedEditScreen(
    state: AppUiState,
    vm: AppViewModel,
    existingId: Long?,
    onBack: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val existing = state.workspace?.finished?.firstOrNull { it.id == existingId }
    val products = state.workspace?.products.orEmpty()
    var code by remember { mutableStateOf(existing?.codeNo?.toString().orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var price by remember { mutableStateOf(existing?.sellPrice?.takeIf { it > 0 }?.let(::formatQtyExact).orEmpty()) }
    var plan by remember { mutableStateOf(existing?.monthPlan?.takeIf { it > 0 }?.toString().orEmpty()) }
    var productQuery by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val selected = remember {
        mutableStateListOf<Long>().also { it.addAll(existing?.productIds.orEmpty()) }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(existingId) {
        if (existing == null && code.isEmpty()) code = vm.nextFinishedNo().toString()
    }
    AppScreenScaffold(title = if (existing == null) "완제품 등록" else "완제품 수정", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { NumberField(code, { code = it }, "완제품번호 (1~500)") }
            item { TextFieldPlain(name, { name = it }, "완제품명(Item)") }
            item { NumberField(price, { price = it }, "완제품 판매단가", suffix = "원") }
            item { NumberField(plan, { plan = it }, "이달 월 생산계획") }
            item { SectionTitle("구성 단품 (최대 30) · 선택 ${selected.size}") }
            item { AppSearchField(productQuery, { productQuery = it }, "단품명 / 번호 / 바코드") }
            val shown = products.filter { ItemMatch.matches(it.codeNo, it.name, productQuery, it.barcode) }
            if (shown.isEmpty()) {
                item { Text("찾는 단품이 없습니다.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(shown, key = { it.id }) { p ->
                val on = p.id in selected
                FilterChip(
                    selected = on,
                    onClick = {
                        if (on) selected.remove(p.id)
                        else if (selected.size < 30) selected.add(p.id)
                        else vm.show("구성 단품은 30개까지입니다")
                    },
                    label = { Text("NO.${p.codeNo} ${p.name}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                PrimaryButton("저장", onClick = {
                    val no = parseNumber(code)?.toInt()
                    if (no == null || no !in 1..500 || name.isBlank()) {
                        vm.show("번호(1~500)와 완제품명을 확인하세요")
                        return@PrimaryButton
                    }
                    if (saving) return@PrimaryButton
                    saving = true
                    vm.saveFinished(
                        FinishedGoodEntity(
                            id = existing?.id ?: 0,
                            codeNo = no,
                            name = name.trim(),
                            sellPrice = parseNumber(price) ?: 0.0
                        ),
                        selected.map { id ->
                            val idx = existing?.productIds?.indexOf(id) ?: -1
                            id to if (idx >= 0) existing!!.qtyAt(idx) else 1
                        },
                        parseNumber(plan)?.toInt()
                    ) { ok -> if (ok) onBack() else saving = false }
                }, enabled = state.role.name == "MANAGER" && !saving)
            }
            if (existing != null && state.role.name == "MANAGER") {
                item {
                    GhostButton("완제품 삭제") { confirmDelete = true }
                }
            }
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 완제품을 삭제할까요?") },
            text = { Text("구성과 월계획도 함께 지워집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteFinished(existing.id) { ok -> if (ok) onBack() }
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
}
