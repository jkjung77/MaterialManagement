package kr.baraplt.material.ui.finished

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kr.baraplt.material.domain.formatMoney
import kr.baraplt.material.domain.formatPct
import kr.baraplt.material.domain.parseNumber
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.GhostButton
import kr.baraplt.material.ui.components.KeyValue
import kr.baraplt.material.ui.components.NumberField
import kr.baraplt.material.ui.components.PrimaryButton
import kr.baraplt.material.ui.components.SectionTitle
import kr.baraplt.material.ui.components.TextFieldPlain
import kr.baraplt.material.ui.theme.Card
import kr.baraplt.material.ui.theme.Ink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedListScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("완제품 · 월계획") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Card),
                actions = {
                    if (state.role.name == "MANAGER") {
                        IconButton(onAdd) { Icon(Icons.Default.Add, null) }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "고객사 월 생산계획만 관리합니다. 일 판매실적은 사용하지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            items(state.workspace?.finished.orEmpty(), key = { it.id }) { f ->
                AppCard(onClick = { if (state.role.name == "MANAGER") onEdit(f.id) }) {
                    Text("NO.${f.codeNo}  ${f.name}", style = MaterialTheme.typography.titleMedium)
                    KeyValue("판매단가", "${formatMoney(f.sellPrice)}원")
                    KeyValue("완제품 자재비", "${formatMoney(f.materialCost)}원")
                    KeyValue("자재비율", formatPct(f.materialRatio))
                    KeyValue("월 생산계획", f.monthPlan.toString())
                    Text("구성: ${f.productNames.joinToString(" + ").ifBlank { "-"} }", style = MaterialTheme.typography.bodyMedium)
                    if (state.canEditOps && state.role.name == "MANAGER") {
                        Spacer(Modifier.height(8.dp))
                        var plan by remember(f.id, f.monthPlan) { mutableStateOf(f.monthPlan.takeIf { it > 0 }?.toString().orEmpty()) }
                        NumberField(plan, { plan = it }, "이달 월계획 수정")
                        GhostButton("계획 저장") {
                            val q = parseNumber(plan)?.toInt() ?: 0
                            vm.saveFinished(
                                FinishedGoodEntity(id = f.id, codeNo = f.codeNo, name = f.name, sellPrice = f.sellPrice),
                                f.productIds,
                                q
                            )
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
    val existing = state.workspace?.finished?.firstOrNull { it.id == existingId }
    val products = state.workspace?.products.orEmpty()
    var code by remember { mutableStateOf(existing?.codeNo?.toString().orEmpty()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var price by remember { mutableStateOf(existing?.sellPrice?.takeIf { it > 0 }?.toLong()?.toString().orEmpty()) }
    var plan by remember { mutableStateOf(existing?.monthPlan?.takeIf { it > 0 }?.toString().orEmpty()) }
    val selected = remember {
        mutableStateListOf<Long>().also { it.addAll(existing?.productIds.orEmpty()) }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(existingId) {
        if (existing == null && code.isEmpty()) code = vm.nextFinishedNo().toString()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "완제품 등록" else "완제품 수정") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Card)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { NumberField(code, { code = it }, "완제품번호 (1~26)") }
            item { TextFieldPlain(name, { name = it }, "완제품명(Item)") }
            item { NumberField(price, { price = it }, "완제품 판매단가", suffix = "원") }
            item { NumberField(plan, { plan = it }, "이달 월 생산계획") }
            item { SectionTitle("구성 단품 (최대 15)") }
            items(products, key = { it.id }) { p ->
                val on = p.id in selected
                FilterChip(
                    selected = on,
                    onClick = {
                        if (on) selected.remove(p.id)
                        else if (selected.size < 15) selected.add(p.id)
                    },
                    label = { Text("NO.${p.codeNo} ${p.name}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                PrimaryButton("저장", onClick = {
                    val no = parseNumber(code)?.toInt()
                    if (no == null || no !in 1..26 || name.isBlank()) {
                        vm.show("번호(1~26)와 완제품명을 확인하세요")
                        return@PrimaryButton
                    }
                    scope.launch {
                        vm.saveFinished(
                            FinishedGoodEntity(
                                id = existing?.id ?: 0,
                                codeNo = no,
                                name = name.trim(),
                                sellPrice = parseNumber(price) ?: 0.0
                            ),
                            selected.toList(),
                            parseNumber(plan)?.toInt()
                        )
                        onBack()
                    }
                }, enabled = state.role.name == "MANAGER")
            }
        }
    }
}
