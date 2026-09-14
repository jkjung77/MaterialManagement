package kr.baraplt.material.data

import kr.baraplt.material.data.entity.DailyProductionEntity
import kr.baraplt.material.data.entity.FinishedCompositionEntity
import kr.baraplt.material.data.entity.FinishedGoodEntity
import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.data.entity.MonthCloseEntity
import kr.baraplt.material.data.entity.MonthlyPlanEntity
import kr.baraplt.material.data.entity.OpeningStockEntity
import kr.baraplt.material.data.entity.ProductBomEntity
import kr.baraplt.material.data.entity.ProductEntity
import kr.baraplt.material.data.entity.ProductPlanEntity
import kr.baraplt.material.data.entity.StockMovementEntity
import kr.baraplt.material.data.repo.BackupBundle
import org.json.JSONArray
import org.json.JSONObject

object BackupIo {
    fun toJson(bundle: BackupBundle): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("materials", JSONArray().also { arr ->
            bundle.materials.forEach { m ->
                arr.put(
                    JSONObject()
                        .put("id", m.id).put("codeNo", m.codeNo).put("name", m.name)
                        .put("unit", m.unit).put("packUnit", m.packUnit)
                        .put("unitPrice", m.unitPrice).put("safetyStock", m.safetyStock)
                        .put("leadTimeDays", m.leadTimeDays).put("note", m.note)
                        .put("isActive", m.isActive).put("updatedAt", m.updatedAt)
                )
            }
        })
        root.put("openings", JSONArray().also { arr ->
            bundle.openings.forEach {
                arr.put(JSONObject().put("id", it.id).put("materialId", it.materialId).put("yearMonth", it.yearMonth).put("qty", it.qty))
            }
        })
        root.put("movements", JSONArray().also { arr ->
            bundle.movements.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("materialId", it.materialId).put("type", it.type)
                        .put("qty", it.qty).put("unitPrice", it.unitPrice).put("occurredOn", it.occurredOn)
                        .put("note", it.note).put("createdAt", it.createdAt).put("createdBy", it.createdBy)
                )
            }
        })
        root.put("products", JSONArray().also { arr ->
            bundle.products.forEach {
                arr.put(JSONObject().put("id", it.id).put("codeNo", it.codeNo).put("name", it.name).put("sellPrice", it.sellPrice).put("isActive", it.isActive).put("updatedAt", it.updatedAt))
            }
        })
        root.put("bom", JSONArray().also { arr ->
            bundle.bom.forEach {
                arr.put(JSONObject().put("id", it.id).put("productId", it.productId).put("materialId", it.materialId).put("usQty", it.usQty).put("sortOrder", it.sortOrder))
            }
        })
        root.put("production", JSONArray().also { arr ->
            bundle.production.forEach {
                arr.put(JSONObject().put("id", it.id).put("productId", it.productId).put("workDate", it.workDate).put("qty", it.qty))
            }
        })
        root.put("productPlans", JSONArray().also { arr ->
            bundle.productPlans.forEach {
                arr.put(JSONObject().put("id", it.id).put("productId", it.productId).put("yearMonth", it.yearMonth).put("qty", it.qty))
            }
        })
        root.put("finished", JSONArray().also { arr ->
            bundle.finished.forEach {
                arr.put(JSONObject().put("id", it.id).put("codeNo", it.codeNo).put("name", it.name).put("sellPrice", it.sellPrice).put("isActive", it.isActive).put("updatedAt", it.updatedAt))
            }
        })
        root.put("composition", JSONArray().also { arr ->
            bundle.composition.forEach {
                arr.put(JSONObject().put("id", it.id).put("finishedGoodId", it.finishedGoodId).put("productId", it.productId).put("sortOrder", it.sortOrder))
            }
        })
        root.put("monthlyPlans", JSONArray().also { arr ->
            bundle.monthlyPlans.forEach {
                arr.put(JSONObject().put("id", it.id).put("finishedGoodId", it.finishedGoodId).put("yearMonth", it.yearMonth).put("qty", it.qty))
            }
        })
        root.put("closes", JSONArray().also { arr ->
            bundle.closes.forEach {
                arr.put(JSONObject().put("yearMonth", it.yearMonth).put("closedAt", it.closedAt).put("closedBy", it.closedBy))
            }
        })
        return root.toString(2)
    }

    fun fromJson(text: String): BackupBundle {
        val root = JSONObject(text)
        return BackupBundle(
            materials = root.optJSONArray("materials").orEmpty().map { o ->
                MaterialEntity(
                    id = o.optLong("id"), codeNo = o.optInt("codeNo"), name = o.optString("name"),
                    unit = o.optString("unit"), packUnit = o.optString("packUnit"),
                    unitPrice = o.optDouble("unitPrice"), safetyStock = o.optDouble("safetyStock"),
                    leadTimeDays = o.optInt("leadTimeDays"), note = o.optString("note"),
                    isActive = o.optBoolean("isActive", true), updatedAt = o.optLong("updatedAt")
                )
            },
            openings = root.optJSONArray("openings").orEmpty().map {
                OpeningStockEntity(it.optLong("id"), it.optLong("materialId"), it.optString("yearMonth"), it.optDouble("qty"))
            },
            movements = root.optJSONArray("movements").orEmpty().map {
                StockMovementEntity(
                    it.optLong("id"), it.optLong("materialId"), it.optString("type"),
                    it.optDouble("qty"), it.optDouble("unitPrice"), it.optString("occurredOn"),
                    it.optString("note"), it.optLong("createdAt"), it.optString("createdBy")
                )
            },
            products = root.optJSONArray("products").orEmpty().map {
                ProductEntity(it.optLong("id"), it.optInt("codeNo"), it.optString("name"), it.optDouble("sellPrice"), it.optBoolean("isActive", true), it.optLong("updatedAt"))
            },
            bom = root.optJSONArray("bom").orEmpty().map {
                ProductBomEntity(it.optLong("id"), it.optLong("productId"), it.optLong("materialId"), it.optDouble("usQty"), it.optInt("sortOrder"))
            },
            production = root.optJSONArray("production").orEmpty().map {
                DailyProductionEntity(it.optLong("id"), it.optLong("productId"), it.optString("workDate"), it.optInt("qty"))
            },
            productPlans = root.optJSONArray("productPlans").orEmpty().map {
                ProductPlanEntity(it.optLong("id"), it.optLong("productId"), it.optString("yearMonth"), it.optInt("qty"))
            },
            finished = root.optJSONArray("finished").orEmpty().map {
                FinishedGoodEntity(it.optLong("id"), it.optInt("codeNo"), it.optString("name"), it.optDouble("sellPrice"), it.optBoolean("isActive", true), it.optLong("updatedAt"))
            },
            composition = root.optJSONArray("composition").orEmpty().map {
                FinishedCompositionEntity(it.optLong("id"), it.optLong("finishedGoodId"), it.optLong("productId"), it.optInt("sortOrder"))
            },
            monthlyPlans = root.optJSONArray("monthlyPlans").orEmpty().map {
                MonthlyPlanEntity(it.optLong("id"), it.optLong("finishedGoodId"), it.optString("yearMonth"), it.optInt("qty"))
            },
            closes = root.optJSONArray("closes").orEmpty().map {
                MonthCloseEntity(it.optString("yearMonth"), it.optLong("closedAt"), it.optString("closedBy"))
            }
        )
    }
}

private fun JSONArray?.orEmpty(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) add(getJSONObject(i))
    }
}
