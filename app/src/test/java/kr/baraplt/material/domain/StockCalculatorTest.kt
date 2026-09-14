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
    fun a1spkCostAndRatio() {
        val cost = 19415.0 + 26369.0 + 23175.0 + 990.0
        assertEquals(69949.0, cost, 0.01)
        assertEquals(0.689, StockCalculator.materialRatio(cost, 101500.0), 0.001)
    }
}
