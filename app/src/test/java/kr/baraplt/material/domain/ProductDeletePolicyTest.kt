package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductDeletePolicyTest {
    @Test
    fun productionBlocksDelete() {
        assertEquals("생산실적을 먼저 지우세요", ProductDeletePolicy.blockReason(1, 0))
    }

    @Test
    fun finishedBlocksDelete() {
        assertEquals("완제품 구성에서 먼저 빼세요", ProductDeletePolicy.blockReason(0, 1))
    }

    @Test
    fun unusedCanDelete() {
        assertNull(ProductDeletePolicy.blockReason(0, 0))
    }
}
