package kr.baraplt.material.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAfterImportTest {
    @Test
    fun importDropsMovementAndProduction() {
        val kept = listOf("movement", "production", "stocktake", "close", "delete_movement")
            .filter { PendingAfterImport.shouldKeepAfterImport(it) }
        assertEquals(listOf("stocktake", "close", "delete_movement"), kept)
    }

    @Test
    fun inboundAlreadyOnServerByCode() {
        val pending = key(codeNo = 1, materialId = 99)
        val server = key(codeNo = 1, materialId = 7)
        assertTrue(PendingAfterImport.alreadyOnServer(pending, listOf(server)))
    }

    @Test
    fun differentQtyIsNotAlreadyOnServer() {
        val pending = key(qty = 100.0)
        val server = key(qty = 200.0)
        assertFalse(PendingAfterImport.alreadyOnServer(pending, listOf(server)))
    }

    @Test
    fun deletedInboundIsRemovedFromSnapshot() {
        val server = listOf(key(qty = 100.0), key(qty = 20.0), key(qty = 500.0))
        val left = PendingAfterImport.remainingAfterDelete(server, listOf(key(qty = 100.0)))
        assertEquals(listOf(key(qty = 20.0), key(qty = 500.0)), left)
    }

    @Test
    fun differentDateIsNotAlreadyOnServer() {
        val pending = key(occurredOn = "2026-09-15")
        val server = key(occurredOn = "2026-09-16")
        assertFalse(PendingAfterImport.alreadyOnServer(pending, listOf(server)))
    }

    @Test
    fun secondSameInboundOfTheDayIsStillSent() {
        val onServer = listOf(PendingAfterImport.ServerMovement("uid-morning", key()))
        val pending = listOf("uid-afternoon" to key())
        assertEquals(emptySet<Int>(), PendingAfterImport.pendingOnServer(pending, onServer))
    }

    @Test
    fun pendingWithSameUidIsAlreadyOnServer() {
        val onServer = listOf(PendingAfterImport.ServerMovement("uid-1", key()))
        assertEquals(setOf(0), PendingAfterImport.pendingOnServer(listOf("uid-1" to key()), onServer))
    }

    @Test
    fun eachImportedRowCoversOnlyOnePending() {
        val onServer = listOf(PendingAfterImport.ServerMovement("import-1-1-INBOUND-100.0-2026-09-15", key()))
        val pending = listOf("a" to key(), "b" to key())
        assertEquals(setOf(0), PendingAfterImport.pendingOnServer(pending, onServer))
    }

    @Test
    fun oneDeleteHidesOnlyOneOfTwinMovementsPreferringId() {
        val server = listOf(10L to key(), 11L to key())
        assertEquals(setOf(1), PendingAfterImport.hiddenByDeletes(server, listOf(11L to key())))
        assertEquals(setOf(0), PendingAfterImport.hiddenByDeletes(server, listOf(99L to key())))
        assertEquals(setOf(0, 1), PendingAfterImport.hiddenByDeletes(server, listOf(10L to key(), 11L to key())))
    }

    @Test
    fun deletingUnsentMovementCancelsOnePendingInsteadOfDeletingOnServer() {
        val pending = listOf(key(qty = 5.0), key(), key())
        assertEquals(1, PendingAfterImport.unsentMovementIndex(pending, key()))
        assertEquals(-1, PendingAfterImport.unsentMovementIndex(pending, key(qty = 7.0)))
    }

    private fun key(
        codeNo: Int = 1,
        materialId: Long = 1,
        type: String = "INBOUND",
        qty: Double = 100.0,
        occurredOn: String = "2026-09-15",
        unitPrice: Double = 100.0
    ) = PendingAfterImport.MovementKey(codeNo, materialId, type, qty, occurredOn, unitPrice)
}
