package kr.baraplt.material.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun emptyServerDoesNotWipeLocalMaterials() {
        assertTrue(SyncPolicy.keepLocalMasters(serverMaterialCount = 0, localMaterialCount = 1))
    }

    @Test
    fun serverSnapshotReplacesWhenItHasMaterials() {
        assertFalse(SyncPolicy.keepLocalMasters(serverMaterialCount = 1, localMaterialCount = 1))
    }

    @Test
    fun bothEmptyRestoresServer() {
        assertFalse(SyncPolicy.keepLocalMasters(serverMaterialCount = 0, localMaterialCount = 0))
    }
}
