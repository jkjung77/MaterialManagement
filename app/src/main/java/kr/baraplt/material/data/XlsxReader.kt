package kr.baraplt.material.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object XlsxReader {
    fun sheets(bytes: ByteArray): Map<String, List<List<String>>> {
        val files = unzip(bytes)
        val workbook = files["xl/workbook.xml"] ?: error("엑셀 파일이 아닙니다")
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { relTargets(it) }.orEmpty()
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) }.orEmpty()
        val out = LinkedHashMap<String, List<List<String>>>()
        elements(parse(workbook), "sheet").forEach { sheet ->
            val name = sheet.getAttribute("name")
            val relId = sheet.getAttributeNS(REL_NS, "id").ifBlank { sheet.getAttribute("r:id") }
            val target = rels[relId] ?: return@forEach
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val xml = files[path] ?: return@forEach
            out[name] = rows(xml, shared)
        }
        return out
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) files[entry.name.removePrefix("/")] = zip.readBytes()
            }
        }
        return files
    }

    private fun relTargets(xml: ByteArray): Map<String, String> =
        elements(parse(xml), "Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }

    private fun sharedStrings(xml: ByteArray): List<String> =
        elements(parse(xml), "si").map { si -> elements(si, "t").joinToString("") { it.textContent } }

    private fun rows(xml: ByteArray, shared: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        elements(parse(xml), "row").forEach { row ->
            val rowIndex = row.getAttribute("r").toIntOrNull()?.minus(1) ?: result.size
            while (result.size < rowIndex) result += emptyList<String>()
            val cells = mutableListOf<String>()
            elements(row, "c").forEach { c ->
                val col = columnIndex(c.getAttribute("r")) ?: cells.size
                while (cells.size < col) cells += ""
                cells += cellText(c, shared)
            }
            result += cells
        }
        return result
    }

    private fun cellText(c: Element, shared: List<String>): String {
        val value = elements(c, "v").firstOrNull()?.textContent.orEmpty()
        return when (c.getAttribute("t")) {
            "s" -> value.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> elements(c, "t").joinToString("") { it.textContent }
            "b" -> if (value == "1") "TRUE" else "FALSE"
            else -> value
        }.trim()
    }

    internal fun columnIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    private fun parse(xml: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
    }

    private fun elements(root: Element, local: String): List<Element> {
        val nodes = root.getElementsByTagNameNS("*", local)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
}
