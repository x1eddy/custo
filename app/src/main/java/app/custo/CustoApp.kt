package app.custo

import android.app.Application
import android.content.Context

class CustoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.shizuku.start()
    }
}

val Context.container: AppContainer get() = (applicationContext as CustoApp).container
