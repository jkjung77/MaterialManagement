package kr.baraplt.material.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    @Test
    fun writesWorkbookThatExcelCanOpen() {
        val bytes = XlsxWriter.write(
            listOf(
                XlsxWriter.Sheet(
                    "자재",
                    listOf(
                        listOf("번호", "품명"),
                        listOf(1, "원단(청색)")
                    )
                )
            )
        )
        val names = zipNames(bytes)
        assertTrue(names.contains("[Content_Types].xml"))
        assertTrue(names.contains("xl/workbook.xml"))
        assertTrue(names.contains("xl/worksheets/sheet1.xml"))
        val sheet = zipEntry(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("원단(청색)"))
        assertTrue(sheet.contains("번호"))
    }

    @Test
    fun escapesXmlSpecialChars() {
        val bytes = XlsxWriter.write(
            listOf(XlsxWriter.Sheet("시트", listOf(listOf("A&B<C>"))))
        )
        val sheet = zipEntry(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("A&amp;B&lt;C&gt;"))
    }

    private fun zipNames(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
            }
        }
        return names
    }

    private fun zipEntry(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("missing $name")
    }
}
