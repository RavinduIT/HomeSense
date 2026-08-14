package lk.ac.ucsc.scs3311.smarthome

import android.app.Application
import lk.ac.ucsc.scs3311.smarthome.data.AppContainer

/**
 * Application entry point.
 *
 * Realtime Database disk persistence must be switched on exactly once, before
 * any other call touches the database, so the whole data layer is constructed
 * here in [onCreate] rather than lazily from a screen.
 */
class HomeSenseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
