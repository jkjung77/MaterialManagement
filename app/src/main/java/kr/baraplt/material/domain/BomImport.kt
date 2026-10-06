package kr.baraplt.material.domain

import kotlin.math.abs
import kotlin.math.round

object BomImport {
    const val MAX_LINES = 30

    data class ProductRef(val id: Long, val codeNo: Int, val bom: List<Line>)
    data class Line(val materialId: Long, val materialCode: Int, val usQty: Double)
    data class Change(val productId: Long, val productCode: Int, val lines: List<Line>)
    data class Plan(
        val changes: List<Change>,
        val unchangedProducts: Int,
        val lineCount: Int,
        val skipped: List<String>
    )

    fun plan(
        rows: List<List<String>>,
        products: List<ProductRef>,
        materialIdsByCode: Map<Int, Long>
    ): Plan {
        val header = rows.indexOfFirst { row -> columns(row) != null }
        val cols = rows.getOrNull(header)?.let { columns(it) }
            ?: return Plan(emptyList(), 0, 0, listOf("BOM 시트에서 단품번호·자재번호·US 칸을 찾지 못했습니다"))
        val byCode = products.associateBy { it.codeNo }
        val skipped = mutableListOf<String>()
        val grouped = LinkedHashMap<Int, LinkedHashMap<Int, Double>>()
        rows.drop(header + 1).forEachIndexed { index, row ->
            val excelRow = header + index + 2
            val productText = row.getOrNull(cols.product).orEmpty()
            val materialText = row.getOrNull(cols.material).orEmpty()
            val usText = row.getOrNull(cols.us).orEmpty()
            if (productText.isBlank() && materialText.isBlank() && usText.isBlank()) return@forEachIndexed
            val productCode = leadingNumber(productText)
            val materialCode = leadingNumber(materialText)
            when {
                productCode == null || productCode !in byCode ->
                    skipped += "${excelRow}행: 단품 ${productText.ifBlank { "(빈칸)" }} 없음"
                materialCode == null || materialCode !in materialIdsByCode ->
                    skipped += "${excelRow}행: 자재 ${materialText.ifBlank { "(빈칸)" }} 없음"
                else -> {
                    val lines = grouped.getOrPut(productCode) { LinkedHashMap() }
                    if (materialCode in lines) {
                        skipped += "${excelRow}행: 단품 NO.$productCode 에 자재 NO.$materialCode 가 두 번 있어 뒤 줄은 뺐습니다"
                    } else {
                        lines[materialCode] = parseUs(usText)
                    }
                }
            }
        }
        val changes = mutableListOf<Change>()
        var unchanged = 0
        var lineCount = 0
        grouped.forEach { (productCode, lines) ->
            val product = byCode.getValue(productCode)
            val next = lines.filterValues { it > 0.0 }.map { (code, us) ->
                Line(materialIdsByCode.getValue(code), code, us)
            }
            if (next.size > MAX_LINES) {
                skipped += "단품 NO.$productCode: 투입자재가 ${next.size}개라 ${MAX_LINES}개를 넘어 바꾸지 않았습니다"
                return@forEach
            }
            if (sameLines(product.bom, next)) unchanged++ else {
                changes += Change(product.id, productCode, next)
                lineCount += next.size
            }
        }
        return Plan(changes, unchanged, lineCount, skipped)
    }

    fun hasHeader(rows: List<List<String>>): Boolean = rows.any { columns(it) != null }

    private data class Columns(val product: Int, val material: Int, val us: Int)

    private fun columns(row: List<String>): Columns? {
        fun find(vararg names: String) = row.indexOfFirst { cell ->
            val c = cell.replace(" ", "")
            names.any { c.equals(it, true) }
        }
        val product = find("단품번호", "단품NO", "단품")
        val material = find("자재번호", "자재NO", "자재")
        val us = find("US", "US사용량", "사용량")
        return if (product >= 0 && material >= 0 && us >= 0) Columns(product, material, us) else null
    }

    internal fun leadingNumber(text: String): Int? {
        val t = text.trim().removePrefix("NO.").removePrefix("No.").removePrefix("no.").trim()
        val digits = t.takeWhile { it.isDigit() || it == '.' }
        return digits.toDoubleOrNull()?.takeIf { it == round(it) }?.toInt()
    }

    private fun parseUs(text: String): Double {
        val v = text.replace(",", "").trim().toDoubleOrNull() ?: return 0.0
        return round(v * 1_000_000) / 1_000_000
    }

    private fun sameLines(old: List<Line>, next: List<Line>): Boolean {
        if (old.size != next.size) return false
        return old.zip(next).all { (a, b) -> a.materialCode == b.materialCode && abs(a.usQty - b.usQty) < 1e-9 }
    }
}
