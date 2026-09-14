package kr.baraplt.material.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.baraplt.material.MaterialApp
import kr.baraplt.material.data.BackupIo
import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.data.entity.ProductBomEntity
import kr.baraplt.material.data.entity.ProductEntity
import kr.baraplt.material.data.entity.FinishedGoodEntity
import kr.baraplt.material.data.entity.StockMovementEntity
import kr.baraplt.material.data.repo.Workspace
import kr.baraplt.material.domain.MovementType
import kr.baraplt.material.domain.UserRole
import kr.baraplt.material.domain.YearMonthKey
import java.time.LocalDate

data class AppUiState(
    val ready: Boolean = false,
    val workspace: Workspace? = null,
    val role: UserRole = UserRole.STAFF,
    val staffName: String = "담당자",
    val message: String? = null
) {
    val month: YearMonthKey get() = workspace?.month ?: YearMonthKey.current()
    val closed: Boolean get() = workspace?.closed == true
    val canEditMaster: Boolean get() = role == UserRole.MANAGER && !closed
    val canEditOps: Boolean get() = !closed
    val canClose: Boolean get() = role == UserRole.MANAGER
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MaterialApp
    private val repo = app.container.repository
    private val settings = app.container.settings

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUiState> = settings.workingMonth
        .flatMapLatest { month ->
            combine(
                combine(
                    repo.observeMaterials(),
                    repo.observeProducts(),
                    repo.observeFinished(),
                    repo.observeBom(),
                    repo.observeComposition()
                ) { _, _, _, _, _ -> 0 },
                combine(
                    repo.observeMonthMovements(month),
                    repo.observeProduction(month),
                    repo.observeCloses(),
                    repo.observeFinishedPlans(month),
                    settings.role
                ) { _, _, _, _, role -> role },
                settings.staffName,
                message
            ) { _, role, name, msg ->
                val ws = repo.buildWorkspace(month)
                AppUiState(ready = true, workspace = ws, role = role, staffName = name, message = msg)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    fun consumeMessage() {
        message.value = null
    }

    fun show(text: String) {
        message.value = text
    }

    fun shiftMonth(delta: Int) {
        viewModelScope.launch {
            val current = settings.workingMonth.first()
            var next = current
            repeat(kotlin.math.abs(delta)) {
                next = if (delta > 0) next.next() else next.previous()
            }
            settings.setWorkingMonth(next)
        }
    }

    fun setStaffName(name: String) {
        viewModelScope.launch { settings.setStaffName(name.ifBlank { "담당자" }) }
    }

    fun switchToStaff() {
        viewModelScope.launch { settings.setRole(UserRole.STAFF) }
    }

    fun switchToManager(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = pin == settings.managerPin.first()
            if (ok) settings.setRole(UserRole.MANAGER)
            onResult(ok)
        }
    }

    fun changePin(old: String, new: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = old == settings.managerPin.first() && new.length in 4..8
            if (ok) settings.setPin(new)
            onResult(ok)
        }
    }

    fun saveMaterial(item: MaterialEntity, opening: Double?) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                return@launch
            }
            val id = repo.saveMaterial(item)
            if (opening != null) {
                repo.setOpening(id, uiState.value.month, opening)
            }
            show("자재를 저장했습니다")
        }
    }

    fun saveProduct(item: ProductEntity, bom: List<ProductBomEntity>, plan: Int?) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                return@launch
            }
            if (bom.size > 30) {
                show("투입자재는 30개까지입니다")
                return@launch
            }
            val id = repo.saveProduct(item, bom)
            if (plan != null) repo.setProductPlan(id, uiState.value.month, plan)
            show("단품을 저장했습니다")
        }
    }

    fun saveFinished(item: FinishedGoodEntity, productIds: List<Long>, plan: Int?) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                return@launch
            }
            if (productIds.size > 15) {
                show("완제품 구성 단품은 15개까지입니다")
                return@launch
            }
            val id = repo.saveFinished(item, productIds)
            if (plan != null) repo.setFinishedPlan(id, uiState.value.month, plan)
            show("완제품을 저장했습니다")
        }
    }

    fun addMovement(materialId: Long, type: MovementType, qty: Double, date: String, note: String) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.closed) {
                show("마감된 달은 입력을 할 수 없습니다")
                return@launch
            }
            if (qty == 0.0) {
                show("수량을 입력하세요")
                return@launch
            }
            val mat = state.workspace?.materials?.firstOrNull { it.id == materialId }
            repo.addMovement(
                StockMovementEntity(
                    materialId = materialId,
                    type = type.name,
                    qty = qty,
                    unitPrice = mat?.unitPrice ?: 0.0,
                    occurredOn = date,
                    note = note,
                    createdBy = state.staffName
                )
            )
            show("${type.label} ${qty} 반영")
        }
    }

    fun deleteMovement(item: StockMovementEntity) {
        viewModelScope.launch {
            if (uiState.value.closed) {
                show("마감된 달은 삭제할 수 없습니다")
                return@launch
            }
            repo.deleteMovement(item)
            show("이력을 삭제했습니다")
        }
    }

    fun setProduction(productId: Long, day: Int, qty: Int) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.closed) {
                show("마감된 달은 실적을 수정하지 않습니다")
                return@launch
            }
            val date = "%s-%02d".format(state.month.value, day)
            repo.setProduction(productId, date, qty)
        }
    }

    fun applyStocktake(counts: Map<Long, Double>, date: String) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.role != UserRole.MANAGER) {
                show("재고조사 조정은 관리책임자 승인 후 반영됩니다")
                return@launch
            }
            if (state.closed) {
                show("마감된 달은 조정할 수 없습니다")
                return@launch
            }
            repo.applyStocktake(state.month, counts, state.staffName, date)
            show("재고조사 차이를 조정했습니다")
        }
    }

    fun closeMonth() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.role != UserRole.MANAGER) {
                show("마감은 관리책임자만 할 수 있습니다")
                return@launch
            }
            repo.closeMonth(state.month, state.staffName.ifBlank { "관리책임자" })
            show("${state.month.display()}을 마감하고 다음 달 시작재고를 넘겼습니다")
        }
    }

    fun reopenMonth() {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) return@launch
            repo.reopenMonth(uiState.value.month)
            show("마감을 해제했습니다")
        }
    }

    fun reloadSample() {
        viewModelScope.launch {
            repo.seedSample()
            settings.setWorkingMonth(YearMonthKey(2026, 8))
            show("엑셀 샘플 데이터를 다시 넣었습니다")
        }
    }

    suspend fun exportJson(): String = BackupIo.toJson(repo.exportBundle())

    fun importJson(text: String) {
        viewModelScope.launch {
            runCatching {
                repo.restoreBundle(BackupIo.fromJson(text))
                show("백업을 복원했습니다")
            }.onFailure { show("복원에 실패했습니다: ${it.message}") }
        }
    }

    suspend fun nextMaterialNo() = repo.nextMaterialNo()
    suspend fun nextProductNo() = repo.nextProductNo()
    suspend fun nextFinishedNo() = repo.nextFinishedNo()

    fun todayInMonth(): String {
        val month = uiState.value.month
        val today = LocalDate.now()
        val day = if (today.year == month.year && today.monthValue == month.month) today.dayOfMonth else 1
        return "%s-%02d".format(month.value, day)
    }
}
