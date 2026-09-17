package kr.baraplt.material.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseSyncTest {
    @Test
    fun pendingCloseKeepsMonthClosed() {
        assertTrue(
            CloseSync.closedAfterPending(
                serverClosed = false,
                pending = listOf("close" to "2026-09"),
                month = "2026-09"
            )
        )
    }

    @Test
    fun pendingReopenOpensMonth() {
        assertFalse(
            CloseSync.closedAfterPending(
                serverClosed = true,
                pending = listOf("reopen" to "2026-09"),
                month = "2026-09"
            )
        )
    }

    @Test
    fun otherMonthDoesNotChange() {
        assertFalse(
            CloseSync.closedAfterPending(
                serverClosed = false,
                pending = listOf("close" to "2026-08"),
                month = "2026-09"
            )
        )
    }
}
