package kr.baraplt.material

import android.content.Context
import kr.baraplt.material.data.AppDatabase
import kr.baraplt.material.data.SettingsStore
import kr.baraplt.material.data.repo.AppRepository
import kr.baraplt.material.data.sync.MaterialApi
import kr.baraplt.material.data.sync.SyncCoordinator

class AppContainer(private val context: Context) {
    val settings: SettingsStore = SettingsStore(context.applicationContext)
    val api: MaterialApi = MaterialApi()

    @Volatile
    private var boundId: String? = null

    @Volatile
    private var db: AppDatabase? = null

    @Volatile
    private var repositoryField: AppRepository? = null

    val repository: AppRepository
        get() = repositoryField ?: error("공장 ID가 아직 연결되지 않았습니다")

    fun bind(workspaceId: String) {
        synchronized(this) {
            if (boundId == workspaceId && repositoryField != null) return
            db?.close()
            val opened = AppDatabase.create(context.applicationContext, workspaceId)
            db = opened
            repositoryField = AppRepository(opened)
            boundId = workspaceId
        }
    }

    fun sync(): SyncCoordinator = SyncCoordinator(settings, api, repository)
}
