package kr.baraplt.material.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {
    data class Sheet(val name: String, val rows: List<List<Any?>>)

    fun write(sheets: List<Sheet>): ByteArray {
        val safe = sheets.ifEmpty { listOf(Sheet("Sheet1", emptyList())) }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(safe.size))
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook(safe))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(safe.size))
            put(zip, "xl/styles.xml", styles())
            safe.forEachIndexed { index, sheet ->
                put(zip, "xl/worksheets/sheet${index + 1}.xml", worksheet(sheet))
            }
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, xml: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(xml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes(count: Int): String {
        val sheets = (1..count).joinToString("") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$sheets
</Types>"""
    }

    private fun rootRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook(sheets: List<Sheet>): String {
        val entries = sheets.mapIndexed { index, sheet ->
            """<sheet name="${xml(sheetName(sheet.name))}" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$entries</sheets>
</workbook>"""
    }

    private fun workbookRels(count: Int): String {
        val rels = (1..count).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels
<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun styles(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf/></cellStyleXfs>
<cellXfs count="1"><xf/></cellXfs>
</styleSheet>"""

    private fun worksheet(sheet: Sheet): String {
        val rowsXml = sheet.rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexed { colIndex, value ->
                cellXml(colIndex + 1, rowIndex + 1, value)
            }.joinToString("")
            """<row r="${rowIndex + 1}">$cells</row>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>$rowsXml</sheetData>
</worksheet>"""
    }

    private fun cellXml(col: Int, row: Int, value: Any?): String {
        val ref = colName(col) + row
        return when (value) {
            null -> """<c r="$ref"/>"""
            is Number -> """<c r="$ref"><v>${value}</v></c>"""
            is Boolean -> """<c r="$ref" t="b"><v>${if (value) 1 else 0}</v></c>"""
            else -> """<c r="$ref" t="inlineStr"><is><t>${xml(value.toString())}</t></is></c>"""
        }
    }

    private fun colName(index: Int): String {
        var n = index
        val chars = StringBuilder()
        while (n > 0) {
            n--
            chars.insert(0, ('A' + n % 26))
            n /= 26
        }
        return chars.toString()
    }

    private fun sheetName(name: String): String =
        name.replace(Regex("""[\\/:*?\[\]]"""), " ").take(31).ifBlank { "Sheet" }

    internal fun xml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
