package kr.baraplt.material.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ItemPhotoStoreTest {
    @Test
    fun saveMoveAndTombstoneKeepIndex() {
        val root = File.createTempFile("photos", "").apply {
            delete()
            mkdirs()
        }
        val store = ItemPhotoStore(root)
        store.saveJpeg("material", 1, byteArrayOf(1, 2, 3), updatedAt = 10)
        assertEquals(3, store.read("material", 1)?.size)
        store.move("material", 1, 2)
        assertNull(store.read("material", 1))
        assertEquals(3, store.read("material", 2)?.size)
        val moved = store.stamps().associateBy { it.codeNo }
        assertTrue(moved.getValue(1).deleted)
        assertFalse(moved.getValue(2).deleted)
        store.tombstone("material", 2, updatedAt = 30)
        assertNull(store.read("material", 2))
        assertTrue(store.stamps().first { it.codeNo == 2 }.deleted)
        root.deleteRecursively()
    }
}
