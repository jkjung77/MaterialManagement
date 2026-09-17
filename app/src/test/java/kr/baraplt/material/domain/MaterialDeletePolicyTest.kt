package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaterialDeletePolicyTest {
    @Test
    fun inboundBlocksDelete() {
        assertEquals("입출고 이력을 먼저 지우세요", MaterialDeletePolicy.blockReason(1, 0))
    }

    @Test
    fun bomBlocksDelete() {
        assertEquals("단품 투입자재에서 먼저 빼세요", MaterialDeletePolicy.blockReason(0, 1))
    }

    @Test
    fun unusedCanDelete() {
        assertNull(MaterialDeletePolicy.blockReason(0, 0))
    }
}
