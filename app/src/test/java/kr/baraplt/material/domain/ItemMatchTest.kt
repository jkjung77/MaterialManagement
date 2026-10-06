package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemMatchTest {
    @Test
    fun numberMatchesThatCodeOnly() {
        assertTrue(ItemMatch.matches(1, "수지", "1"))
        assertFalse(ItemMatch.matches(10, "수지", "1"))
        assertFalse(ItemMatch.matches(32, "포장비닐", "1"))
        assertTrue(ItemMatch.matches(4, "STEP1", "4. STEP1"))
        assertFalse(ItemMatch.matches(14, "STEP1", "4. STEP1"))
        assertTrue(ItemMatch.matches(8, "원단", "VN05L", "VN05L"))
        assertFalse(ItemMatch.matches(8, "원단", "VN05L", "VN06L"))
        assertTrue(ItemMatch.matches(8, "원단", "vn05", "VN05L"))
    }

    @Test
    fun gradeUsesSavedPercents() {
        assertEquals("좋음", ratioGrade(100.0, 0.50, 55, 70))
        assertEquals("보통", ratioGrade(100.0, 0.64, 55, 70))
        assertEquals("주의", ratioGrade(100.0, 0.64, 40, 50))
    }
}
