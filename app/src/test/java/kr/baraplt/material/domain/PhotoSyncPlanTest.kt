package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSyncPlanTest {
    @Test
    fun newerLocalPhotoUploadsAndNewerRemoteDownloads() {
        val ops = PhotoSyncPlan.ops(
            local = listOf(
                PhotoStamp("material", 1, 200),
                PhotoStamp("material", 2, 50),
                PhotoStamp("product", 3, 10, deleted = true)
            ),
            remote = listOf(
                PhotoStamp("material", 1, 100),
                PhotoStamp("material", 2, 80),
                PhotoStamp("product", 3, 5),
                PhotoStamp("product", 4, 90)
            )
        )
        assertEquals(PhotoSyncAction.UPLOAD, ops[0].action)
        assertEquals(1, ops[0].stamp.codeNo)
        assertEquals(PhotoSyncAction.DOWNLOAD, ops[1].action)
        assertEquals(2, ops[1].stamp.codeNo)
        assertEquals(PhotoSyncAction.DELETE_REMOTE, ops[2].action)
        assertEquals(3, ops[2].stamp.codeNo)
        assertEquals(PhotoSyncAction.DOWNLOAD, ops[3].action)
        assertEquals(4, ops[3].stamp.codeNo)
    }

    @Test
    fun equalTimeAndLocalTombstoneWithoutRemoteAreSkipped() {
        val ops = PhotoSyncPlan.ops(
            local = listOf(
                PhotoStamp("material", 1, 10),
                PhotoStamp("material", 9, 10, deleted = true)
            ),
            remote = listOf(PhotoStamp("material", 1, 10))
        )
        assertEquals(0, ops.size)
    }
}
