package lk.ac.ucsc.scs3311.smarthome

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import lk.ac.ucsc.scs3311.smarthome.data.AppContainer
import lk.ac.ucsc.scs3311.smarthome.notifications.SafetyNotifications

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
        SafetyNotifications.ensureChannels(this)
        subscribeToSafetyAlerts()
    }

    /**
     * The worker publishes cut-off alerts to a topic rather than to individual
     * device tokens, so there is no token registry to keep in sync and every
     * phone in the household is reached. Subscribing is idempotent.
     */
    private fun subscribeToSafetyAlerts() {
        if (container.isDemo) return // the demo flavour has no Firebase project

        runCatching {
            FirebaseMessaging.getInstance()
                .subscribeToTopic(SAFETY_TOPIC)
                .addOnFailureListener { Log.w(TAG, "Could not subscribe to $SAFETY_TOPIC", it) }
        }
    }

    private companion object {
        const val TAG = "HomeSenseApp"

        /** Must match `SAFETY_TOPIC` in worker/src/notifications.ts. */
        const val SAFETY_TOPIC = "safety-alerts"
    }
}
