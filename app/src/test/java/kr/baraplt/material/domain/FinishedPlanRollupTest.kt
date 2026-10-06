package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinishedPlanRollupTest {
    @Test
    fun sharedProductSumsFinishedPlans() {
        val refs = FinishedPlanRollup.byProduct(
            listOf(
                finished(codeNo = 1, plan = 100, products = listOf(10L, 11L)),
                finished(codeNo = 2, plan = 50, products = listOf(10L, 11L, 12L))
            )
        )
        assertEquals(150, refs[10L]?.qty)
        assertEquals("NO.1+NO.2", refs[10L]?.sources)
        assertEquals(50, refs[12L]?.qty)
        assertEquals("NO.2", refs[12L]?.sources)
    }

    @Test
    fun zeroPlanAndDuplicateLineAreIgnored() {
        val refs = FinishedPlanRollup.byProduct(
            listOf(
                finished(codeNo = 1, plan = 0, products = listOf(10L)),
                finished(codeNo = 3, plan = 20, products = listOf(10L, 10L))
            )
        )
        assertEquals(20, refs[10L]?.qty)
        assertEquals("NO.3", refs[10L]?.sources)
        assertNull(refs[99L])
    }

    @Test
    fun componentQuantityMultipliesPlan() {
        val refs = FinishedPlanRollup.byProduct(
            listOf(finished(codeNo = 4, plan = 30, products = listOf(10L, 11L), qtys = listOf(2, 1)))
        )
        assertEquals(60, refs[10L]?.qty)
        assertEquals(30, refs[11L]?.qty)
    }

    private fun finished(codeNo: Int, plan: Int, products: List<Long>, qtys: List<Int> = emptyList()) = FinishedSnapshot(
        id = codeNo.toLong(),
        codeNo = codeNo,
        name = "item",
        sellPrice = 0.0,
        productIds = products,
        productNames = emptyList(),
        monthPlan = plan,
        materialCost = 0.0,
        productQtys = qtys
    )
}
