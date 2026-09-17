package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceIdTest {

    @Test
    fun sameFactoryNameIsSameId() {
        assertEquals("경주1공장", WorkspaceId.normalize(" 경주1공장 "))
        assertEquals(
            WorkspaceId.normalize("경주1공장"),
            WorkspaceId.normalize("경주1공장")
        )
    }

    @Test
    fun blankIsRejected() {
        assertEquals("공장 ID를 입력하세요", WorkspaceId.validate("   "))
    }

    @Test
    fun tooLongIsRejected() {
        assertEquals("공장 ID는 32자까지입니다", WorkspaceId.validate("가".repeat(33)))
    }

    @Test
    fun validExamplesPass() {
        listOf("경주1공장", "경주2공장", "성남공장").forEach {
            assertNull(WorkspaceId.validate(it))
        }
    }

    @Test
    fun dbFileIsPerFactory() {
        assertEquals("material_경주1공장.db", WorkspaceId.dbFileName("경주1공장"))
        assertEquals("material_성남공장.db", WorkspaceId.dbFileName("성남공장"))
        assertTrue(WorkspaceId.dbFileName("경주1공장") != WorkspaceId.dbFileName("경주2공장"))
    }

    @Test
    fun unsafeFileCharsAreReplaced() {
        assertEquals("material_A_B.db", WorkspaceId.dbFileName("A/B"))
    }
}
