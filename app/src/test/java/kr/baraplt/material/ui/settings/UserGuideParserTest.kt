package kr.baraplt.material.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGuideParserTest {
    @Test
    fun headingAndBullet() {
        val blocks = UserGuideParser.parse("## 1. 제목\n\n- 항목")
        assertEquals(listOf(GuideBlock.Heading("1. 제목"), GuideBlock.Bullet("항목")), blocks)
    }

    @Test
    fun skipsSeparatorAndCleansMarks() {
        val blocks = UserGuideParser.parse("# 안내\n\n---\n\n**중요** 내용")
        assertEquals(GuideBlock.Title("안내"), blocks[0])
        assertEquals(GuideBlock.Body("중요 내용"), blocks[1])
    }

    @Test
    fun tableSkipsAlignRow() {
        val blocks = UserGuideParser.parse(
            """
            | 칸 | 뜻 |
            |---|---|
            | 홈 | 숫자 |
            """.trimIndent()
        )
        val table = blocks.single() as GuideBlock.Table
        assertEquals(listOf("칸", "뜻"), table.headers)
        assertEquals(listOf(listOf("홈", "숫자")), table.rows)
        assertTrue(table.headers.none { it.contains("---") })
    }
}
