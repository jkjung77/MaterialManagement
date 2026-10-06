package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun exactQtyKeepsSmallUsValues() {
        assertEquals("0.0001", formatQtyExact(0.0001))
        assertEquals(0.0001, parseNumber(formatQtyExact(0.0001))!!, 0.0)
    }

    @Test
    fun exactQtyHasNoGroupingBecauseNumberFieldTurnsCommaIntoDecimalPoint() {
        assertEquals("1100", formatQtyExact(1100.0))
        assertEquals("1234.5", formatQtyExact(1234.5))
        assertEquals("0.238", formatQtyExact(0.238))
        assertEquals("0.000005", formatQtyExact(0.000005))
    }

    @Test
    fun countAcceptsBlankAsZeroAndWholeNumbers() {
        assertEquals(0, parseCount(""))
        assertEquals(12, parseCount("12"))
        assertEquals(1200, parseCount("1,200"))
        assertEquals(12, parseCount("12.0"))
    }

    @Test
    fun countRejectsFractionNegativeAndGarbage() {
        assertEquals(null, parseCount("12.5"))
        assertEquals(null, parseCount("-3"))
        assertEquals(null, parseCount("abc"))
        assertEquals(null, parseCount("99999999999"))
    }

    @Test
    fun exactQtyHidesFloatNoise() {
        assertEquals("0.238", formatQtyExact(0.23799999999999999))
        assertEquals("812", formatQtyExact(812.0000000001))
    }
}
