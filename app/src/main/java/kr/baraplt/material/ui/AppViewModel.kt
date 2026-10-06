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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kr.baraplt.material.domain.WorkspaceId
import kr.baraplt.material.domain.WorkspaceSecret
import kr.baraplt.material.MaterialApp
import kr.baraplt.material.data.BackupIo
import kr.baraplt.material.data.XlsxExport
import kr.baraplt.material.data.XlsxReader
import kr.baraplt.material.domain.BomImport
import kr.baraplt.material.data.sync.PendingAfterImport
import kr.baraplt.material.data.sync.SyncCoordinator
import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.data.entity.ProductBomEntity
import kr.baraplt.material.data.entity.ProductEntity
import kr.baraplt.material.data.entity.FinishedGoodEntity
import kr.baraplt.material.data.entity.StockMovementEntity
import kr.baraplt.material.data.repo.Workspace
import kr.baraplt.material.domain.MovementType
import kr.baraplt.material.domain.ScreenLabels
import kr.baraplt.material.domain.UserRole
import kr.baraplt.material.domain.YearMonthKey
import kr.baraplt.material.domain.formatQty
import java.time.LocalDate

data class AppUiState(
    val ready: Boolean = false,
    val needsWorkspace: Boolean = false,
    val needsWorkspacePassword: Boolean = false,
    val workspaceId: String = "",
    val workspace: Workspace? = null,
    val role: UserRole = UserRole.STAFF,
    val staffName: String = "담당자",
    val serverLinked: Boolean = false,
    val pendingCount: Int = 0,
    val message: String? = null,
    val photoEpoch: Int = 0,
    val gradeGood: Int = 55,
    val gradeNormal: Int = 70
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
    private val repo get() = app.container.repository
    private val settings = app.container.settings

    private val message = MutableStateFlow<String?>(null)
    private val photoEpoch = MutableStateFlow(0)

    val uiState: StateFlow<AppUiState> = settings.workspaceId
        .flatMapLatest { rawId ->
            val id = WorkspaceId.normalize(rawId)
            if (id.isEmpty()) {
                message.map { msg ->
                    AppUiState(ready = true, needsWorkspace = true, message = msg)
                }
            } else {
                settings.workspaceSecrets.flatMapLatest { secrets ->
                    if (!secrets.containsKey(id)) {
                        message.map { msg ->
                            AppUiState(
                                ready = true,
                                needsWorkspacePassword = true,
                                workspaceId = id,
                                message = msg
                            )
                        }
                    } else {
                        app.container.bind(id)
                        settings.workingMonth.flatMapLatest { month ->
                            combine(
                                combine(
                                    combine(
                                        repo.observeMaterials(),
                                        repo.observeProducts(),
                                        repo.observeFinished(),
                                        repo.observeBom(),
                                        repo.observeComposition()
                                    ) { _, _, _, _, _ -> 0 },
                                    repo.observeFinishedOutput(month),
                                    repo.observeProductOpenings(month)
                                ) { _, _, _ -> 0 },
                                combine(
                                    repo.observeMonthMovements(month),
                                    repo.observeProduction(month),
                                    repo.observeCloses(),
                                    repo.observeFinishedPlans(month),
                                    settings.role
                                ) { _, _, _, _, role -> role },
                                combine(
                                    combine(
                                        settings.staffName,
                                        settings.serverLinked,
                                        settings.pendingQueue,
                                        message
                                    ) { name, linked, queue, msg -> arrayOf(name, linked, queue, msg) },
                                    combine(photoEpoch, settings.gradeBounds) { epoch, bounds ->
                                        arrayOf(epoch, bounds)
                                    }
                                ) { left, right ->
                                    arrayOf(left[0], left[1], left[2], left[3], right[0], right[1])
                                }
                            ) { _, role, extra ->
                                val name = extra[0] as String
                                val linked = extra[1] as Boolean
                                val queue = extra[2] as String
                                val msg = extra[3] as String?
                                val epoch = extra[4] as Int
                                val bounds = extra[5] as Pair<*, *>
                                val ws = repo.buildWorkspace(month)
                                AppUiState(
                                    ready = true,
                                    workspaceId = id,
                                    workspace = ws,
                                    role = role,
                                    staffName = name,
                                    serverLinked = linked,
                                    pendingCount = runCatching { JSONArray(queue).length() }.getOrDefault(0),
                                    message = msg,
                                    photoEpoch = epoch,
                                    gradeGood = bounds.first as Int,
                                    gradeNormal = bounds.second as Int
                                )
                            }
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    val screenLabels: StateFlow<ScreenLabels> =
        settings.screenLabels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenLabels())

    fun saveScreenLabels(labels: ScreenLabels, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("부제목은 관리책임자만 바꿀 수 있습니다")
                onDone(false)
                return@launch
            }
            val err = withContext(Dispatchers.IO) { app.container.sync().saveScreenLabels(labels) }
            show(err ?: "부제목을 저장했습니다. 다른 폰은 「서버와 맞추기」 뒤에 바뀝니다")
            onDone(err == null)
        }
    }

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
            val state = uiState.value
            if (!state.serverLinked) return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    delay(400)
                    if (settings.workingMonth.first() != next) null
                    else app.container.sync().syncNow(next, state.role)
                }
            }.onSuccess { text ->
                if (text == null) return@onSuccess
                photoEpoch.value = photoEpoch.value + 1
                if (text.contains("로그인") || text.contains("거절")) show(text)
            }
        }
    }

    fun setGradeBounds(goodPercent: Int, normalPercent: Int) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("비율 기준은 관리책임자만 바꿀 수 있습니다")
                return@launch
            }
            if (goodPercent !in 1..97 || normalPercent !in (goodPercent + 1)..99) {
                show("좋음 기준은 보통 기준보다 작아야 합니다")
                return@launch
            }
            settings.setGradeBounds(goodPercent, normalPercent)
            show("자재투입비율 기준을 저장했습니다")
        }
    }

    fun setStaffName(name: String) {
        viewModelScope.launch { settings.setStaffName(name.ifBlank { "담당자" }) }
    }

    fun joinWorkspace(
        rawId: String,
        password: String,
        confirm: String? = null,
        onResult: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val idError = WorkspaceId.validate(rawId)
            if (idError != null) {
                onResult(idError)
                return@launch
            }
            val id = WorkspaceId.normalize(rawId)
            val secrets = settings.workspaceSecrets.first()
            val known = secrets.containsKey(id)
            if (!known) {
                val pwError = WorkspaceSecret.validate(password, confirm ?: password)
                if (pwError != null) {
                    onResult(pwError)
                    return@launch
                }
            }
            val staff = settings.staffName.first()
            val serverErr = syncLogin(id, password, staff)
            if (serverErr == null) {
                settings.setWorkspaceSecret(id, password)
                settings.setWorkspaceId(id)
                app.container.bind(id)
                show(withContext(Dispatchers.IO) { app.container.sync().afterLogin(settings.workingMonth.first()) })
                photoEpoch.value = photoEpoch.value + 1
                onResult(null)
                return@launch
            }
            val offlineOk = known && settings.verifyWorkspaceSecret(id, password) &&
                serverErr.contains("연결")
            if (offlineOk) {
                settings.clearTokens()
                settings.setWorkspaceId(id)
                show("오프라인으로 입장했습니다. 통신되면 서버와 맞춥니다")
                onResult(null)
                return@launch
            }
            onResult(serverErr)
        }
    }

    fun setWorkspacePassword(password: String, confirm: String, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val id = WorkspaceId.normalize(settings.workspaceId.first())
            if (id.isEmpty()) {
                onResult("공장 ID를 먼저 입력하세요")
                return@launch
            }
            val pwError = WorkspaceSecret.validate(password, confirm)
            if (pwError != null) {
                onResult(pwError)
                return@launch
            }
            val staff = settings.staffName.first()
            val serverErr = syncLogin(id, password, staff)
            if (serverErr == null) {
                settings.setWorkspaceSecret(id, password)
                app.container.bind(id)
                show(withContext(Dispatchers.IO) { app.container.sync().afterLogin(settings.workingMonth.first()) })
                photoEpoch.value = photoEpoch.value + 1
                onResult(null)
                return@launch
            }
            if (serverErr.contains("연결")) {
                settings.setWorkspaceSecret(id, password)
                onResult(null)
                return@launch
            }
            onResult(serverErr)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.workspaceId.isBlank()) return@launch
            runCatching {
                withContext(Dispatchers.IO) { app.container.sync().syncNow(state.month, state.role) }
            }
                .onSuccess {
                    photoEpoch.value = photoEpoch.value + 1
                    show(it)
                }
                .onFailure { show(it.message ?: "동기화에 실패했습니다") }
        }
    }

    private suspend fun syncLogin(id: String, password: String, staff: String): String? {
        return SyncCoordinator(settings, app.container.api, null).loginOrCreate(id, password, staff)
    }

    private suspend fun syncAfterChange() {
        val state = uiState.value
        if (!state.serverLinked) return
        runCatching {
            withContext(Dispatchers.IO) { app.container.sync().syncNow(state.month, state.role) }
        }.onSuccess { text ->
            photoEpoch.value = photoEpoch.value + 1
            if (text.contains("로그인") || text.contains("거절") || text.contains("이 폰에 저장")) show(text)
        }
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

    fun changeWorkspacePassword(
        current: String,
        next: String,
        confirm: String,
        onResult: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                val msg = "공장 암호는 관리책임자만 바꿀 수 있습니다"
                show(msg)
                onResult(msg)
                return@launch
            }
            val err = withContext(Dispatchers.IO) {
                app.container.sync().changeWorkspacePassword(current, next, confirm)
            }
            show(err ?: "공장 암호를 변경했습니다. 다른 폰은 새 암호로 다시 입장하세요")
            onResult(err)
        }
    }

    private suspend fun applyItemPhoto(photo: ItemPhotoEdit) {
        if (photo.kind != "material" && photo.kind != "product") return
        if (photo.newCode !in 1..500) return
        withContext(Dispatchers.IO) {
            val store = app.container.photos()
            val previous = photo.previousCode?.takeIf { it in 1..500 && it != photo.newCode }
            when {
                photo.removed -> {
                    if (previous != null) store.tombstone(photo.kind, previous)
                    store.tombstone(photo.kind, photo.newCode)
                }
                photo.jpeg != null -> {
                    if (previous != null) store.tombstone(photo.kind, previous)
                    store.saveJpeg(photo.kind, photo.newCode, photo.jpeg)
                }
                previous != null -> store.move(photo.kind, previous, photo.newCode)
            }
        }
        photoEpoch.value = photoEpoch.value + 1
    }

    suspend fun saveMaterial(item: MaterialEntity, opening: Double?, photo: ItemPhotoEdit? = null): Boolean {
        if (uiState.value.role != UserRole.MANAGER) {
            show("기초정보는 관리책임자만 수정할 수 있습니다")
            return false
        }
        val taken = uiState.value.workspace?.materials?.firstOrNull { it.codeNo == item.codeNo && it.id != item.id }
        if (taken != null) {
            show("자재번호 ${item.codeNo}은(는) 이미 「${taken.name}」이 쓰고 있습니다")
            return false
        }
        val id = runCatching { repo.saveMaterial(item) }.getOrElse {
            show("자재 저장 실패: ${it.message}")
            return false
        }
        if (opening != null) {
            repo.setOpening(id, uiState.value.month, opening)
        }
        settings.markMastersDirty()
        if (photo != null) applyItemPhoto(photo)
        show("자재를 저장했습니다")
        syncAfterChange()
        return true
    }

    fun deleteMaterial(id: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                onDone(false)
                return@launch
            }
            val mat = uiState.value.workspace?.materials?.firstOrNull { it.id == id }
            val err = repo.deleteMaterial(id)
            if (err != null) {
                show(err)
                onDone(false)
                return@launch
            }
            settings.enqueue(
                SyncCoordinator.deleteMaterial(id, mat?.codeNo ?: 0)
            )
            if (mat != null && mat.codeNo in 1..500) {
                withContext(Dispatchers.IO) { app.container.photos().tombstone("material", mat.codeNo) }
                photoEpoch.value = photoEpoch.value + 1
            }
            show("자재를 삭제했습니다")
            syncAfterChange()
            onDone(true)
        }
    }

    fun saveProduct(
        item: ProductEntity,
        bom: List<ProductBomEntity>,
        plan: Int?,
        photo: ItemPhotoEdit? = null,
        opening: Int? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                onDone(false)
                return@launch
            }
            if (bom.size > 30) {
                show("투입자재는 30개까지입니다")
                onDone(false)
                return@launch
            }
            val taken = uiState.value.workspace?.products?.firstOrNull { it.codeNo == item.codeNo && it.id != item.id }
            if (taken != null) {
                show("단품번호 ${item.codeNo}은(는) 이미 「${taken.name}」이 쓰고 있습니다")
                onDone(false)
                return@launch
            }
            val id = runCatching { repo.saveProduct(item, bom) }.getOrElse {
                show("단품 저장 실패: ${it.message}")
                onDone(false)
                return@launch
            }
            if (plan != null) repo.setProductPlan(id, uiState.value.month, plan)
            if (opening != null) repo.setProductOpening(id, uiState.value.month, opening)
            settings.markMastersDirty()
            if (photo != null) applyItemPhoto(photo)
            show("단품을 저장했습니다")
            onDone(true)
            syncAfterChange()
        }
    }

    fun deleteProduct(id: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                onDone(false)
                return@launch
            }
            val product = uiState.value.workspace?.products?.firstOrNull { it.id == id }
            val err = repo.deleteProduct(id)
            if (err != null) {
                show(err)
                onDone(false)
                return@launch
            }
            settings.enqueue(SyncCoordinator.deleteProduct(id, product?.codeNo ?: 0))
            if (product != null && product.codeNo in 1..500) {
                withContext(Dispatchers.IO) { app.container.photos().tombstone("product", product.codeNo) }
                photoEpoch.value = photoEpoch.value + 1
            }
            show("단품을 삭제했습니다")
            syncAfterChange()
            onDone(true)
        }
    }

    fun saveFinished(item: FinishedGoodEntity, lines: List<Pair<Long, Int>>, plan: Int?, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                onDone(false)
                return@launch
            }
            if (lines.size > 30) {
                show("완성품 구성 단품은 30개까지입니다")
                onDone(false)
                return@launch
            }
            val taken = uiState.value.workspace?.finished?.firstOrNull { it.codeNo == item.codeNo && it.id != item.id }
            if (taken != null) {
                show("완제품번호 ${item.codeNo}은(는) 이미 「${taken.name}」이 쓰고 있습니다")
                onDone(false)
                return@launch
            }
            val id = runCatching { repo.saveFinished(item, lines) }.getOrElse {
                show("완제품 저장 실패: ${it.message}")
                onDone(false)
                return@launch
            }
            if (plan != null) repo.setFinishedPlan(id, uiState.value.month, plan)
            else repo.recordFinishedPlansOnProducts(uiState.value.month)
            settings.markMastersDirty()
            show("완성품을 저장했습니다. 같은 단품은 완성품 계획 합계가 단품 계획에 반영됩니다")
            onDone(true)
            syncAfterChange()
        }
    }

    fun setFinishedProduction(finishedId: Long, day: Int, qty: Int) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.closed) {
                show("마감된 달은 실적을 수정하지 않습니다")
                return@launch
            }
            val date = "%s-%02d".format(state.month.value, day)
            val item = state.workspace?.finished?.firstOrNull { it.id == finishedId }
            repo.setFinishedProduction(finishedId, date, qty)
            settings.enqueue(SyncCoordinator.finishedProduction(finishedId, date, qty, item?.codeNo ?: 0))
            show(if (qty > 0) "완성품 실적을 저장했습니다. 구성 단품 재고가 빠집니다" else "완성품 실적을 지웠습니다")
            syncAfterChange()
        }
    }

    fun setProductOpening(productId: Long, qty: Int) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.role != UserRole.MANAGER) {
                show("시작재고는 관리책임자만 바꿀 수 있습니다")
                return@launch
            }
            if (state.closed) {
                show("마감된 달은 시작재고를 바꾸지 않습니다")
                return@launch
            }
            repo.setProductOpening(productId, state.month, qty)
            settings.markMastersDirty()
            show("단품 시작재고를 저장했습니다")
            syncAfterChange()
        }
    }

    fun deleteFinished(id: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("기초정보는 관리책임자만 수정할 수 있습니다")
                onDone(false)
                return@launch
            }
            val item = uiState.value.workspace?.finished?.firstOrNull { it.id == id }
            val err = repo.deleteFinished(id)
            if (err != null) {
                show(err)
                onDone(false)
                return@launch
            }
            settings.enqueue(SyncCoordinator.deleteFinished(id, item?.codeNo ?: 0))
            show("완성품을 삭제했습니다")
            syncAfterChange()
            onDone(true)
        }
    }

    fun addMovement(
        materialId: Long,
        type: MovementType,
        qty: Double,
        date: String,
        note: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.closed) {
                show("마감된 달은 입력을 할 수 없습니다")
                onDone(false)
                return@launch
            }
            if (!(qty > 0.0) || qty.isInfinite()) {
                show("수량은 0보다 크게 입력하세요")
                onDone(false)
                return@launch
            }
            if (!date.startsWith(state.month.value + "-") || runCatching { java.time.LocalDate.parse(date) }.isFailure) {
                show("일자는 ${state.month.value}-DD 형식으로 이달 날짜를 넣으세요")
                onDone(false)
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
            settings.enqueue(
                SyncCoordinator.movement(
                    materialId, type.name, qty, mat?.unitPrice ?: 0.0, date, note, mat?.codeNo ?: 0
                )
            )
            show("${type.label} ${formatQty(qty)} 반영")
            onDone(true)
            syncAfterChange()
        }
    }

    private suspend fun enqueueMovementDelete(item: StockMovementEntity, codeNo: Int) {
        val target = PendingAfterImport.MovementKey(codeNo, item.materialId, item.type, item.qty, item.occurredOn, item.unitPrice)
        val cancelled = PendingAfterImport.cancelUnsentMovement(settings.pendingItems(), target)
        if (cancelled != null) {
            settings.setPendingItems(cancelled)
            return
        }
        settings.enqueue(
            SyncCoordinator.deleteMovement(
                item.id, item.materialId, item.type, item.qty, item.unitPrice, item.occurredOn, codeNo
            )
        )
    }

    fun deleteMovement(item: StockMovementEntity) {
        viewModelScope.launch {
            if (uiState.value.closed) {
                show("마감된 달은 삭제할 수 없습니다")
                return@launch
            }
            val mat = uiState.value.workspace?.materials?.firstOrNull { it.id == item.materialId }
            enqueueMovementDelete(item, mat?.codeNo ?: 0)
            repo.deleteMovement(item)
            show("이력을 삭제했습니다")
            syncAfterChange()
        }
    }

    fun replaceMovement(old: StockMovementEntity, qty: Double, date: String, note: String) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.closed) {
                show("마감된 달은 수정할 수 없습니다")
                return@launch
            }
            if (qty < 0.0 || qty.isNaN() || qty.isInfinite()) {
                show("수량은 0보다 크게 입력하세요")
                return@launch
            }
            val mat = state.workspace?.materials?.firstOrNull { it.id == old.materialId }
            val codeNo = mat?.codeNo ?: 0
            enqueueMovementDelete(old, codeNo)
            repo.deleteMovement(old)
            if (qty == 0.0) {
                show("이력을 삭제했습니다")
                syncAfterChange()
                return@launch
            }
            val occurredOn = date.ifBlank { old.occurredOn }
            val label = runCatching { MovementType.valueOf(old.type).label }.getOrDefault(old.type)
            repo.addMovement(
                StockMovementEntity(
                    materialId = old.materialId,
                    type = old.type,
                    qty = qty,
                    unitPrice = old.unitPrice,
                    occurredOn = occurredOn,
                    note = note,
                    createdBy = state.staffName
                )
            )
            settings.enqueue(
                SyncCoordinator.movement(
                    old.materialId, old.type, qty, old.unitPrice, occurredOn, note, codeNo
                )
            )
            show("$label $qty 로 고쳤습니다")
            syncAfterChange()
        }
    }

    fun setProduction(productId: Long, day: Int, qty: Int, defect: Int = 0) {
        viewModelScope.launch {
            val state = uiState.value
            if (state.closed) {
                show("마감된 달은 실적을 수정하지 않습니다")
                return@launch
            }
            val date = "%s-%02d".format(state.month.value, day)
            val product = state.workspace?.products?.firstOrNull { it.id == productId }
            repo.setProduction(productId, date, qty)
            settings.enqueue(SyncCoordinator.production(productId, date, qty, product?.codeNo ?: 0))
            val marker = "생산불량 NO.${product?.codeNo ?: 0} $date"
            val olds = state.workspace?.movements?.filter { it.occurredOn == date && it.note == marker }.orEmpty()
            olds.forEach { old ->
                val mat = state.workspace?.materials?.firstOrNull { it.id == old.materialId }
                enqueueMovementDelete(old, mat?.codeNo ?: 0)
                repo.deleteMovement(old)
            }
            if (defect > 0 && product != null) {
                product.bom.forEach { line ->
                    val scrapQty = defect * line.usQty
                    if (scrapQty <= 0.0) return@forEach
                    val mat = state.workspace?.materials?.firstOrNull { it.id == line.materialId }
                    repo.addMovement(
                        kr.baraplt.material.data.entity.StockMovementEntity(
                            materialId = line.materialId,
                            type = kr.baraplt.material.domain.MovementType.SCRAP.name,
                            qty = scrapQty,
                            unitPrice = mat?.unitPrice ?: line.unitPrice,
                            occurredOn = date,
                            note = marker,
                            createdBy = state.staffName
                        )
                    )
                    settings.enqueue(
                        SyncCoordinator.movement(
                            line.materialId,
                            kr.baraplt.material.domain.MovementType.SCRAP.name,
                            scrapQty,
                            mat?.unitPrice ?: line.unitPrice,
                            date,
                            marker,
                            mat?.codeNo ?: line.materialNo
                        )
                    )
                }
            }
            show(if (defect > 0) "생산 $qty, 불량 $defect 를 저장했습니다" else "생산실적을 저장했습니다")
            syncAfterChange()
        }
    }

    fun applyStocktake(counts: Map<Long, Double>, notes: Map<Long, String>, date: String) {
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
            repo.applyStocktake(state.month, counts, notes, state.staffName, date)
            val codes = state.workspace?.materials?.associate { it.id to it.codeNo }.orEmpty()
            settings.enqueue(SyncCoordinator.stocktake(state.month.value, date, counts, codes, notes))
            show("재고조사 차이를 조정했습니다")
            syncAfterChange()
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
            settings.enqueue(SyncCoordinator.close(state.month.value))
            show("${state.month.display()}을 마감하고 다음 달 시작재고를 넘겼습니다")
            syncAfterChange()
        }
    }

    fun reopenMonth() {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) return@launch
            repo.reopenMonth(uiState.value.month)
            settings.enqueue(SyncCoordinator.reopen(uiState.value.month.value))
            show("마감을 해제했습니다. 고친 뒤 다시 마감해야 다음 달 시작재고가 새로 맞춰집니다")
            syncAfterChange()
        }
    }

    fun reloadSample() {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("샘플 넣기는 관리책임자만 할 수 있습니다")
                return@launch
            }
            repo.seedSample()
            settings.markMastersDirty()
            settings.setWorkingMonth(YearMonthKey(2026, 8))
            show("엑셀 샘플 데이터를 이 폰에만 넣었습니다. 서버에 올리려면 「서버와 맞추기」를 누르세요")
        }
    }

    suspend fun exportJson(): String = BackupIo.toJson(repo.exportBundle())

    suspend fun exportXlsx(): ByteArray {
        val state = uiState.value
        return XlsxExport.toBytes(repo.exportBundle(), state.workspace, state.workspaceId)
    }

    suspend fun planBomImport(bytes: ByteArray): BomImport.Plan? {
        if (uiState.value.role != UserRole.MANAGER) {
            show("투입자재 가져오기는 관리책임자만 할 수 있습니다")
            return null
        }
        val sheets = withContext(Dispatchers.IO) { XlsxReader.sheets(bytes) }
        val rows = (listOfNotNull(sheets["BOM"]) + sheets.values).firstOrNull { BomImport.hasHeader(it) }
        if (rows == null) {
            show("BOM 시트(단품번호·자재번호·US 칸)를 찾지 못했습니다. 「엑셀로 저장」한 파일을 고쳐서 쓰세요")
            return null
        }
        return repo.planBomImport(rows)
    }

    fun applyBomImport(plan: BomImport.Plan) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("투입자재 가져오기는 관리책임자만 할 수 있습니다")
                return@launch
            }
            if (plan.changes.isEmpty()) return@launch
            runCatching { repo.applyBomImport(plan.changes) }
                .onFailure {
                    show("투입자재 반영 실패: ${it.message}")
                    return@launch
                }
            settings.markMastersDirty()
            show("단품 ${plan.changes.size}개의 투입자재를 바꿨습니다")
            syncAfterChange()
        }
    }

    fun importJson(text: String) {
        viewModelScope.launch {
            if (uiState.value.role != UserRole.MANAGER) {
                show("백업 복원은 관리책임자만 할 수 있습니다")
                return@launch
            }
            runCatching {
                repo.restoreBundle(BackupIo.fromJson(text))
                settings.markMastersDirty()
                show("백업을 이 폰에만 복원했습니다. 서버에 올리려면 「서버와 맞추기」를 누르세요")
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

data class ItemPhotoEdit(
    val kind: String,
    val previousCode: Int?,
    val newCode: Int,
    val jpeg: ByteArray?,
    val removed: Boolean
)
