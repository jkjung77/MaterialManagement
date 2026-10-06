package kr.baraplt.material

import android.app.Application
import kr.baraplt.material.ads.AdConfig
import kr.baraplt.material.ads.AdsSdk

class MaterialApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (AdConfig.SHOW_BANNER) AdsSdk.initialize(this)
    }
}
