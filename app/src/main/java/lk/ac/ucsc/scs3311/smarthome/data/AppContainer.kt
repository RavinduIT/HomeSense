package lk.ac.ucsc.scs3311.smarthome.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import lk.ac.ucsc.scs3311.smarthome.BuildConfig
import lk.ac.ucsc.scs3311.smarthome.data.local.HomeSenseDatabase
import lk.ac.ucsc.scs3311.smarthome.data.remote.FakeRemoteSource
import lk.ac.ucsc.scs3311.smarthome.data.remote.RealtimeDatabaseSource
import lk.ac.ucsc.scs3311.smarthome.data.remote.RemoteHomeSource
import lk.ac.ucsc.scs3311.smarthome.data.repository.DefaultHomeRepository
import lk.ac.ucsc.scs3311.smarthome.data.repository.HomeRepository

/**
 * Manual dependency container.
 *
 * A DI framework would be a fourth thing for three people to defend in a viva;
 * this is twenty lines and does the same job for an app with one repository.
 * Everything below the UI is constructed exactly once, here.
 */
class AppContainer(context: Context) {

    /**
     * Application-scoped, so the Firebase listeners feeding the repository's
     * StateFlows outlive any single screen. Cancelled only with the process.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = HomeSenseDatabase.get(context)

    /**
     * Non-null only in the `demo` flavour, where it also drives the on-screen
     * chaos controls. `live` builds keep this null and talk to real Firebase.
     */
    var fakeSource: FakeRemoteSource? = null
        private set

    private val remote: RemoteHomeSource = createRemoteSource()

    val homeId: String =
        if (fakeSource != null) FakeRemoteSource.DEMO_HOME_ID else LIVE_HOME_ID

    val repository: HomeRepository =
        DefaultHomeRepository(remote, database, scope, homeId).also { it.start() }

    val isDemo: Boolean get() = fakeSource != null

    private fun createRemoteSource(): RemoteHomeSource {
        if (BuildConfig.USE_FAKE_BACKEND) {
            return FakeRemoteSource(scope).also { fakeSource = it }
        }
        return runCatching {
            val db = FirebaseDatabase.getInstance().apply {
                // Must be set before any other database call. Keeps the last
                // known tree on disk so the app renders instantly on launch and
                // queues writes made while offline.
                runCatching { setPersistenceEnabled(true) }
            }
            RealtimeDatabaseSource(db, FirebaseAuth.getInstance())
        }.getOrElse { error ->
            // Reached when google-services.json is absent from a `live` build.
            // Falling back keeps the app usable instead of crashing on launch,
            // and the banner in the UI says which backend is actually in use.
            Log.w(TAG, "Firebase unavailable, falling back to the in-memory backend", error)
            FakeRemoteSource(scope).also { fakeSource = it }
        }
    }

    private companion object {
        const val TAG = "AppContainer"

        /**
         * Single-home build. Multi-tenancy is out of scope for the assignment;
         * the tree is already keyed by home id so adding it later is additive.
         */
        const val LIVE_HOME_ID = "home-1"
    }
}
