package kr.baraplt.material.data.sync

import kr.baraplt.material.data.BackupIo
import kr.baraplt.material.data.ItemPhotoStore
import kr.baraplt.material.data.SettingsStore
import kr.baraplt.material.data.repo.AppRepository
import kr.baraplt.material.domain.PhotoStamp
import kr.baraplt.material.domain.PhotoSyncAction
import kr.baraplt.material.domain.PhotoSyncPlan
import kr.baraplt.material.domain.MonthCarry
import kr.baraplt.material.domain.ScreenLabels
import kr.baraplt.material.domain.UserRole
import kr.baraplt.material.domain.WorkspaceId
import kr.baraplt.material.domain.WorkspacePasswordChange
import kr.baraplt.material.domain.YearMonthKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SyncCoordinator(
    private val settings: SettingsStore,
    private val api: MaterialApi,
    private val repo: AppRepository?,
    private val photos: ItemPhotoStore? = null
) {
    private fun db(): AppRepository = repo ?: error("공장이 아직 연결되지 않았습니다")
    suspend fun loginOrCreate(workspaceId: String, password: String, staffName: String): String? {
        return when (val result = api.login(workspaceId, password, staffName)) {
            is ApiResult.Ok -> {
                applyLogin(result.data)
                null
            }
            is ApiResult.Err -> when (result.code) {
                "WORKSPACE_AUTH" -> createThenLogin(workspaceId, password, staffName, result.message)
                "NETWORK" -> "서버에 연결할 수 없습니다"
                else -> result.message
            }
        }
    }

    private suspend fun createThenLogin(
        workspaceId: String,
        password: String,
        staffName: String,
        loginMessage: String
    ): String? {
        return when (val created = api.createWorkspace(workspaceId, password, staffName)) {
            is ApiResult.Ok -> {
                applyLogin(created.data)
                null
            }
            is ApiResult.Err -> if (created.code == "DUPLICATE_WORKSPACE") loginMessage else created.message
        }
    }

    private suspend fun applyLogin(data: LoginData) {
        settings.setTokens(data.accessToken, data.refreshToken)
        settings.setStaffName(data.userName.ifBlank { settings.staffName.first() })
        runCatching { settings.setRole(UserRole.valueOf(data.role)) }
    }

    suspend fun changeWorkspacePassword(current: String, next: String, confirm: String): String? {
        val err = WorkspacePasswordChange.validate(current, next, confirm)
        if (err != null) return err
        val id = WorkspaceId.normalize(settings.workspaceId.first())
        if (id.isBlank()) return "공장에 입장하세요"
        val known = settings.workspaceSecrets.first().containsKey(id)
        if (known && !settings.verifyWorkspaceSecret(id, current)) {
            return "현재 공장 암호가 올바르지 않습니다"
        }
        val token = requireToken() ?: return "서버에 연결되어 있지 않습니다. 공장에 다시 입장하세요"
        return when (val result = api.changeWorkspacePassword(token, current.trim(), next.trim())) {
            is ApiResult.Err -> when (result.code) {
                "FORBIDDEN" -> "관리책임자 이름으로 공장 암호를 넣고 「공장 전환」을 누르세요"
                "UNAUTHORIZED" -> "다시 로그인하세요"
                "WORKSPACE_AUTH" -> "현재 공장 암호가 올바르지 않습니다"
                else -> result.message
            }
            is ApiResult.Ok -> {
                settings.setWorkspaceSecret(id, next.trim())
                loginOrCreate(id, next.trim(), settings.staffName.first())
            }
        }
    }

    suspend fun afterLogin(month: YearMonthKey): String {
        val token = requireToken() ?: return "서버 토큰이 없습니다"
        val snap = snapshot(token, month) ?: return "서버에서 데이터를 받지 못했습니다"
        val serverHas = snap.optJSONArray("materials")?.length() ?: 0
        val localHas = db().exportBundle().materials.isNotEmpty()
        if (serverHas == 0 && localHas) {
            val imported = api.importBundle(token, JSONObject(BackupIo.toJson(db().exportBundle())))
            if (imported is ApiResult.Err) {
                if (imported.code == "FORBIDDEN") {
                    restoreSnapshot(snap)
                    settings.setLastSync(System.currentTimeMillis())
                    return withPhotos(token, "서버에 연결했습니다. 기초정보는 관리책임자가 올립니다")
                }
                return imported.message
            }
            val again = snapshot(token, month) ?: return "서버에 올렸으나 다시 받지 못했습니다"
            restoreSnapshot(again)
            settings.setPendingItems(PendingAfterImport.dropUploadedKinds(settings.pendingItems()))
        } else {
            restoreSnapshot(snap)
            settings.setPendingItems(
                PendingAfterImport.dropAlreadyOnServer(
                    settings.pendingItems(),
                    snap.optJSONArray("movements")
                )
            )
        }
        settings.setLastSync(System.currentTimeMillis())
        return withPhotos(token, "서버와 연결했습니다")
    }

    suspend fun syncNow(month: YearMonthKey, role: UserRole): String = syncLock.withLock { syncNowLocked(month, role) }

    private suspend fun syncNowLocked(month: YearMonthKey, role: UserRole): String {
        var token = requireToken() ?: return "서버에 연결되어 있지 않습니다. 공장에 다시 입장하세요"
        token = refreshIfNeeded(token)
        refreshPendingIds()
        var pending = settings.pendingItems()
        var keptNotice = ""
        val doneMovementDeletes = mutableSetOf<Int>()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            if (item.optString("kind") !in DELETE_KINDS) continue
            val deleted = when (item.optString("kind")) {
                "delete_material" -> api.deleteMaterial(token, item)
                "delete_product" -> api.deleteProduct(token, item)
                "delete_finished" -> api.deleteFinished(token, item)
                else -> api.deleteMovement(token, item)
            }
            when (deleted) {
                is ApiResult.Err -> {
                    if (deleted.code == "UNAUTHORIZED") {
                        settings.clearTokens()
                        return "다시 로그인하세요"
                    }
                    if (deleted.code == "MONTH_CLOSED" && item.optString("kind") == "delete_movement") {
                        doneMovementDeletes += i
                        keptNotice = "마감된 달 이력은 서버에서 지우지 않았습니다"
                    }
                }
                is ApiResult.Ok -> if (item.optString("kind") == "delete_movement") doneMovementDeletes += i
            }
        }
        if (doneMovementDeletes.isNotEmpty()) {
            val next = JSONArray()
            for (i in 0 until pending.length()) if (i !in doneMovementDeletes) next.put(pending.get(i))
            pending = next
            settings.setPendingItems(pending)
        }
        val localBundle = db().exportBundle()
        var probe: JSONObject? = null
        if (role == UserRole.MANAGER && localBundle.materials.isNotEmpty()) {
            probe = snapshot(token, month)
            val serverCount = probe?.optJSONArray("materials")?.length() ?: 0
            if (serverCount == 0) {
                val imported = api.importBundle(token, JSONObject(BackupIo.toJson(localBundle)))
                if (imported is ApiResult.Err && imported.code == "FORBIDDEN") {
                    return "설정에서 공장 암호를 넣고 「공장 전환」을 누르세요. 아래 PIN 관리책임자와 서버 권한은 다릅니다. 이 폰 입고는 그대로 있습니다"
                }
                if (imported is ApiResult.Ok) {
                    pending = PendingAfterImport.dropUploadedKinds(pending)
                    settings.setPendingItems(pending)
                }
            }
        }
        if (probe == null) probe = snapshot(token, month)
        pending = PendingAfterImport.dropAlreadyOnServer(pending, probe?.optJSONArray("movements"))
        settings.setPendingItems(pending)
        val dirtyStamp = settings.mastersDirtyAt()
        val pushBody = buildPush(pending, role == UserRole.MANAGER && dirtyStamp > 0)
        if (pushBody.length() > 0) {
            when (val pushed = api.push(token, pushBody)) {
                is ApiResult.Err -> {
                    if (pushed.code == "UNAUTHORIZED") {
                        settings.clearTokens()
                        return "다시 로그인하세요"
                    }
                    return pushed.message
                }
                is ApiResult.Ok -> {
                    val rejected = pushed.data.optJSONArray("rejected") ?: JSONArray()
                    pending = PendingAfterImport.dropAccepted(pending, pushed.data.optJSONArray("accepted"))
                    pending = PendingAfterImport.dropAccepted(pending, PendingAfterImport.invalid(rejected))
                    settings.setPendingItems(pending)
                    val inUse = PendingAfterImport.inUseDeletes(rejected)
                    if (inUse.isNotEmpty()) {
                        pending = PendingAfterImport.dropDeletes(pending, inUse)
                        settings.setPendingItems(pending)
                        keptNotice = (0 until rejected.length())
                            .mapNotNull { rejected.optJSONObject(it) }
                            .firstOrNull { it.optString("code") == "IN_USE" }
                            ?.optString("message").orEmpty()
                    }
                    if (rejected.length() > inUse.size) {
                        val first = (0 until rejected.length())
                            .mapNotNull { rejected.optJSONObject(it) }
                            .firstOrNull { it.optString("code") != "IN_USE" }
                        val msg = first?.optString("message").orEmpty()
                        return msg.ifBlank { "일부 전송이 거절되었습니다" }
                    }
                }
            }
        }
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            val extra = runExtra(token, item) ?: continue
            if (extra.isNotBlank()) return extra
        }
        pending = PendingAfterImport.dropKinds(pending, EXTRA_KINDS)
        settings.setPendingItems(pending)
        val localCount = db().exportBundle().materials.size
        val snap = snapshot(token, month) ?: return "서버에서 최신 데이터를 받지 못했습니다"
        val serverCount = snap.optJSONArray("materials")?.length() ?: 0
        if (SyncPolicy.keepLocalMasters(serverCount, localCount)) {
            if (role == UserRole.MANAGER) {
                val imported = api.importBundle(token, JSONObject(BackupIo.toJson(db().exportBundle())))
                if (imported is ApiResult.Ok) {
                    val again = snapshot(token, month) ?: return "서버에 올렸으나 다시 받지 못했습니다"
                    restoreKeepingDeletes(again, pending, month)
                    settings.setLastSync(System.currentTimeMillis())
                    settings.clearMastersDirty(dirtyStamp)
                    return withPhotos(token, role, synced(keptNotice))
                }
            }
            return "자재는 이 폰에 저장했습니다. 설정에서 공장 암호를 넣고 「공장 전환」을 하면 서버에도 올라갑니다"
        }
        restoreKeepingDeletes(snap, pending, month)
        settings.setLastSync(System.currentTimeMillis())
        settings.clearMastersDirty(dirtyStamp)
        return withPhotos(token, role, synced(keptNotice))
    }

    private fun synced(notice: String): String =
        if (notice.isBlank()) "서버와 맞췄습니다" else "서버와 맞췄습니다. $notice"

    suspend fun enqueueAndSync(month: YearMonthKey, role: UserRole, item: JSONObject?): String? {
        if (item != null) settings.enqueue(item)
        val token = settings.accessToken.first()
        if (token.isBlank()) return null
        return runCatching { syncNow(month, role) }.getOrElse { it.message }
    }

    private suspend fun withPhotos(token: String, message: String): String =
        withPhotos(token, settings.role.first(), message)

    private suspend fun withPhotos(token: String, role: UserRole, message: String): String {
        val extra = syncPhotos(token, role)
        return if (extra.isNullOrBlank()) message else "$message $extra"
    }

    private suspend fun syncPhotos(token: String, role: UserRole): String? {
        val store = photos ?: return null
        val remote = when (val index = api.photoIndex(token)) {
            is ApiResult.Err -> return if (index.code == "HTTP_404") null else "사진은 다음에 맞춰집니다"
            is ApiResult.Ok -> photoStamps(index.data)
        }
        var failed = false
        for (op in PhotoSyncPlan.ops(store.stamps(), remote)) {
            val stamp = op.stamp
            val stepFailed = when (op.action) {
                PhotoSyncAction.UPLOAD -> {
                    if (role != UserRole.MANAGER) false
                    else {
                        val bytes = store.read(stamp.kind, stamp.codeNo)
                        if (bytes == null) false
                        else api.uploadPhoto(token, stamp.kind, stamp.codeNo, stamp.updatedAt, bytes) is ApiResult.Err
                    }
                }
                PhotoSyncAction.DELETE_REMOTE -> {
                    if (role != UserRole.MANAGER) false
                    else {
                        val deleted = api.deletePhoto(token, stamp.kind, stamp.codeNo, stamp.updatedAt)
                        deleted is ApiResult.Err && deleted.code != "HTTP_404" && deleted.code != "NOT_FOUND"
                    }
                }
                PhotoSyncAction.DOWNLOAD -> when (val down = api.downloadPhoto(token, stamp.kind, stamp.codeNo)) {
                    is ApiResult.Ok -> {
                        val updated = down.data.updatedAt.coerceAtLeast(stamp.updatedAt)
                        store.saveJpeg(stamp.kind, stamp.codeNo, down.data.bytes, updated)
                        false
                    }
                    is ApiResult.Err -> down.code != "HTTP_404" && down.code != "NOT_FOUND"
                }
            }
            if (stepFailed) failed = true
        }
        return if (failed) "사진은 다음에 맞춰집니다" else null
    }

    private fun photoStamps(data: JSONObject): List<PhotoStamp> {
        val items = data.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<PhotoStamp>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val kind = item.optString("kind")
            val code = item.optInt("codeNo")
            if ((kind != "material" && kind != "product") || code !in 1..500) continue
            out += PhotoStamp(kind, code, item.optLong("updatedAt"))
        }
        return out
    }

    private suspend fun requireToken(): String? = settings.accessToken.first().ifBlank { null }

    private suspend fun refreshIfNeeded(token: String): String {
        val probe = api.snapshot(token, YearMonthKey.current().value)
        if (probe is ApiResult.Ok || (probe is ApiResult.Err && probe.code != "UNAUTHORIZED")) return token
        val refresh = settings.refreshToken.first()
        if (refresh.isBlank()) return token
        return when (val again = api.refresh(refresh)) {
            is ApiResult.Ok -> {
                applyLogin(again.data)
                again.data.accessToken
            }
            is ApiResult.Err -> token
        }
    }

    private suspend fun snapshot(token: String, month: YearMonthKey): JSONObject? {
        return when (val result = api.snapshot(token, month.value)) {
            is ApiResult.Ok -> result.data.also { applyScreenLabels(it) }
            is ApiResult.Err -> null
        }
    }

    private suspend fun applyScreenLabels(snap: JSONObject) {
        val labels = snap.optJSONObject("workspace")?.optJSONObject("labels") ?: return
        val id = settings.workspaceId.first()
        if (id.isBlank()) return
        settings.setScreenLabels(
            id,
            ScreenLabels(
                labels.optString("title"),
                labels.optString("materials"),
                labels.optString("production"),
                labels.optString("report")
            )
        )
    }

    suspend fun saveScreenLabels(labels: ScreenLabels): String? {
        val id = settings.workspaceId.first()
        if (id.isBlank()) return "공장에 입장하세요"
        val token = refreshIfNeeded(requireToken() ?: return "서버에 연결되어 있지 않습니다. 공장에 다시 입장하세요")
        val clean = labels.cleaned()
        val body = JSONObject()
            .put("title", clean.title)
            .put("materials", clean.materials)
            .put("production", clean.production)
            .put("report", clean.report)
        return when (val result = api.setScreenLabels(token, body)) {
            is ApiResult.Ok -> {
                settings.setScreenLabels(id, clean)
                null
            }
            is ApiResult.Err -> when (result.code) {
                "FORBIDDEN" -> "관리책임자 이름으로 공장에 입장한 폰에서만 저장할 수 있습니다"
                "NETWORK" -> "서버에 연결할 수 없습니다"
                else -> result.message
            }
        }
    }

    private suspend fun restoreKeepingDeletes(data: JSONObject, pending: JSONArray, month: YearMonthKey) {
        val rawMoves = data.optJSONArray("movements")
        val rawMats = data.optJSONArray("materials")
        val rawProds = data.optJSONArray("products")
        val rawFin = data.optJSONArray("finished")
        data.put("movements", PendingAfterImport.stripDeletedMovements(rawMoves, pending))
        data.put("materials", PendingAfterImport.stripDeletedMaterials(rawMats, pending))
        data.put("products", PendingAfterImport.stripDeletedProducts(rawProds, pending))
        data.put("finished", PendingAfterImport.stripDeletedFinished(rawFin, pending))
        PendingAfterImport.stripOrphans(data)
        CloseSync.applyToSnapshot(data, pending, month.value)
        restoreSnapshot(data)
        settings.setPendingItems(
            PendingAfterImport.keepUnfinishedDeletes(pending, rawMoves, rawMats, rawProds, rawFin)
        )
    }

    private suspend fun restoreSnapshot(data: JSONObject) {
        val before = db().exportBundle()
        val incoming = BackupIo.fromJson(data.toString())
        db().restoreBundle(
            incoming.copy(
                openings = MonthCarry.keepOtherMonths(incoming.openings, before.openings) { it.yearMonth },
                productOpenings = MonthCarry.keepOtherMonths(incoming.productOpenings, before.productOpenings) { it.yearMonth }
            )
        )
        val after = db().exportBundle()
        refreshPendingIds(
            extraIdToCode = before.materials.associate { it.id to it.codeNo },
            extraCodeToId = after.materials.associate { it.codeNo to it.id },
            extraProdIdToCode = before.products.associate { it.id to it.codeNo },
            extraProdCodeToId = after.products.associate { it.codeNo to it.id }
        )
    }

    private suspend fun refreshPendingIds(
        extraIdToCode: Map<Long, Int> = emptyMap(),
        extraCodeToId: Map<Int, Long> = emptyMap(),
        extraProdIdToCode: Map<Long, Int> = emptyMap(),
        extraProdCodeToId: Map<Int, Long> = emptyMap()
    ) {
        val bundle = db().exportBundle()
        val idToCode = extraIdToCode + bundle.materials.associate { it.id to it.codeNo }
        val codeToId = extraCodeToId + bundle.materials.associate { it.codeNo to it.id }
        val prodIdToCode = extraProdIdToCode + bundle.products.associate { it.id to it.codeNo }
        val prodCodeToId = extraProdCodeToId + bundle.products.associate { it.codeNo to it.id }
        val pending = settings.pendingItems()
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            next.put(
                when (item.optString("kind")) {
                    "movement", "delete_movement" -> PendingSyncIds.enrichMovement(item, idToCode, codeToId)
                    "production" -> PendingSyncIds.enrichProduction(item, prodIdToCode, prodCodeToId)
                    else -> item
                }
            )
        }
        settings.setPendingItems(next)
    }

    private suspend fun buildPush(pending: JSONArray, includeMasters: Boolean): JSONObject {
        val body = JSONObject()
        val movements = JSONArray()
        val deletedMats = JSONArray()
        val deletedProds = JSONArray()
        val deletedFin = JSONArray()
        val production = JSONArray()
        val finishedOutput = JSONArray()
        val bundle = db().exportBundle()
        val idToCode = bundle.materials.associate { it.id to it.codeNo }
        val codeToId = bundle.materials.associate { it.codeNo to it.id }
        val prodIdToCode = bundle.products.associate { it.id to it.codeNo }
        val prodCodeToId = bundle.products.associate { it.codeNo to it.id }
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            when (item.optString("kind")) {
                "movement" -> movements.put(PendingSyncIds.enrichMovement(item, idToCode, codeToId))
                "delete_material" -> deletedMats.put(item)
                "delete_product" -> deletedProds.put(item)
                "delete_finished" -> deletedFin.put(item)
                "production" -> production.put(PendingSyncIds.enrichProduction(item, prodIdToCode, prodCodeToId))
                "finished_production" -> finishedOutput.put(item)
            }
        }
        if (finishedOutput.length() > 0) body.put("finishedProduction", finishedOutput)
        if (deletedMats.length() > 0) body.put("deletedMaterials", deletedMats)
        if (deletedProds.length() > 0) body.put("deletedProducts", deletedProds)
        if (deletedFin.length() > 0) body.put("deletedFinished", deletedFin)
        if (movements.length() > 0) body.put("movements", movements)
        if (production.length() > 0) body.put("production", production)
        if (includeMasters) {
            val bundle = JSONObject(BackupIo.toJson(db().exportBundle()))
            body.put("materials", bundle.optJSONArray("materials") ?: JSONArray())
            body.put("products", bundle.optJSONArray("products") ?: JSONArray())
            body.put("bom", bundle.optJSONArray("bom") ?: JSONArray())
            body.put("finished", bundle.optJSONArray("finished") ?: JSONArray())
            body.put("composition", bundle.optJSONArray("composition") ?: JSONArray())
            body.put("openings", bundle.optJSONArray("openings") ?: JSONArray())
            body.put("productPlans", bundle.optJSONArray("productPlans") ?: JSONArray())
            body.put("monthlyPlans", bundle.optJSONArray("monthlyPlans") ?: JSONArray())
            body.put("productOpenings", bundle.optJSONArray("productOpenings") ?: JSONArray())
        }
        return body
    }

    private suspend fun runExtra(token: String, item: JSONObject): String? {
        return when (item.optString("kind")) {
            "delete_material" -> when (val r = api.deleteMaterial(token, item)) {
                is ApiResult.Err -> r.message
                is ApiResult.Ok -> null
            }
            "delete_product" -> when (val r = api.deleteProduct(token, item)) {
                is ApiResult.Err -> r.message
                is ApiResult.Ok -> null
            }
            "delete_finished" -> when (val r = api.deleteFinished(token, item)) {
                is ApiResult.Err -> r.message
                is ApiResult.Ok -> null
            }
            "stocktake" -> when (val r = api.stocktake(token, item)) {
                is ApiResult.Err -> r.message
                is ApiResult.Ok -> null
            }
            "close" -> when (val r = api.closeMonth(token, item.optString("yearMonth"))) {
                is ApiResult.Err -> if (r.code == "CONFLICT") null else r.message
                is ApiResult.Ok -> null
            }
            "reopen" -> when (val r = api.reopenMonth(token, item.optString("yearMonth"))) {
                is ApiResult.Err -> r.message
                is ApiResult.Ok -> null
            }
            else -> null
        }
    }

    companion object {
        /** 동기화는 DB를 통째로 다시 쓰므로 한 번에 하나만 돈다. */
        private val syncLock = Mutex()

        private val EXTRA_KINDS = setOf("close", "reopen", "stocktake")

        private val DELETE_KINDS = setOf(
            "delete_movement",
            "delete_material",
            "delete_product",
            "delete_finished"
        )

        fun deleteMaterial(id: Long, codeNo: Int) = JSONObject()
            .put("kind", "delete_material")
            .put("id", id)
            .put("codeNo", codeNo)

        fun deleteProduct(id: Long, codeNo: Int) = JSONObject()
            .put("kind", "delete_product")
            .put("id", id)
            .put("codeNo", codeNo)

        fun deleteFinished(id: Long, codeNo: Int) = JSONObject()
            .put("kind", "delete_finished")
            .put("id", id)
            .put("codeNo", codeNo)

        fun deleteMovement(
            id: Long,
            materialId: Long,
            type: String,
            qty: Double,
            unitPrice: Double,
            occurredOn: String,
            codeNo: Int = 0
        ) = JSONObject()
            .put("kind", "delete_movement")
            .put("id", id)
            .put("materialId", materialId)
            .put("codeNo", codeNo)
            .put("type", type)
            .put("qty", qty)
            .put("unitPrice", unitPrice)
            .put("occurredOn", occurredOn)

        fun movement(
            materialId: Long,
            type: String,
            qty: Double,
            unitPrice: Double,
            occurredOn: String,
            note: String,
            codeNo: Int = 0
        ) = JSONObject()
            .put("kind", "movement")
            .put("clientUid", UUID.randomUUID().toString())
            .put("materialId", materialId)
            .put("codeNo", codeNo)
            .put("type", type)
            .put("qty", qty)
            .put("unitPrice", unitPrice)
            .put("occurredOn", occurredOn)
            .put("note", note)

        fun production(productId: Long, workDate: String, qty: Int, codeNo: Int = 0) = JSONObject()
            .put("kind", "production")
            .put("clientUid", UUID.randomUUID().toString())
            .put("productId", productId)
            .put("codeNo", codeNo)
            .put("workDate", workDate)
            .put("qty", qty)

        fun finishedProduction(finishedId: Long, workDate: String, qty: Int, codeNo: Int) = JSONObject()
            .put("kind", "finished_production")
            .put("clientUid", UUID.randomUUID().toString())
            .put("finishedGoodId", finishedId)
            .put("codeNo", codeNo)
            .put("workDate", workDate)
            .put("qty", qty)

        fun stocktake(
            yearMonth: String,
            occurredOn: String,
            counts: Map<Long, Double>,
            codes: Map<Long, Int> = emptyMap(),
            notes: Map<Long, String> = emptyMap()
        ): JSONObject {
            val arr = JSONArray()
            counts.forEach { (id, qty) ->
                val code = codes[id] ?: 0
                arr.put(
                    JSONObject()
                        .put("materialId", id)
                        .put("codeNo", code)
                        .put("physicalQty", qty)
                        .put("note", notes[id].orEmpty())
                        .put("clientUid", "stocktake-$yearMonth-${if (code > 0) code else id}-$occurredOn")
                )
            }
            return JSONObject()
                .put("kind", "stocktake")
                .put("yearMonth", yearMonth)
                .put("occurredOn", occurredOn)
                .put("counts", arr)
        }

        fun close(yearMonth: String) = JSONObject().put("kind", "close").put("yearMonth", yearMonth)
        fun reopen(yearMonth: String) = JSONObject().put("kind", "reopen").put("yearMonth", yearMonth)
    }
}
