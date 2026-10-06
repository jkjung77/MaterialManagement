package kr.baraplt.material.data

import android.content.Context
import kr.baraplt.material.domain.PhotoStamp
import kr.baraplt.material.domain.WorkspaceId
import java.io.File

class ItemPhotoStore(private val root: File) {
    private val lock = Any()
    private val indexFile = File(root, "index.tsv")

    fun stamps(): List<PhotoStamp> = synchronized(lock) { readIndex() }

    fun exists(kind: String, codeNo: Int): Boolean = synchronized(lock) {
        if ((kind != "material" && kind != "product") || codeNo !in 1..500) return false
        imageFile(kind, codeNo).isFile
    }

    fun read(kind: String, codeNo: Int): ByteArray? = synchronized(lock) {
        val file = imageFile(kind, codeNo)
        if (!file.isFile) null else file.readBytes()
    }

    fun saveJpeg(kind: String, codeNo: Int, jpeg: ByteArray, updatedAt: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val file = imageFile(kind, codeNo)
            file.parentFile?.mkdirs()
            file.writeBytes(jpeg)
            put(PhotoStamp(kind, codeNo, updatedAt, deleted = false))
        }
    }

    fun tombstone(kind: String, codeNo: Int, updatedAt: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            imageFile(kind, codeNo).delete()
            put(PhotoStamp(kind, codeNo, updatedAt, deleted = true))
        }
    }

    fun move(kind: String, fromCode: Int, toCode: Int) {
        if (fromCode == toCode) return
        synchronized(lock) {
            val bytes = imageFile(kind, fromCode).takeIf { it.isFile }?.readBytes() ?: return
            val now = System.currentTimeMillis()
            val dest = imageFile(kind, toCode)
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            imageFile(kind, fromCode).delete()
            val next = readIndex().filterNot { it.kind == kind && (it.codeNo == fromCode || it.codeNo == toCode) } +
                listOf(
                    PhotoStamp(kind, toCode, now, deleted = false),
                    PhotoStamp(kind, fromCode, now, deleted = true)
                )
            writeIndex(next)
        }
    }

    private fun put(stamp: PhotoStamp) {
        val next = readIndex().filterNot { it.kind == stamp.kind && it.codeNo == stamp.codeNo } + stamp
        writeIndex(next)
    }

    private fun imageFile(kind: String, codeNo: Int): File {
        require(kind == "material" || kind == "product") { "kind" }
        require(codeNo in 1..500) { "code" }
        return File(root, "$kind/$codeNo.jpg")
    }

    private fun readIndex(): List<PhotoStamp> {
        if (!indexFile.isFile) return emptyList()
        return indexFile.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val kind = parts[0]
            val code = parts[1].toIntOrNull() ?: return@mapNotNull null
            val updated = parts[2].toLongOrNull() ?: return@mapNotNull null
            if (kind != "material" && kind != "product") return@mapNotNull null
            if (code !in 1..500) return@mapNotNull null
            PhotoStamp(kind, code, updated, deleted = parts[3] == "1")
        }
    }

    private fun writeIndex(stamps: List<PhotoStamp>) {
        root.mkdirs()
        val text = stamps.joinToString("\n") { "${it.kind}\t${it.codeNo}\t${it.updatedAt}\t${if (it.deleted) 1 else 0}" }
        indexFile.writeText(if (text.isEmpty()) "" else text + "\n")
    }

    companion object {
        fun forWorkspace(context: Context, workspaceId: String): ItemPhotoStore {
            val safe = WorkspaceId.dbFileName(workspaceId).removeSuffix(".db")
            return ItemPhotoStore(File(context.applicationContext.filesDir, "item-photos/$safe"))
        }
    }
}
