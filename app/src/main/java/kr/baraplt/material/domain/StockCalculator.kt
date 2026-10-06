package kr.baraplt.material.domain

object StockCalculator {

    fun currentStock(
        opening: Double,
        inbound: Double,
        outbound: Double,
        scrap: Double,
        adjust: Double,
        usage: Double
    ): Double = opening + inbound - outbound - scrap + adjust - usage

    fun productMaterialCost(usQty: List<Double>, unitPrices: List<Double>): Double {
        require(usQty.size == unitPrices.size)
        return usQty.indices.sumOf { usQty[it] * unitPrices[it] }
    }

    fun materialRatio(materialCost: Double, sellPrice: Double): Double =
        if (sellPrice <= 0.0) 0.0 else materialCost / sellPrice

    fun explodeUsage(
        productions: Map<Long, Int>,
        boms: Map<Long, List<Pair<Long, Double>>>
    ): Map<Long, Double> {
        val usage = mutableMapOf<Long, Double>()
        productions.forEach { (productId, qty) ->
            boms[productId].orEmpty().forEach { (materialId, us) ->
                usage[materialId] = (usage[materialId] ?: 0.0) + qty * us
            }
        }
        return usage
    }

    fun productsUsedByFinished(
        finishedQty: Map<Long, Int>,
        composition: Map<Long, List<Pair<Long, Int>>>
    ): Map<Long, Int> {
        val used = mutableMapOf<Long, Int>()
        finishedQty.forEach { (fgId, qty) ->
            if (qty <= 0) return@forEach
            composition[fgId].orEmpty().forEach { (productId, per) ->
                used[productId] = (used[productId] ?: 0) + qty * per.coerceAtLeast(1)
            }
        }
        return used
    }

    fun productShortfall(current: Int, safetyStock: Int): Int =
        if (safetyStock <= 0) 0 else (safetyStock - current).coerceAtLeast(0)

    fun requiredFromPlans(
        finishedPlans: Map<Long, Int>,
        finishedBom: Map<Long, List<Pair<Long, Int>>>,
        productBoms: Map<Long, List<Pair<Long, Double>>>,
        productShortfalls: Map<Long, Int> = emptyMap(),
        productPlans: Map<Long, Int> = emptyMap()
    ): Map<Long, Double> {
        val productNeed = productsUsedByFinished(finishedPlans, finishedBom).toMutableMap()
        productPlans.forEach { (productId, qty) ->
            if (qty > 0) productNeed[productId] = maxOf(productNeed[productId] ?: 0, qty)
        }
        productShortfalls.forEach { (productId, qty) ->
            if (qty > 0) productNeed[productId] = (productNeed[productId] ?: 0) + qty
        }
        return explodeUsage(productNeed, productBoms)
    }
}
