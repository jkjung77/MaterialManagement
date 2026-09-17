package kr.baraplt.material.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.StockStatus
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatPct
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.components.AppListCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.KpiTile
import kr.baraplt.material.ui.components.LockedBanner
import kr.baraplt.material.ui.components.MonthSwitcher
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.StatusChip
import kr.baraplt.material.ui.components.TwoCol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onInbound: () -> Unit,
    onProduction: () -> Unit,
    onScrap: () -> Unit,
    onStocktake: () -> Unit,
    onMaterial: (Long) -> Unit,
    onSync: () -> Unit = {}
) {
    val ws = state.workspace
    AppScreenScaffold(title = "자재관리") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    listOfNotNull(
                        state.workspaceId.takeIf { it.isNotBlank() },
                        state.staffName,
                        if (state.role.name == "MANAGER") "관리책임자" else "담당자",
                        if (state.serverLinked) "서버 연결" else "이 폰만"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                MonthSwitcher(
                    label = state.month.display(),
                    closed = state.closed,
                    onPrev = onPrevMonth,
                    onNext = onNextMonth
                )
            }
            item { LockedBanner(state.closed) }
            item {
                GhostButton(
                    if (state.pendingCount > 0) "서버와 맞추기 · 대기 ${state.pendingCount}건" else "서버와 맞추기"
                ) { onSync() }
            }
            if (ws != null) {
                item {
                    TwoCol {
                        KpiTile("자재투입비율", formatPct(ws.report.materialRatio), ws.report.grade, Modifier.weight(1f))
                        KpiTile("판매금액", formatMoney(ws.report.salesAmount), "원", Modifier.weight(1f))
                    }
                }
                item {
                    TwoCol {
                        KpiTile("자재사용금액", formatMoney(ws.report.usageAmount), "원", Modifier.weight(1f))
                        KpiTile("자재입고금액", formatMoney(ws.report.purchaseAmount), "원", Modifier.weight(1f))
                    }
                }
                item {
                    TwoCol {
                        val low = ws.materials.count { it.status != StockStatus.OK }
                        KpiTile("재고경보", "${low}건", "안전재고 이하", Modifier.weight(1f))
                        KpiTile("단품생산", "${ws.report.producedQty}", "대/개", Modifier.weight(1f))
                    }
                }
                item { SectionTitle("오늘 할 일") }
                item {
                    PrimaryButton("자재 입고", enabled = state.canEditOps, onClick = onInbound)
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("단품 생산실적", onClick = onProduction)
                    Spacer(Modifier.height(8.dp))
                    TwoCol {
                        GhostButton("폐기/반출", Modifier.weight(1f), enabled = state.canEditOps, onClick = onScrap)
                        GhostButton("재고조사", Modifier.weight(1f), onClick = onStocktake)
                    }
                }
                val alerts = ws.materials.filter { it.status != StockStatus.OK }
                if (alerts.isNotEmpty()) {
                    item { SectionTitle("안전재고 경보") }
                    items(alerts, key = { it.id }) { m ->
                        AppListCard(
                            title = "NO.${m.codeNo}  ${m.name}",
                            subtitle = "현재고 ${formatQty(m.current)} ${m.unit} · 안전 ${formatQty(m.safetyStock)} · 리드타임 ${m.leadTimeDays}일",
                            onClick = { onMaterial(m.id) },
                            trailing = { StatusChip(m.status) }
                        )
                    }
                }
                val need = ws.required.filter { it.shortage > 0 }
                if (need.isNotEmpty()) {
                    item { SectionTitle("월계획 대비 부족") }
                    items(need.take(8), key = { "need-${it.materialId}" }) { r ->
                        AppListCard(
                            title = "NO.${r.codeNo}  ${r.name}",
                            subtitle = "필요 ${formatQty(r.required)} / 현재 ${formatQty(r.current)} · 부족 ${formatQty(r.shortage)} ${r.unit}",
                            onClick = { onMaterial(r.materialId) }
                        )
                    }
                }
                item {
                    Text(
                        "현장 통신이 약해도 이 단말에서 바로 입력할 수 있습니다. 퇴근 전 백업을 권장합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
