package kr.baraplt.material.domain

data class PhotoStamp(
    val kind: String,
    val codeNo: Int,
    val updatedAt: Long,
    val deleted: Boolean = false
)

enum class PhotoSyncAction { UPLOAD, DELETE_REMOTE, DOWNLOAD }

data class PhotoSyncOp(val action: PhotoSyncAction, val stamp: PhotoStamp)

object PhotoSyncPlan {
    fun ops(local: List<PhotoStamp>, remote: List<PhotoStamp>): List<PhotoSyncOp> {
        val localBy = local.associateBy { it.kind to it.codeNo }
        val remoteBy = remote.associateBy { it.kind to it.codeNo }
        val out = mutableListOf<PhotoSyncOp>()
        for (key in (localBy.keys + remoteBy.keys).sortedWith(compareBy({ it.first }, { it.second }))) {
            val mine = localBy[key]
            val theirs = remoteBy[key]
            when {
                mine == null && theirs != null -> out += PhotoSyncOp(PhotoSyncAction.DOWNLOAD, theirs)
                mine != null && theirs == null && !mine.deleted -> out += PhotoSyncOp(PhotoSyncAction.UPLOAD, mine)
                mine != null && theirs != null && mine.updatedAt > theirs.updatedAt && mine.deleted ->
                    out += PhotoSyncOp(PhotoSyncAction.DELETE_REMOTE, mine)
                mine != null && theirs != null && mine.updatedAt > theirs.updatedAt ->
                    out += PhotoSyncOp(PhotoSyncAction.UPLOAD, mine)
                mine != null && theirs != null && theirs.updatedAt > mine.updatedAt ->
                    out += PhotoSyncOp(PhotoSyncAction.DOWNLOAD, theirs)
            }
        }
        return out
    }
}
