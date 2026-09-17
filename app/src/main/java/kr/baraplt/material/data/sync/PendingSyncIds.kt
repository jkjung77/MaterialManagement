package kr.baraplt.material.data.sync

import org.json.JSONObject

object PendingSyncIds {
    fun resolve(
        oldId: Long,
        existingCode: Int,
        idToCode: Map<Long, Int>,
        codeToId: Map<Int, Long>
    ): Pair<Int, Long>? {
        val code = listOf(
            existingCode.takeIf { it > 0 },
            idToCode[oldId],
            codeToId.keys.singleOrNull()
        ).firstOrNull { it != null && it > 0 } ?: return null
        val newId = codeToId[code] ?: oldId
        return code to newId
    }

    fun enrichMovement(
        item: JSONObject,
        idToCode: Map<Long, Int>,
        codeToId: Map<Int, Long>
    ): JSONObject {
        val out = JSONObject(item.toString())
        val resolved = resolve(out.optLong("materialId"), out.optInt("codeNo"), idToCode, codeToId)
            ?: return out
        out.put("codeNo", resolved.first)
        out.put("materialId", resolved.second)
        return out
    }

    fun enrichProduction(
        item: JSONObject,
        idToCode: Map<Long, Int>,
        codeToId: Map<Int, Long>
    ): JSONObject {
        val out = JSONObject(item.toString())
        val resolved = resolve(out.optLong("productId"), out.optInt("codeNo"), idToCode, codeToId)
            ?: return out
        out.put("codeNo", resolved.first)
        out.put("productId", resolved.second)
        return out
    }
}
