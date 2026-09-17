package kr.baraplt.material.ui.production

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppListCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.LockedBanner
import kr.baraplt.material.ui.components.MonthSwitcher
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.SectionTitle
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(
    state: AppUiState,
    vm: AppViewModel,
    presetProductId: Long?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val products = state.workspace?.products.orEmpty()
    var productId by remember { mutableStateOf(presetProductId ?: products.firstOrNull()?.id) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var qtyText by remember { mutableStateOf("") }
    val product = products.firstOrNull { it.id == productId }
    val days = state.month.dayCount()
    val qtyByDay = state.workspace?.production
        ?.filter { it.productId == productId }
        ?.associate { it.workDate.takeLast(2).toInt() to it.qty }
        .orEmpty()

    AppScreenScaffold(title = "단품 생산실적") { padding ->
    LazyColumn(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "오전에 전일·당일 실적을 넣으면 자재 투입이 자동 계산됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            MonthSwitcher(state.month.display(), state.closed, onPrevMonth, onNextMonth)
        }
        item { LockedBanner(state.closed) }
        item { SectionTitle("단품 선택") }
        items(products, key = { it.id }) { p ->
            AppListCard(
                title = "NO.${p.codeNo}  ${p.name}",
                subtitle = "실적 ${p.produced} / 계획 ${p.monthPlan}",
                onClick = { productId = p.id },
                trailing = {
                    if (p.id == productId) Text("선택", color = MaterialTheme.colorScheme.primary)
                }
            )
        }
        if (product != null) {
            item {
                AppCard {
                    Text(product.name, style = MaterialTheme.typography.titleLarge)
                    Text("이달 합계 ${product.produced} · 자재투입 ${formatMoney(product.usageAmount)}원", style = MaterialTheme.typography.bodyMedium)
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

    val day = selectedDay
    if (day != null && product != null) {
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text("${product.name} · ${day}일") },
            text = {
                Column {
                    if (state.closed) Text("마감된 달은 수정하지 않습니다.")
                    else NumberField(qtyText, { qtyText = it }, "생산수량")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.canEditOps) {
                            val q = qtyText.replace(",", "").toIntOrNull() ?: 0
                            vm.setProduction(product.id, day, q)
                        }
                        selectedDay = null
                    }
                ) { Text(if (state.canEditOps) "저장" else "닫기") }
            },
            dismissButton = { TextButton({ selectedDay = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun DayGrid(
    year: Int,
    month: Int,
    days: Int,
    qtyByDay: Map<Int, Int>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    val weekLabels = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(Modifier.fillMaxWidth()) {
        weekLabels.forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(6.dp))
    val offset = java.time.LocalDate.of(year, month, 1).dayOfWeek.value % 7
    val cells = List(offset) { 0 } + (1..days).toList()
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { day ->
                if (day == 0) {
                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                } else {
                    val qty = qtyByDay[day] ?: 0
                    val scheme = MaterialTheme.colorScheme
                    val bg = when {
                        selected == day -> scheme.primary
                        qty > 0 -> scheme.primaryContainer
                        else -> scheme.surface
                    }
                    val fg = when {
                        selected == day -> scheme.onPrimary
                        qty > 0 -> scheme.onPrimaryContainer
                        else -> scheme.onSurface
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { onSelect(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$day", color = fg, style = MaterialTheme.typography.labelMedium)
                            if (qty > 0) Text("$qty", color = fg, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
        }
        Spacer(Modifier.height(4.dp))
    }
}
