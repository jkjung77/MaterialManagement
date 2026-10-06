package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StockCalculatorTest {

    @Test
    fun frtMaterialCostMatchesExcel() {
        val us = listOf(0.6, 2.0, 0.55, 1.0, 7.0)
        val prices = listOf(30000.0, 50.0, 2300.0, 15.0, 5.0)
        assertEquals(19415.0, StockCalculator.productMaterialCost(us, prices), 0.01)
    }

    @Test
    fun ctrMaterialCostMatchesExcel() {
        val us = listOf(0.8, 0.98, 2.0, 3.0)
        val prices = listOf(30000.0, 2300.0, 50.0, 5.0)
        assertEquals(26369.0, StockCalculator.productMaterialCost(us, prices), 0.01)
    }

    @Test
    fun fabricCurrentStockMatchesExcel() {
        val usage = StockCalculator.explodeUsage(
            productions = mapOf(1L to 400, 2L to 400, 3L to 350),
            boms = mapOf(
                1L to listOf(1L to 0.6),
                2L to listOf(1L to 0.8),
                3L to listOf(1L to 0.72)
            )
        )
        assertEquals(812.0, usage.getValue(1L), 0.01)
        val current = StockCalculator.currentStock(
            opening = 900.0,
            inbound = 0.0,
            outbound = 0.0,
            scrap = 0.0,
            adjust = 0.0,
            usage = 812.0
        )
        assertEquals(88.0, current, 0.01)
    }

    @Test
    fun resinCurrentStockMatchesExcel() {
        val usage = StockCalculator.explodeUsage(
            productions = mapOf(1L to 400, 2L to 400, 3L to 350, 4L to 275, 5L to 250),
            boms = mapOf(
                1L to listOf(2L to 0.55),
                2L to listOf(2L to 0.98),
                3L to listOf(2L to 0.65),
                4L to listOf(2L to 0.3),
                5L to listOf(2L to 0.46)
            )
        )
        assertEquals(1037.0, usage.getValue(2L), 0.01)
        val current = StockCalculator.currentStock(800.0, 1000.0, 0.0, 2.0, 0.0, 1037.0)
        assertEquals(761.0, current, 0.01)
    }

    @Test
    fun finishedOutputConsumesProductsByComponentQty() {
        val used = StockCalculator.productsUsedByFinished(
            finishedQty = mapOf(100L to 10, 200L to 5, 300L to 0),
            composition = mapOf(
                100L to listOf(1L to 2, 2L to 1),
                200L to listOf(1L to 1),
                300L to listOf(3L to 1)
            )
        )
        assertEquals(mapOf(1L to 25, 2L to 10), used)
    }

    @Test
    fun productCurrentStockSubtractsFinishedUse() {
        val p = ProductSnapshot(
            id = 1, codeNo = 1, name = "A", sellPrice = 0.0, bom = emptyList(),
            monthPlan = 0, produced = 40, safetyStock = 30, opening = 20, consumed = 25
        )
        assertEquals(35, p.current)
        assertEquals(false, p.stockLow)
        assertEquals(true, p.copy(consumed = 31).stockLow)
    }

    @Test
    fun shortfallOnlyBelowSafetyStock() {
        assertEquals(0, StockCalculator.productShortfall(current = 50, safetyStock = 0))
        assertEquals(0, StockCalculator.productShortfall(current = 50, safetyStock = 40))
        assertEquals(15, StockCalculator.productShortfall(current = 25, safetyStock = 40))
        assertEquals(50, StockCalculator.productShortfall(current = -10, safetyStock = 40))
    }

    @Test
    fun productMonthPlanFillsNeedWhenHigherThanFinished() {
        val need = StockCalculator.requiredFromPlans(
            finishedPlans = mapOf(100L to 10),
            finishedBom = mapOf(100L to listOf(1L to 1)),
            productBoms = mapOf(1L to listOf(7L to 2.0)),
            productPlans = mapOf(1L to 20)
        )
        assertEquals(40.0, need.getValue(7L), 0.0001)
    }

    @Test
    fun requiredMaterialsIncludePlanAndProductShortfall() {
        val need = StockCalculator.requiredFromPlans(
            finishedPlans = mapOf(100L to 10),
            finishedBom = mapOf(100L to listOf(1L to 2)),
            productBoms = mapOf(1L to listOf(7L to 0.5), 2L to listOf(7L to 1.0)),
            productShortfalls = mapOf(2L to 4)
        )
        assertEquals(14.0, need.getValue(7L), 0.0001)
    }

    @Test
    fun a1spkCostAndRatio() {
        val cost = 19415.0 + 26369.0 + 23175.0 + 990.0
        assertEquals(69949.0, cost, 0.01)
        assertEquals(0.689, StockCalculator.materialRatio(cost, 101500.0), 0.001)
    }
}
