package kr.baraplt.material.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingSyncIdsTest {
    @Test
    fun staleIdUsesOnlyMaterialCode() {
        val resolved = PendingSyncIds.resolve(
            oldId = 1L,
            existingCode = 0,
            idToCode = emptyMap(),
            codeToId = mapOf(1 to 7L)
        )
        assertEquals(1, resolved?.first)
        assertEquals(7L, resolved?.second)
    }

    @Test
    fun knownIdKeepsCode() {
        val resolved = PendingSyncIds.resolve(
            oldId = 3L,
            existingCode = 0,
            idToCode = mapOf(3L to 1),
            codeToId = mapOf(1 to 3L)
        )
        assertEquals(1, resolved?.first)
        assertEquals(3L, resolved?.second)
    }

    @Test
    fun productionUsesProductCodeAfterRemap() {
        val resolved = PendingSyncIds.resolve(
            oldId = 1L,
            existingCode = 12,
            idToCode = emptyMap(),
            codeToId = mapOf(12 to 40L)
        )
        assertEquals(12, resolved?.first)
        assertEquals(40L, resolved?.second)
    }
}
