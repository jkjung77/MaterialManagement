package kr.baraplt.material

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kr.baraplt.material.domain.YearMonthKey

class MaterialApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = container.settings
            if (!settings.seeded.first()) {
                container.repository.seedSample()
                settings.setSeeded(true)
                settings.setWorkingMonth(YearMonthKey(2026, 8))
                settings.setStaffName("담당자")
            }
        }
    }
}
