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

    fun shouldKeepAfterImport(kind: String): Boolean =
        kind != "movement" && kind != "production"

    fun alreadyOnServer(pending: MovementKey, server: List<MovementKey>): Boolean =
        server.any { sameMovement(pending, it) }

    fun remainingAfterDelete(server: List<MovementKey>, deletes: List<MovementKey>): List<MovementKey> =
        server.filter { item -> deletes.none { sameMovement(it, item) } }

    fun stripDeletedMovements(serverMovements: JSONArray?, pending: JSONArray): JSONArray {
        val deletes = pendingDeleteKeys(pending)
        val next = JSONArray()
        if (serverMovements == null) return next
        for (i in 0 until serverMovements.length()) {
            val item = serverMovements.optJSONObject(i) ?: continue
            if (deletes.none { sameMovement(it, item.toMovementKey()) }) next.put(item)
        }
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

    private fun pendingDeleteKeys(pending: JSONArray): List<MovementKey> {
        return buildList {
            for (i in 0 until pending.length()) {
                val item = pending.optJSONObject(i) ?: continue
                if (item.optString("kind") == "delete_movement") add(item.toMovementKey())
            }
        }
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

    fun dropUploadedKinds(pending: JSONArray): JSONArray {
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            if (shouldKeepAfterImport(item.optString("kind"))) next.put(item)
        }
        return next
    }

    fun dropAlreadyOnServer(pending: JSONArray, serverMovements: JSONArray?): JSONArray {
        val server = serverMovements.toMovementKeys()
        val next = JSONArray()
        for (i in 0 until pending.length()) {
            val item = pending.optJSONObject(i) ?: continue
            if (item.optString("kind") == "movement" && alreadyOnServer(item.toMovementKey(), server)) {
                continue
            }
            next.put(item)
        }
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
