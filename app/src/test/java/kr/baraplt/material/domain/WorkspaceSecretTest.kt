package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSecretTest {

    @Test
    fun sameFactoryAndPasswordHashEqual() {
        val a = WorkspaceSecret.hash("경주1공장", "alpha12")
        val b = WorkspaceSecret.hash("경주1공장", "alpha12")
        assertEquals(a, b)
        assertTrue(WorkspaceSecret.matches("경주1공장", "alpha12", a))
    }

    @Test
    fun differentPasswordDoesNotMatch() {
        val hash = WorkspaceSecret.hash("경주1공장", "alpha12")
        assertFalse(WorkspaceSecret.matches("경주1공장", "wrong", hash))
    }

    @Test
    fun samePasswordDifferentFactoryDoesNotMatch() {
        val hash = WorkspaceSecret.hash("경주1공장", "alpha12")
        assertNotEquals(hash, WorkspaceSecret.hash("성남공장", "alpha12"))
        assertFalse(WorkspaceSecret.matches("성남공장", "alpha12", hash))
    }

    @Test
    fun shortPasswordRejected() {
        assertEquals("공장 암호는 4자 이상입니다", WorkspaceSecret.validate("123"))
    }

    @Test
    fun confirmMismatchRejected() {
        assertEquals("공장 암호가 일치하지 않습니다", WorkspaceSecret.validate("1234", "1235"))
    }
}
