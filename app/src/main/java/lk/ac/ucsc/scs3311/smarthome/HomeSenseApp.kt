package lk.ac.ucsc.scs3311.smarthome

import android.app.Application
import lk.ac.ucsc.scs3311.smarthome.data.AppContainer
import lk.ac.ucsc.scs3311.smarthome.notifications.SafetyNotifications

/**
 * Application entry point.
 *
 * Realtime Database disk persistence must be switched on exactly once, before
 * any other call touches the database, so the whole data layer is constructed
 * here rather than lazily from a screen.
 *
 * Push subscription is deliberately not performed here. It depends on which
 * household is active, which is not known until the session is restored, so
 * [AppContainer] tracks it and adjusts the subscription as the active household
 * changes.
 */
class HomeSenseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SafetyNotifications.ensureChannels(this)
    }
}
