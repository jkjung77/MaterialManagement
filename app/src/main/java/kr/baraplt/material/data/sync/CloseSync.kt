package kr.baraplt.material.data.sync

import org.json.JSONArray
import org.json.JSONObject

object CloseSync {
    fun closedAfterPending(
        serverClosed: Boolean,
        pending: List<Pair<String, String>>,
        month: String
    ): Boolean {
        var closed = serverClosed
        pending.forEach { (kind, yearMonth) ->
            if (yearMonth != month) return@forEach
            when (kind) {
                "close" -> closed = true
                "reopen" -> closed = false
            }
        }
        return closed
    }

    fun applyToSnapshot(snap: JSONObject, pending: JSONArray, month: String): JSONObject {
        val kinds = buildList {
            for (i in 0 until pending.length()) {
                val item = pending.optJSONObject(i) ?: continue
                add(item.optString("kind") to item.optString("yearMonth"))
            }
        }
        val closed = closedAfterPending(snap.optBoolean("closed"), kinds, month)
        snap.put("closed", closed)
        val closes = snap.optJSONArray("closes") ?: JSONArray()
        val next = JSONArray()
        for (i in 0 until closes.length()) {
            val item = closes.optJSONObject(i) ?: continue
            if (item.optString("yearMonth") != month) next.put(item)
        }
        if (closed) {
            next.put(JSONObject().put("yearMonth", month).put("closedAt", 1L).put("closedBy", "동기화"))
        }
        snap.put("closes", next)
        return snap
    }
}
