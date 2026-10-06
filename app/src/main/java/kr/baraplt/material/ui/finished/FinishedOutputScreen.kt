package kr.baraplt.material.ui.finished

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.ItemMatch
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
fun FinishedOutputScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val finished = state.workspace?.finished.orEmpty()
    var query by remember { mutableStateOf("") }
    val filtered = finished.filter {
        query.isBlank() ||
            ItemMatch.matches(it.codeNo, it.name, query) ||
            it.productNames.any { name -> name.contains(query, true) }
    }
    var finishedId by remember { mutableStateOf(finished.firstOrNull()?.id) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var qtyText by remember { mutableStateOf("") }
    val item = finished.firstOrNull { it.id == finishedId }
    val days = state.month.dayCount()
    val qtyByDay = state.workspace?.finishedOutput
        ?.filter { it.finishedGoodId == finishedId }
        ?.associate { it.workDate.takeLast(2).toInt() to it.qty }
        .orEmpty()

    AppScreenScaffold(title = "완성품 실적", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(
                "날짜별 완성품 수량을 넣으면 구성 단품 재고가 빠집니다. 자재는 단품 생산실적을 넣을 때만 빠집니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(Modifier.height(8.dp))
            MonthSwitcher(state.month.display(), state.closed, onPrevMonth, onNextMonth)
            LockedBanner(state.closed)
            Spacer(Modifier.height(8.dp))
            AppSearchField(query, { query = it }, "완성품명 / 번호 / 구성 단품")
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SectionTitle("완성품 선택") }
                if (filtered.isEmpty()) {
                    item { Text("찾는 완성품이 없습니다.", style = MaterialTheme.typography.bodyMedium) }
                }
                items(filtered, key = { it.id }) { f ->
                    AppListCard(
                        title = "NO.${f.codeNo}  ${f.name}",
                        subtitle = "실적 ${f.produced} / 계획 ${f.monthPlan} · ${formatMoney(f.salesAmount)}원",
                        onClick = { finishedId = f.id },
                        trailing = {
                            if (f.id == finishedId) Text("선택", color = MaterialTheme.colorScheme.primary)
                        }
                    )
                }
                if (item != null && filtered.any { it.id == item.id }) {
                    item {
                        AppCard {
                            Text(item.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "이달 합계 ${item.produced} · ${formatMoney(item.salesAmount)}원",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val parts = item.productNames.mapIndexed { i, name ->
                                "$name ×${item.qtyAt(i)}"
                            }
                            Text(
                                "구성: ${parts.joinToString(" + ").ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            DayGrid(state.month.year, state.month.month, days, qtyByDay, selectedDay) { day ->
                                selectedDay = day
                                qtyText = qtyByDay[day]?.toString().orEmpty()
                            }
                        }
                    }
                }
            }
        }
    }

    val day = selectedDay
    if (day != null && item != null) {
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text("${item.name} · ${day}일") },
            text = {
                Column {
                    if (state.closed) {
                        Text("마감된 달은 수정하지 않습니다.")
                    } else {
                        NumberField(qtyText, { qtyText = it }, "완성품 수량")
                        Text(
                            "저장하면 구성 단품이 빠집니다. 단품 재고가 모자라도 저장은 됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.canEditOps) {
                            val q = parseCount(qtyText)
                            if (q == null) {
                                vm.show("완성품 수량은 0 이상의 정수로 입력하세요")
                                return@TextButton
                            }
                            vm.setFinishedProduction(item.id, day, q)
                        }
                        selectedDay = null
                    }
                ) { Text(if (state.canEditOps) "저장" else "닫기") }
            },
            dismissButton = { TextButton({ selectedDay = null }) { Text("취소") } }
        )
    }
}
