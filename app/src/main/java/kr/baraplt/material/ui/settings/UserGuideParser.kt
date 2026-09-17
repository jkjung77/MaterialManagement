package kr.baraplt.material.ui.settings

sealed class GuideBlock {
    data class Title(val text: String) : GuideBlock()
    data class Heading(val text: String) : GuideBlock()
    data class Subheading(val text: String) : GuideBlock()
    data class Body(val text: String) : GuideBlock()
    data class Bullet(val text: String) : GuideBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : GuideBlock()
}

object UserGuideParser {
    fun parse(markdown: String): List<GuideBlock> {
        val blocks = mutableListOf<GuideBlock>()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val raw = lines[i].trimEnd()
            val line = raw.trim()
            when {
                line.isEmpty() || line == "---" -> i++
                line.startsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = splitRow(lines[i])
                        if (cells.isNotEmpty() && cells.none { it.all { ch -> ch == '-' || ch == ':' || ch == ' ' } }) {
                            tableLines.add(lines[i])
                        }
                        i++
                    }
                    if (tableLines.isNotEmpty()) {
                        val rows = tableLines.map { splitRow(it).map(::clean) }
                        blocks.add(GuideBlock.Table(rows.first(), rows.drop(1)))
                    }
                }
                line.startsWith("### ") -> {
                    blocks.add(GuideBlock.Subheading(clean(line.removePrefix("### "))))
                    i++
                }
                line.startsWith("## ") -> {
                    blocks.add(GuideBlock.Heading(clean(line.removePrefix("## "))))
                    i++
                }
                line.startsWith("# ") -> {
                    blocks.add(GuideBlock.Title(clean(line.removePrefix("# "))))
                    i++
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    blocks.add(GuideBlock.Bullet(clean(line.substring(2))))
                    i++
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    blocks.add(GuideBlock.Bullet(clean(line)))
                    i++
                }
                else -> {
                    blocks.add(GuideBlock.Body(clean(line)))
                    i++
                }
            }
        }
        return blocks
    }

    private fun splitRow(line: String): List<String> {
        return line.trim().trim('|').split("|").map { it.trim() }
    }

    private fun clean(text: String): String =
        text.replace("**", "").replace("`", "")
}
