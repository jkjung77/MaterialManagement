package kr.baraplt.material.domain

object FinishedPlanRollup {
    data class Ref(val qty: Int, val sources: String)

    fun byProduct(finished: List<FinishedSnapshot>): Map<Long, Ref> {
        val qty = mutableMapOf<Long, Int>()
        val codes = mutableMapOf<Long, MutableList<Int>>()
        finished.forEach { item ->
            if (item.monthPlan <= 0) return@forEach
            item.productIds.withIndex().distinctBy { it.value }.forEach { (index, productId) ->
                qty[productId] = (qty[productId] ?: 0) + item.monthPlan * item.qtyAt(index)
                codes.getOrPut(productId) { mutableListOf() }.add(item.codeNo)
            }
        }
        return qty.mapValues { (productId, sum) ->
            val sources = codes[productId].orEmpty().distinct().sorted().joinToString("+") { "NO.$it" }
            Ref(sum, sources)
        }
    }
}
