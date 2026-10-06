package kr.baraplt.material.ui.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.BarcodeLookup
import kr.baraplt.material.domain.ItemMatch
import kr.baraplt.material.ui.components.rememberBarcodeScan
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.parseCount
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppListCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.AppSearchField
import kr.baraplt.material.ui.components.DayGrid
import kr.baraplt.material.ui.components.LockedBanner
import kr.baraplt.material.ui.components.MonthSwitcher
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(
    state: AppUiState,
    vm: AppViewModel,
    presetProductId: Long?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPresetUsed: () -> Unit = {}
) {
    val products = state.workspace?.products.orEmpty()
    var query by remember { mutableStateOf("") }
    val filtered = products.filter { ItemMatch.matches(it.codeNo, it.name, query, it.barcode) }
    var productId by remember { mutableStateOf(presetProductId ?: products.firstOrNull()?.id) }
    LaunchedEffect(presetProductId) {
        if (presetProductId != null) {
            productId = presetProductId
            query = ""
            onPresetUsed()
        }
    }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var defectText by remember { mutableStateOf("") }
    val product = products.firstOrNull { it.id == productId }
    val scan = rememberBarcodeScan(onError = vm::show) { code ->
        val hit = BarcodeLookup.pick(code, products, { it.barcode }, { it.codeNo })
        query = code
        if (hit != null) productId = hit.id else vm.show("바코드 $code 단품을 찾지 못했습니다")
    }
    val days = state.month.dayCount()
    val qtyByDay = state.workspace?.production
        ?.filter { it.productId == productId }
        ?.associate { it.workDate.takeLast(2).toInt() to it.qty }
        .orEmpty()

    AppScreenScaffold(title = "단품 생산실적") { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
    Text(
        "오전에 전일·당일 실적을 넣으면 자재 투입이 자동 계산됩니다.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
    Spacer(Modifier.height(8.dp))
    MonthSwitcher(state.month.display(), state.closed, onPrevMonth, onNextMonth)
    LockedBanner(state.closed)
    Spacer(Modifier.height(8.dp))
    AppSearchField(query, { query = it }, "단품명 / 번호 / 바코드", onScan = scan)
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("단품 선택") }
        if (filtered.isEmpty()) {
            item { Text("찾는 단품이 없습니다.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(filtered, key = { it.id }) { p ->
            AppListCard(
                title = "NO.${p.codeNo}  ${p.name}",
                subtitle = listOfNotNull(
                    "실적 ${p.produced} / 계획 ${p.monthPlan}",
                    p.finishedRefLabel.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                onClick = { productId = p.id },
                trailing = {
                    if (p.id == productId) Text("선택", color = MaterialTheme.colorScheme.primary)
                }
            )
        }
        if (product != null && filtered.any { it.id == product.id }) {
            item {
                AppCard {
                    Text(product.name, style = MaterialTheme.typography.titleLarge)
                    Text("이달 합계 ${product.produced} · 자재투입 ${formatMoney(product.usageAmount)}원", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    DayGrid(state.month.year, state.month.month, days, qtyByDay, selectedDay) { day ->
                        selectedDay = day
                        qtyText = qtyByDay[day]?.toString().orEmpty()
                        val date = "%s-%02d".format(state.month.value, day)
                        val marker = "생산불량 NO.${product.codeNo} $date"
                        val line = product.bom.firstOrNull { it.usQty > 0 }
                        val scrap = state.workspace?.movements?.firstOrNull {
                            it.occurredOn == date && it.note == marker && it.materialId == line?.materialId
                        }
                        val defect = if (line != null && scrap != null) kotlin.math.round(scrap.qty / line.usQty).toInt() else 0
                        defectText = if (defect > 0) defect.toString() else ""
                    }
                }
            }
        }
    }
    }
    }

    val day = selectedDay
    if (day != null && product != null) {
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text("${product.name} · ${day}일") },
            text = {
                Column {
                    if (state.closed) Text("마감된 달은 수정하지 않습니다.")
                    else {
                        NumberField(qtyText, { qtyText = it }, "생산수량")
                        NumberField(defectText, { defectText = it }, "불량")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.canEditOps) {
                            val q = parseCount(qtyText)
                            val defect = parseCount(defectText)
                            if (q == null || defect == null) {
                                vm.show("생산수량과 불량은 0 이상의 정수로 입력하세요")
                                return@TextButton
                            }
                            vm.setProduction(product.id, day, q, defect)
                        }
                        selectedDay = null
                    }
                ) { Text(if (state.canEditOps) "저장" else "닫기") }
            },
            dismissButton = { TextButton({ selectedDay = null }) { Text("취소") } }
        )
    }
}
