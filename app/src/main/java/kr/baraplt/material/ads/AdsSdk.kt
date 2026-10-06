package kr.baraplt.material.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds

object AdsSdk {
    @Volatile
    private var ready = false
    private val lock = Any()
    private val waiting = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun initialize(context: Context) {
        val app = context.applicationContext
        Thread {
            MobileAds.initialize(app) {
                val queued: List<() -> Unit>
                synchronized(lock) {
                    ready = true
                    queued = waiting.toList()
                    waiting.clear()
                }
                queued.forEach { action -> main.post(action) }
            }
        }.start()
    }

    fun whenReady(action: () -> Unit) {
        val runNow = synchronized(lock) {
            if (ready) {
                true
            } else {
                waiting.add(action)
                false
            }
        }
        if (runNow) main.post(action)
    }
}
