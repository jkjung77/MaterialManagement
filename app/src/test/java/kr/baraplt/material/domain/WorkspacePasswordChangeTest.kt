package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspacePasswordChangeTest {
    @Test
    fun blankCurrentIsRejected() {
        assertEquals(
            "현재 공장 암호를 입력하세요",
            WorkspacePasswordChange.validate("", "newpass", "newpass")
        )
    }

    @Test
    fun shortNewPasswordIsRejected() {
        assertEquals(
            "공장 암호는 4자 이상입니다",
            WorkspacePasswordChange.validate("oldpass", "123", "123")
        )
    }

    @Test
    fun confirmMismatchIsRejected() {
        assertEquals(
            "공장 암호가 일치하지 않습니다",
            WorkspacePasswordChange.validate("oldpass", "newpass", "other")
        )
    }

    @Test
    fun sameAsCurrentIsRejected() {
        assertEquals(
            "현재 암호와 같습니다",
            WorkspacePasswordChange.validate("same12", "same12", "same12")
        )
    }

    @Test
    fun validChangePasses() {
        assertNull(WorkspacePasswordChange.validate("oldpass", "newpass", "newpass"))
    }
}
