package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeLookupTest {
    private data class Item(val id: Long, val codeNo: Int, val barcode: String)

    private val items = listOf(
        Item(1, 1, "VN05L"),
        Item(2, 2, ""),
        Item(3, 33, "2B3L"),
        Item(4, 34, "")
    )

    private fun pick(scanned: String) = BarcodeLookup.pick(scanned, items, { it.barcode }, { it.codeNo })

    @Test
    fun barcodeMatchIgnoresCaseAndSpaces() {
        assertEquals(1L, pick(" vn05l ")?.id)
    }

    @Test
    fun barcodeWinsOverNumber() {
        assertEquals(3L, pick("2B3L")?.id)
    }

    @Test
    fun numberMatchesExactCodeOnly() {
        assertEquals(2L, pick("2")?.id)
        assertEquals(3L, pick("33")?.id)
    }

    @Test
    fun unknownCodeReturnsNull() {
        assertNull(pick("ZZZ"))
        assertNull(pick("99"))
        assertNull(pick(""))
    }

    @Test
    fun duplicateBarcodeIsNotAutoOpened() {
        val dup = items + Item(5, 50, "VN05L")
        assertNull(BarcodeLookup.pick("VN05L", dup, { it.barcode }, { it.codeNo }))
    }
}
