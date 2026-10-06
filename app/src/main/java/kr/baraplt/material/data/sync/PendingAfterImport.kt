package kr.baraplt.material.data.sync

import org.json.JSONArray
import org.json.JSONObject

object PendingAfterImport {
    data class MovementKey(
        val codeNo: Int,
        val materialId: Long,
        val type: String,
        val qty: Double,
        val occurredOn: String,
        val unitPrice: Double
    )

    data class DeleteKey(val kind: String, val codeNo: Int)

    fun inUseDeletes(rejected: JSONArray?): List<DeleteKey> {
        if (rejected == null) return emptyList()
        return buildList {
            for (i in 0 until rejected.length()) {
                val r = rejected.optJSONObject(i) ?: continue
                if (r.optString("code") != "IN_USE") continue
                val kind = r.optString("kind")
                val code = r.optInt("codeNo")
                if (kind.isBlank() || code <= 0) continue
                add(DeleteKey("delete_$kind", code))
            }
        }
    }

    fun dropDeletes(pending: JSONArray, keys: List<DeleteKey>): JSONArray {
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            val key = DeleteKey(item.optString("kind"), item.optInt("codeNo"))
            if (key !in keys) next.put(item)
        }
        return next
    }

    fun shouldKeepAfterImport(kind: String): Boolean =
        kind != "movement" && kind != "production" && kind != "finished_production"

    fun alreadyOnServer(pending: MovementKey, server: List<MovementKey>): Boolean =
        server.any { sameMovement(pending, it) }

    fun remainingAfterDelete(server: List<MovementKey>, deletes: List<MovementKey>): List<MovementKey> =
        server.filter { item -> deletes.none { sameMovement(it, item) } }

    data class ServerMovement(val clientUid: String, val key: MovementKey)

    /**
     * 대기 입출고 중 서버에 이미 있는 것의 위치.
     * 같은 날 같은 수량 입고가 두 번 있을 수 있어 내용만으로는 같은 건으로 보지 않는다.
     * 전체 올리기(import-) 행은 고유번호가 달라 내용으로 맞추되, 한 행이 대기 한 건만 덮는다.
     */
    fun pendingOnServer(pending: List<Pair<String, MovementKey>>, server: List<ServerMovement>): Set<Int> {
        val uids = server.mapTo(HashSet()) { it.clientUid }
        val imported = server.filter { it.clientUid.startsWith("import-") }.toMutableList()
        val out = HashSet<Int>()
        pending.forEachIndexed { i, (uid, key) ->
            if (uid.isNotBlank() && uid in uids) {
                out += i
                return@forEachIndexed
            }
            val hit = imported.indexOfFirst { sameMovement(key, it.key) }
            if (hit >= 0) {
                imported.removeAt(hit)
                out += i
            }
        }
        return out
    }

    /** 삭제 대기 한 건이 서버 입출고 한 건만 가린다. id가 맞는 행을 먼저 고른다. */
    fun hiddenByDeletes(server: List<Pair<Long, MovementKey>>, deletes: List<Pair<Long, MovementKey>>): Set<Int> {
        val taken = HashSet<Int>()
        for ((id, key) in deletes) {
            var hit = if (id > 0) server.indices.firstOrNull { it !in taken && server[it].first == id && sameMovement(key, server[it].second) } else null
            if (hit == null) hit = server.indices.firstOrNull { it !in taken && sameMovement(key, server[it].second) }
            if (hit != null) taken += hit
        }
        return taken
    }

    /** 아직 서버에 안 보낸 입출고를 지울 때, 서버 삭제 대신 취소할 대기 항목 위치. 없으면 -1. */
    fun unsentMovementIndex(pendingMovements: List<MovementKey>, target: MovementKey): Int =
        pendingMovements.indexOfFirst { sameMovement(it, target) }

    fun cancelUnsentMovement(pending: JSONArray, target: MovementKey): JSONArray? {
        val positions = (0 until pending.length()).filter { pending.optJSONObject(it)?.optString("kind") == "movement" }
        val hit = unsentMovementIndex(positions.map { pending.getJSONObject(it).toMovementKey() }, target)
        if (hit < 0) return null
        val next = JSONArray()
        for (i in 0 until pending.length()) if (i != positions[hit]) next.put(pending.get(i))
        return next
    }

    fun stripDeletedMovements(serverMovements: JSONArray?, pending: JSONArray): JSONArray {
        val next = JSONArray()
        if (serverMovements == null) return next
        val server = (0 until serverMovements.length()).mapNotNull { serverMovements.optJSONObject(it) }
        val deletes = (0 until pending.length())
            .mapNotNull { pending.optJSONObject(it) }
            .filter { it.optString("kind") == "delete_movement" }
            .map { it.optLong("id") to it.toMovementKey() }
        val hidden = hiddenByDeletes(server.map { it.optLong("id") to it.toMovementKey() }, deletes)
        server.forEachIndexed { i, item -> if (i !in hidden) next.put(item) }
        return next
    }

    fun keepUnfinishedDeletes(
        pending: JSONArray,
        serverMovements: JSONArray?,
        serverMaterials: JSONArray? = null,
        serverProducts: JSONArray? = null,
        serverFinished: JSONArray? = null
    ): JSONArray {
        val server = serverMovements.toMovementKeys()
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            when (item.optString("kind")) {
                "delete_movement" -> if (alreadyOnServer(item.toMovementKey(), server)) next.put(item)
                "delete_material" -> if (sameIdOrCodeOnServer(item, serverMaterials)) next.put(item)
                "delete_product" -> if (sameIdOrCodeOnServer(item, serverProducts)) next.put(item)
                "delete_finished" -> if (sameIdOrCodeOnServer(item, serverFinished)) next.put(item)
            }
        }
        return next
    }

    fun stripDeletedMaterials(serverMaterials: JSONArray?, pending: JSONArray): JSONArray =
        stripDeletedMasters(serverMaterials, pending, "delete_material")

    fun stripDeletedProducts(serverProducts: JSONArray?, pending: JSONArray): JSONArray =
        stripDeletedMasters(serverProducts, pending, "delete_product")

    fun stripDeletedFinished(serverFinished: JSONArray?, pending: JSONArray): JSONArray =
        stripDeletedMasters(serverFinished, pending, "delete_finished")

    fun stripOrphans(data: JSONObject): JSONObject {
        val matIds = idsOf(data.optJSONArray("materials"))
        val prodIds = idsOf(data.optJSONArray("products"))
        val finIds = idsOf(data.optJSONArray("finished"))
        data.put("bom", filterBy(filterBy(data.optJSONArray("bom"), "productId", prodIds), "materialId", matIds))
        data.put("productPlans", filterBy(data.optJSONArray("productPlans"), "productId", prodIds))
        data.put("production", filterBy(data.optJSONArray("production"), "productId", prodIds))
        data.put("composition", filterBy(data.optJSONArray("composition"), "finishedGoodId", finIds))
        data.put("monthlyPlans", filterBy(data.optJSONArray("monthlyPlans"), "finishedGoodId", finIds))
        data.put("openings", filterBy(data.optJSONArray("openings"), "materialId", matIds))
        data.put("productOpenings", filterBy(data.optJSONArray("productOpenings"), "productId", prodIds))
        data.put("finishedProduction", filterBy(data.optJSONArray("finishedProduction"), "finishedGoodId", finIds))
        return data
    }

    private fun stripDeletedMasters(server: JSONArray?, pending: JSONArray, kind: String): JSONArray {
        val next = JSONArray()
        if (server == null) return next
        for (i in 0 until server.length()) {
            val item = server.optJSONObject(i) ?: continue
            var drop = false
            for (p in 0 until pending.length()) {
                val del = pending.optJSONObject(p) ?: continue
                if (del.optString("kind") == kind && sameIdOrCode(del, item)) {
                    drop = true
                    break
                }
            }
            if (!drop) next.put(item)
        }
        return next
    }

    private fun sameIdOrCodeOnServer(pending: JSONObject, server: JSONArray?): Boolean {
        if (server == null) return false
        for (i in 0 until server.length()) {
            val item = server.optJSONObject(i) ?: continue
            if (sameIdOrCode(pending, item)) return true
        }
        return false
    }

    private fun sameIdOrCode(a: JSONObject, b: JSONObject): Boolean {
        val code = a.optInt("codeNo")
        if (code > 0 && code == b.optInt("codeNo")) return true
        val id = a.optLong("id")
        return id > 0 && id == b.optLong("id")
    }

    private fun idsOf(arr: JSONArray?): Set<Long> {
        if (arr == null) return emptySet()
        return buildSet {
            for (i in 0 until arr.length()) {
                val id = arr.optJSONObject(i)?.optLong("id") ?: 0L
                if (id > 0) add(id)
            }
        }
    }

    private fun filterBy(arr: JSONArray?, key: String, keep: Set<Long>): JSONArray {
        val next = JSONArray()
        if (arr == null) return next
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optLong(key) in keep) next.put(item)
        }
        return next
    }

    fun sameMovement(a: MovementKey, b: MovementKey): Boolean {
        val sameIdent = when {
            a.codeNo > 0 && b.codeNo > 0 -> a.codeNo == b.codeNo
            else -> a.materialId > 0 && a.materialId == b.materialId
        }
        return sameIdent &&
            a.type == b.type &&
            a.qty == b.qty &&
            a.occurredOn == b.occurredOn &&
            a.unitPrice == b.unitPrice
    }

    fun dropKinds(pending: JSONArray, kinds: Set<String>): JSONArray {
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            if (item.optString("kind") !in kinds) next.put(item)
        }
        return next
    }

    fun dropUploadedKinds(pending: JSONArray): JSONArray {
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            if (shouldKeepAfterImport(item.optString("kind"))) next.put(item)
        }
        return next
    }

    fun dropAccepted(pending: JSONArray, accepted: JSONArray?): JSONArray {
        val uids = buildSet {
            if (accepted != null) for (i in 0 until accepted.length()) {
                accepted.optJSONObject(i)?.optString("clientUid")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        if (uids.isEmpty()) return pending
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            if (item.optString("kind") in SENT_KINDS && item.optString("clientUid") in uids) continue
            next.put(item)
        }
        return next
    }

    private val SENT_KINDS = setOf("movement", "production", "finished_production")

    /** 서버가 내용 때문에 거절한 항목. 다시 보내도 같으므로 대기열에서 뺀다. */
    fun invalid(rejected: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until rejected.length()) {
            val r = rejected.optJSONObject(i) ?: continue
            if (r.optString("code") == "VALIDATION") out.put(r)
        }
        return out
    }

    fun dropAlreadyOnServer(pending: JSONArray, serverMovements: JSONArray?): JSONArray {
        val server = buildList {
            if (serverMovements != null) for (i in 0 until serverMovements.length()) {
                val item = serverMovements.optJSONObject(i) ?: continue
                add(ServerMovement(item.optString("clientUid"), item.toMovementKey()))
            }
        }
        val items = (0 until pending.length()).mapNotNull { pending.optJSONObject(it) }
        val movementPositions = items.indices.filter { items[it].optString("kind") == "movement" }
        val onServer = pendingOnServer(
            movementPositions.map { items[it].optString("clientUid") to items[it].toMovementKey() },
            server
        ).map { movementPositions[it] }.toSet()
        val next = JSONArray()
        items.forEachIndexed { i, item -> if (i !in onServer) next.put(item) }
        return next
    }

    private fun JSONObject.toMovementKey() = MovementKey(
        codeNo = optInt("codeNo"),
        materialId = optLong("materialId"),
        type = optString("type"),
        qty = optDouble("qty"),
        occurredOn = optString("occurredOn"),
        unitPrice = optDouble("unitPrice")
    )

    private fun JSONArray?.toMovementKeys(): List<MovementKey> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                add(item.toMovementKey())
            }
        }
    }
}
