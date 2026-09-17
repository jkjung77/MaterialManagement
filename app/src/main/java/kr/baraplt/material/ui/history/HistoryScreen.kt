package kr.baraplt.material.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.baraplt.material.domain.MovementType
import kr.baraplt.material.domain.formatQty
import kr.baraplt.material.ui.AppUiState
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.AppCard
import kr.baraplt.material.ui.components.AppScreenScaffold
import kr.baraplt.material.ui.components.GhostButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    var type by remember { mutableStateOf<String?>(null) }
    val materials = state.workspace?.materials?.associateBy { it.id }.orEmpty()
    val rows = state.workspace?.movements.orEmpty()
        .filter { type == null || it.type == type }
    AppScreenScaffold(title = "입출고 이력", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == null, onClick = { type = null }, label = { Text("전체") })
                    MovementType.entries.forEach { t ->
                        FilterChip(selected = type == t.name, onClick = { type = t.name }, label = { Text(t.label) })
                    }
                }
            }
            items(rows, key = { it.id }) { mv ->
                val mat = materials[mv.materialId]
                val label = runCatching { MovementType.valueOf(mv.type).label }.getOrDefault(mv.type)
                AppCard {
                    Text("$label  ${mat?.name ?: "?"}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${mv.occurredOn} · ${formatQty(mv.qty)} ${mat?.unit.orEmpty()} · ${mv.createdBy}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (mv.note.isNotBlank()) Text(mv.note, style = MaterialTheme.typography.bodyMedium)
                    if (state.canEditOps) {
                        GhostButton("삭제") { vm.deleteMovement(mv) }
                    }
                }
            }
            if (rows.isEmpty()) {
                item { Text("이달 이력이 없습니다.", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
