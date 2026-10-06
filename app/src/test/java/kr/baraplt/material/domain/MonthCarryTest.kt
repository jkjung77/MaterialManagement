package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MonthCarryTest {
    @Test
    fun keepsLocalNextMonthWhenServerSentOnlyThisMonth() {
        val merged = MonthCarry.keepOtherMonths(
            server = listOf("2026-09" to 1100),
            local = listOf("2026-09" to 0, "2026-10" to 880),
            monthOf = { it.first }
        )
        assertEquals(listOf("2026-09" to 1100, "2026-10" to 880), merged)
    }

    @Test
    fun usesServerWhenItSentEveryMonth() {
        val merged = MonthCarry.keepOtherMonths(
            server = listOf("2026-09" to 1100, "2026-10" to 880),
            local = listOf("2026-10" to 1),
            monthOf = { it.first }
        )
        assertEquals(listOf("2026-09" to 1100, "2026-10" to 880), merged)
    }
}
