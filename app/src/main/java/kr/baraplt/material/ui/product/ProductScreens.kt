package kr.baraplt.material.ui.product

import androidx.compose.foundation.layout.Arrangement
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
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatPct
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
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.TextFieldPlain

@Composable
fun ProductListScreen(
    state: AppUiState,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val items = state.workspace?.products.orEmpty().filter {
        query.isBlank() || it.name.contains(query, true) || it.codeNo.toString().contains(query)
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
            item { AppSearchField(query, { query = it }, "단품명 / 번호 검색") }
            items(items, key = { it.id }) { p ->
                AppListCard(
                    title = "NO.${p.codeNo}  ${p.name}",
                    subtitle = "이달 ${p.produced} / 계획 ${p.monthPlan} · 자재비 ${formatMoney(p.materialCost)}원 · ${formatPct(p.materialRatio)}",
                    onClick = { onOpen(p.id) }
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
                    Text("NO.${p.codeNo}  ${p.name}", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    KeyValue("판매단가", "${formatMoney(p.sellPrice)}원")
                    KeyValue("단품자재비용", "${formatMoney(p.materialCost)}원")
                    KeyValue("자재비율", formatPct(p.materialRatio))
                    KeyValue("월 생산계획", p.monthPlan.toString())
                    KeyValue("이달 실적", p.produced.toString())
                    KeyValue("투입자재비용", "${formatMoney(p.usageAmount)}원")
                }
            }
            item { SectionTitle("투입자재 BOM (${p.bom.size}/30)") }
            items(p.bom, key = { it.materialId }) { line ->
                AppCard {
                    Text("NO.${line.materialNo}  ${line.materialName}", style = MaterialTheme.typography.titleSmall)
                    Text("US ${formatQty(line.usQty)} ${line.unit} · ${formatMoney(line.lineCost)}원", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                PrimaryButton("생산실적 입력", onClick = onProduction)
                if (state.role.name == "MANAGER") {
                    Spacer(Modifier.height(8.dp))
                    GhostButton("기초정보 수정", onClick = onEdit)
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

private data class BomDraft(var materialId: Long, var us: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    state: AppUiState,
    vm: AppViewModel,
    existingId: Long?,
    onBack: () -> Unit
) {
    val existing = state.workspace?.products?.firstOrNull { it.id == existingId }
    val materials = state.workspace?.materials.orEmpty()
    var code by remember { mutableStateOf(existing?.codeNo?.toString().orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var price by remember { mutableStateOf(existing?.sellPrice?.takeIf { it > 0 }?.toLong()?.toString().orEmpty()) }
    var plan by remember { mutableStateOf(existing?.monthPlan?.takeIf { it > 0 }?.toString().orEmpty()) }
    val bom = remember {
        mutableStateListOf<BomDraft>().also { list ->
            existing?.bom?.forEach { list.add(BomDraft(it.materialId, formatQty(it.usQty))) }
            if (list.isEmpty()) list.add(BomDraft(materials.firstOrNull()?.id ?: 0, ""))
        }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(existingId) {
        if (existing == null && code.isEmpty()) code = vm.nextProductNo().toString()
    }
    AppScreenScaffold(title = if (existing == null) "단품 등록" else "단품 수정", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { NumberField(code, { code = it }, "단품번호 (1~500)") }
            item { TextFieldPlain(name, { name = it }, "제품명(단품)") }
            item { NumberField(price, { price = it }, "단품판매단가", suffix = "원") }
            item { NumberField(plan, { plan = it }, "이달 생산계획") }
            item { SectionTitle("투입자재 (최대 30)") }
            items(bom.size) { index ->
                val row = bom[index]
                AppCard {
                    Text("자재 ${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    materials.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { m ->
                                FilterChip(
                                    selected = row.materialId == m.id,
                                    onClick = { bom[index] = row.copy(materialId = m.id) },
                                    label = { Text("NO.${m.codeNo} ${m.name}") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    NumberField(row.us, { bom[index] = row.copy(us = it) }, "US 사용량")
                    if (bom.size > 1) {
                        GhostButton("이 줄 삭제") { bom.removeAt(index) }
                    }
                }
            }
            item {
                if (bom.size < 30) GhostButton("투입자재 추가") {
                    bom.add(BomDraft(materials.firstOrNull()?.id ?: 0, ""))
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
                    scope.launch {
                        vm.saveProduct(
                            ProductEntity(
                                id = existing?.id ?: 0,
                                codeNo = no,
                                name = name.trim(),
                                sellPrice = parseNumber(price) ?: 0.0
                            ),
                            lines,
                            parseNumber(plan)?.toInt()
                        )
                        onBack()
                    }
                }, enabled = state.role.name == "MANAGER")
            }
        }
    }
}
