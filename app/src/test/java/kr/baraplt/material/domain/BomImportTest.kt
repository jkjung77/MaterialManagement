package kr.baraplt.material.domain

import kr.baraplt.material.domain.BomImport.Line
import kr.baraplt.material.domain.BomImport.ProductRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BomImportTest {
    private val materials = mapOf(1 to 101L, 2 to 33L, 33 to 1617L, 135 to 20621L)
    private val products = listOf(
        ProductRef(1, 1, listOf(Line(33, 2, 0.238), Line(20621, 135, 0.134))),
        ProductRef(2, 2, listOf(Line(33, 2, 0.238)))
    )
    private val header = listOf("단품번호", "단품명", "자재번호", "자재명", "단위", "US", "순번")

    private fun row(p: String, m: String, us: String) = listOf(p, "", m, "", "", us, "")

    @Test
    fun matchesByNumberNotInternalId() {
        val plan = BomImport.plan(listOf(header, row("2", "33", "0.5")), products, materials)
        val change = plan.changes.single()
        assertEquals(2L, change.productId)
        assertEquals(listOf(Line(1617, 33, 0.5)), change.lines)
    }

    @Test
    fun unchangedProductIsNotRewritten() {
        val plan = BomImport.plan(
            listOf(header, row("1", "2", "0.238"), row("1", "135", "0.134")),
            products,
            materials
        )
        assertTrue(plan.changes.isEmpty())
        assertEquals(1, plan.unchangedProducts)
    }

    @Test
    fun productsNotInSheetAreLeftAlone() {
        val plan = BomImport.plan(listOf(header, row("1", "2", "0.3")), products, materials)
        assertEquals(listOf(1L), plan.changes.map { it.productId })
    }

    @Test
    fun zeroOrBlankUsRemovesLine() {
        val plan = BomImport.plan(
            listOf(header, row("1", "2", "0.238"), row("1", "135", "")),
            products,
            materials
        )
        assertEquals(listOf(Line(33, 2, 0.238)), plan.changes.single().lines)
    }

    @Test
    fun unknownNumbersAreSkippedWithRow() {
        val plan = BomImport.plan(
            listOf(header, row("999", "2", "1"), row("1", "777", "1")),
            products,
            materials
        )
        assertTrue(plan.changes.isEmpty())
        assertEquals(listOf("2행: 단품 999 없음", "3행: 자재 777 없음"), plan.skipped)
    }

    @Test
    fun oldExportFormatWithNameInCellWorks() {
        val old = listOf(listOf("단품", "자재", "US", "순번"), listOf("2 성형 JK", "33 PAD IK", "0.4", "0"))
        val plan = BomImport.plan(old, products, materials)
        assertEquals(listOf(Line(1617, 33, 0.4)), plan.changes.single().lines)
    }

    @Test
    fun excelFloatNoiseIsRounded() {
        val plan = BomImport.plan(listOf(header, row("2", "2", "0.23799999999999999")), products, materials)
        assertTrue(plan.changes.isEmpty())
    }

    @Test
    fun overThirtyLinesIsRejected() {
        val many = (1..31).map { row("2", "$it", "1") }
        val allMaterials = (1..31).associateWith { it.toLong() + 5000 }
        val plan = BomImport.plan(listOf(header) + many, products, allMaterials)
        assertTrue(plan.changes.isEmpty())
        assertTrue(plan.skipped.last().contains("30개를 넘어"))
    }

    @Test
    fun missingHeaderIsReported() {
        val plan = BomImport.plan(listOf(listOf("a", "b")), products, materials)
        assertTrue(plan.skipped.single().contains("찾지 못했습니다"))
    }
}
