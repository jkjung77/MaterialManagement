package kr.baraplt.material.data

import org.junit.Assert.assertEquals
import org.junit.Test

class XlsxReaderTest {
    @Test
    fun readsBackWhatWriterWrote() {
        val bytes = XlsxWriter.write(
            listOf(
                XlsxWriter.Sheet("안내", listOf(listOf("공장", "경주1공장"))),
                XlsxWriter.Sheet(
                    "BOM",
                    listOf(
                        listOf("단품번호", "단품명", "자재번호", "US"),
                        listOf(1, "성형 JK <LH> & RH", 2, 0.238)
                    )
                )
            )
        )
        val sheets = XlsxReader.sheets(bytes)
        assertEquals(listOf("안내", "BOM"), sheets.keys.toList())
        assertEquals(listOf("1", "성형 JK <LH> & RH", "2", "0.238"), sheets.getValue("BOM")[1])
    }

    @Test
    fun columnLettersMapToIndex() {
        assertEquals(0, XlsxReader.columnIndex("A1"))
        assertEquals(25, XlsxReader.columnIndex("Z9"))
        assertEquals(26, XlsxReader.columnIndex("AA3"))
    }
}
