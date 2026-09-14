package kr.baraplt.material

import android.content.Context
import kr.baraplt.material.data.AppDatabase
import kr.baraplt.material.data.SettingsStore
import kr.baraplt.material.data.repo.AppRepository

class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.create(context.applicationContext)
    val settings: SettingsStore = SettingsStore(context.applicationContext)
    val repository: AppRepository = AppRepository(db)
}
