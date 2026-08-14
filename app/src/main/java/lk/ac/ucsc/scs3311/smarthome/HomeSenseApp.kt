package lk.ac.ucsc.scs3311.smarthome

import android.app.Application

/**
 * Application entry point.
 *
 * Realtime Database disk persistence has to be switched on exactly once, before
 * any other call touches the database, so it is wired here in [onCreate] rather
 * than lazily from a repository. See [lk.ac.ucsc.scs3311.smarthome.data] for
 * where the singletons are assembled.
 */
class HomeSenseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Phase 2 wires the Realtime Database source and the Room cache here.
    }
}
