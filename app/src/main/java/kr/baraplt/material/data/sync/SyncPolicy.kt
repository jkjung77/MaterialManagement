package kr.baraplt.material.data.sync

object SyncPolicy {
    fun keepLocalMasters(serverMaterialCount: Int, localMaterialCount: Int): Boolean {
        return serverMaterialCount == 0 && localMaterialCount > 0
    }
}
