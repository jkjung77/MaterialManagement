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

    fun requiredFromPlans(
        finishedPlans: Map<Long, Int>,
        finishedBom: Map<Long, List<Long>>,
        productBoms: Map<Long, List<Pair<Long, Double>>>
    ): Map<Long, Double> {
        val productNeed = mutableMapOf<Long, Int>()
        finishedPlans.forEach { (fgId, plan) ->
            finishedBom[fgId].orEmpty().forEach { productId ->
                productNeed[productId] = (productNeed[productId] ?: 0) + plan
            }
        }
        return explodeUsage(productNeed, productBoms)
    }
}
