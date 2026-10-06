package kr.baraplt.material.ui.stocktake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.ItemMatch
import kr.baraplt.material.ui.components.rememberBarcodeScan
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.domain.formatQtyExact
import kr.baraplt.material.domain.parseNumber
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.AppSearchField
import kr.baraplt.material.ui.components.KeyValue
import kr.baraplt.material.ui.components.LockedBanner
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.TextFieldPlain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocktakeScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val drafts = remember {
        mutableStateMapOf<Long, String>().also { map ->
            state.workspace?.materials?.forEach { map[it.id] = formatQtyExact(it.current) }
        }
    }
    val reasons = remember { mutableStateMapOf<Long, String>() }
    var submitted by remember { mutableStateOf(false) }
    val materials = state.workspace?.materials.orEmpty().filter {
        ItemMatch.matches(it.codeNo, it.name, query, it.barcode)
    }
    val scan = rememberBarcodeScan(onError = vm::show) { query = it }
    AppScreenScaffold(title = "재고조사", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    LockedBanner(state.closed)
                    Text(
                        "실사 수량을 넣고 관리책임자가 승인하면 차이만큼 재고조정이 됩니다. 수량이 다르면 수정 사유를 적을 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    AppSearchField(query, { query = it }, "자재명 / 번호 / 바코드", Modifier.fillMaxWidth(), onScan = scan)
                }
                items(materials, key = { it.id }) { m ->
                    val typed = drafts[m.id].orEmpty()
                    val physical = parseNumber(typed)
                    val diff = if (physical == null) 0.0 else physical - m.current
                    val changed = physical != null && kotlin.math.abs(diff) >= 0.0001
                    AppCard {
                        Text("NO.${m.codeNo}  ${m.name}", style = MaterialTheme.typography.titleMedium)
                        KeyValue("장부재고", "${formatQty(m.current)} ${m.unit}")
                        NumberField(typed, { drafts[m.id] = it }, "실사수량", suffix = m.unit)
                        if (changed) {
                            KeyValue("차이", "${formatQty(diff)} ${m.unit}")
                            TextFieldPlain(
                                reasons[m.id].orEmpty(),
                                { reasons[m.id] = it },
                                "수정 사유",
                                Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            PrimaryButton(
                text = if (state.role.name == "MANAGER") "차이 승인 · 조정 반영" else "관리책임자 승인 필요",
                onClick = {
                    if (submitted) return@PrimaryButton
                    submitted = true
                    val counts = drafts.mapNotNull { (id, text) ->
                        parseNumber(text)?.let { id to it }
                    }.toMap()
                    vm.applyStocktake(counts, reasons.toMap(), vm.todayInMonth())
                    onBack()
                },
                enabled = state.role.name == "MANAGER" && state.canEditOps && !submitted,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}
