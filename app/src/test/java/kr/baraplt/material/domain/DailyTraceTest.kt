package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTraceTest {
    private val products = listOf(
        TraceItem(1, 3, "JK FRT", "85810"),
        TraceItem(2, 1, "JK RR", "85811")
    )
    private val production = listOf(
        DatedQty(1, "2026-10-02", 50),
        DatedQty(2, "2026-10-02", 10),
        DatedQty(1, "2026-10-03", 4),
        DatedQty(1, "2026-10-04", 0)
    )

    @Test
    fun listsProductsMadeOnTheChosenDayByCodeNo() {
        val lines = DailyTrace.onDay(2, production, products)
        assertEquals(listOf(1, 3), lines.map { it.codeNo })
        assertEquals(10, lines[0].qty)
        assertEquals("85810", lines[1].barcode)
    }

    @Test
    fun skipsZeroQtyAndUnknownItems() {
        val lines = DailyTrace.onDay(4, production + DatedQty(99, "2026-10-04", 7), products)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun marksDaysThatHaveQuantity() {
        assertEquals(setOf(2, 3), DailyTrace.activeDays(production))
    }

    @Test
    fun readsTheDayFromTheDate() {
        assertEquals(2, DailyTrace.dayOf("2026-10-02"))
        assertEquals(null, DailyTrace.dayOf("날짜없음"))
    }
}
