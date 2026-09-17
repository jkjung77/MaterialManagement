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

    private fun key(
        codeNo: Int = 1,
        materialId: Long = 1,
        type: String = "INBOUND",
        qty: Double = 100.0,
        occurredOn: String = "2026-09-15",
        unitPrice: Double = 100.0
    ) = PendingAfterImport.MovementKey(codeNo, materialId, type, qty, occurredOn, unitPrice)
}
