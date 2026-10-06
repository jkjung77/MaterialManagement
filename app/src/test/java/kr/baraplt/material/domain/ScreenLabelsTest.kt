package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenLabelsTest {
    @Test
    fun roundTripsForSameFactory() {
        val labels = ScreenLabels("물류", "출하", "서열", "물류결산")
        assertEquals(labels, ScreenLabels.decode(labels.encode("경주1공장"), "경주1공장"))
    }

    @Test
    fun otherFactoryGetsNoLabels() {
        val raw = ScreenLabels("물류", "출하", "서열", "물류결산").encode("경주1공장")
        assertEquals(ScreenLabels(), ScreenLabels.decode(raw, "울산공장"))
    }

    @Test
    fun cleansParenthesesNewlinesAndLength() {
        val labels = ScreenLabels("(물류)", "출\n하", "  서열 ", "아주아주아주긴물류결산이름").cleaned()
        assertEquals(ScreenLabels("물류", "출 하", "서열", "아주아주아주긴물류결"), labels)
    }

    @Test
    fun subtitleIsAppendedOnlyWhenPresent() {
        assertEquals("만능자재관리(물류)", ScreenLabels.withSub("만능자재관리", "물류"))
        assertEquals("자재", ScreenLabels.withSub("자재", ""))
    }
}
