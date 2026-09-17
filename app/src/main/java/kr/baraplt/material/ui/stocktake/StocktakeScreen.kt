package kr.baraplt.material.ui.stocktake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.domain.parseNumber
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.KeyValue
import kr.baraplt.material.ui.components.LockedBanner
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocktakeScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val drafts = remember {
        mutableStateMapOf<Long, String>().also { map ->
            state.workspace?.materials?.forEach { map[it.id] = formatQty(it.current) }
        }
    }
    AppScreenScaffold(title = "재고조사", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                LockedBanner(state.closed)
                Text(
                    "실사 수량을 넣고 관리책임자가 승인하면 차이만큼 재고조정이 됩니다. 시작재고는 월말 재고조사에서 다음 달로 넘어갑니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.workspace?.materials.orEmpty(), key = { it.id }) { m ->
                val typed = drafts[m.id].orEmpty()
                val physical = parseNumber(typed)
                val diff = if (physical == null) 0.0 else physical - m.current
                AppCard {
                    Text("NO.${m.codeNo}  ${m.name}", style = MaterialTheme.typography.titleMedium)
                    KeyValue("장부재고", "${formatQty(m.current)} ${m.unit}")
                    NumberField(typed, { drafts[m.id] = it }, "실사수량", suffix = m.unit)
                    if (physical != null && kotlin.math.abs(diff) >= 0.0001) {
                        KeyValue("차이", "${formatQty(diff)} ${m.unit}")
                    }
                }
            }
            item {
                PrimaryButton(
                    text = if (state.role.name == "MANAGER") "차이 승인 · 조정 반영" else "관리책임자 승인 필요",
                    onClick = {
                        val counts = drafts.mapNotNull { (id, text) ->
                            parseNumber(text)?.let { id to it }
                        }.toMap()
                        vm.applyStocktake(counts, vm.todayInMonth())
                        onBack()
                    },
                    enabled = state.role.name == "MANAGER" && state.canEditOps
                )
            }
        }
    }
}
