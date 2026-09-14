package kr.baraplt.material.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatPct
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.KeyValue
import kr.baraplt.material.ui.components.KpiTile
import kr.baraplt.material.ui.components.MonthSwitcher
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.TwoCol
import kr.baraplt.material.ui.theme.InkMute

@Composable
fun ReportScreen(
    state: AppUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val ws = state.workspace
    val report = ws?.report
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("매출 대비 자재실적", style = MaterialTheme.typography.headlineMedium)
            Text("단품 생산실적 기준으로 계산합니다.", style = MaterialTheme.typography.bodyMedium, color = InkMute)
        }
        item { MonthSwitcher(state.month.display(), state.closed, onPrevMonth, onNextMonth) }
        if (report != null) {
            item {
                AppCard {
                    Text("종합", style = MaterialTheme.typography.titleLarge)
                    KeyValue("판매금액", "${formatMoney(report.salesAmount)}원")
                    KeyValue("자재 사용금액", "${formatMoney(report.usageAmount)}원")
                    KeyValue("자재 구입금액", "${formatMoney(report.purchaseAmount)}원")
                    KeyValue("폐기(실패비용)", "${formatMoney(report.scrapCost)}원")
                    KeyValue("자재 투입비율", formatPct(report.materialRatio))
                    KeyValue("판정", report.grade)
                    KeyValue("단품 생산합계", report.producedQty.toString())
                }
            }
            item { SectionTitle("단품별") }
            items(ws.products, key = { it.id }) { p ->
                AppCard {
                    Text("NO.${p.codeNo}  ${p.name}", style = MaterialTheme.typography.titleMedium)
                    KeyValue("실적 / 계획", "${p.produced} / ${p.monthPlan}")
                    KeyValue("판매금액", "${formatMoney(p.salesAmount)}원")
                    KeyValue("투입자재비용", "${formatMoney(p.usageAmount)}원")
                    KeyValue("자재비율", formatPct(p.materialRatio))
                }
            }
            item { SectionTitle("자재별") }
            items(ws.materials.filter { it.usage > 0 || it.inbound > 0 || it.scrap > 0 }, key = { it.id }) { m ->
                AppCard {
                    Text("NO.${m.codeNo}  ${m.name}", style = MaterialTheme.typography.titleMedium)
                    KeyValue("사용량", "${formatQty(m.usage)} ${m.unit}")
                    KeyValue("입고", "${formatQty(m.inbound)} ${m.unit}")
                    KeyValue("현재고", "${formatQty(m.current)} ${m.unit}")
                    KeyValue("사용금액", "${formatMoney(m.usageAmount)}원")
                    KeyValue("입고금액", "${formatMoney(m.purchaseAmount)}원")
                }
            }
            item {
                TwoCol {
                    KpiTile("투입비율", formatPct(report.materialRatio), report.grade, Modifier.weight(1f))
                    KpiTile("폐기비용", formatMoney(report.scrapCost), "원", Modifier.weight(1f))
                }
            }
        }
    }
}
