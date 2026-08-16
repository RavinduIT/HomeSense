package lk.ac.ucsc.scs3311.smarthome.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.BuildConfig
import lk.ac.ucsc.scs3311.smarthome.data.auth.AuthRepository
import lk.ac.ucsc.scs3311.smarthome.data.auth.AuthState
import lk.ac.ucsc.scs3311.smarthome.data.auth.FakeAuthRepository
import lk.ac.ucsc.scs3311.smarthome.data.auth.FakeMembershipRepository
import lk.ac.ucsc.scs3311.smarthome.data.auth.FirebaseAuthRepository
import lk.ac.ucsc.scs3311.smarthome.data.auth.FirebaseMembershipRepository
import lk.ac.ucsc.scs3311.smarthome.data.auth.MembershipRepository
import lk.ac.ucsc.scs3311.smarthome.data.local.HomeSenseDatabase
import lk.ac.ucsc.scs3311.smarthome.data.remote.FakeRemoteSource
import lk.ac.ucsc.scs3311.smarthome.data.remote.RealtimeDatabaseSource
import lk.ac.ucsc.scs3311.smarthome.data.remote.RemoteHomeSource
import lk.ac.ucsc.scs3311.smarthome.data.repository.DefaultHomeRepository
import lk.ac.ucsc.scs3311.smarthome.data.repository.HomeRepository
import lk.ac.ucsc.scs3311.smarthome.data.session.SessionPreferences

/**
 * Manual dependency container.
 *
 * A dependency injection framework would be a further component to understand
 * and defend for an application with one repository. This is a few dozen lines
 * and does the same job. Everything below the interface layer is constructed
 * exactly once, here.
 */
class AppContainer(private val context: Context) {

    /**
     * Application-scoped, so the database listeners feeding the repository
     * outlive any single screen. Cancelled only with the process.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = HomeSenseDatabase.get(context)

    private val preferences = SessionPreferences(context)

    /**
     * Non-null only in the `demo` flavour, where it also drives the on-screen
     * fault-injection controls. Live builds keep this null.
     */
    var fakeSource: FakeRemoteSource? = null
        private set

    private val firebaseDatabase: FirebaseDatabase? = createFirebaseDatabase()

    val authRepository: AuthRepository = createAuthRepository()

    val membershipRepository: MembershipRepository = createMembershipRepository()

    private val remote: RemoteHomeSource = createRemoteSource()

    val repository: HomeRepository = DefaultHomeRepository(
        remote = remote,
        db = database,
        scope = scope,
        activeHomeId = membershipRepository.activeHomeId,
    ).also { it.start() }

    val isDemo: Boolean get() = fakeSource != null

    init {
        observeAccountChanges()
        observeHouseholdForNotifications()
    }

    // ---- construction -------------------------------------------------------

    private fun createFirebaseDatabase(): FirebaseDatabase? {
        if (BuildConfig.USE_FAKE_BACKEND) return null
        return runCatching {
            FirebaseDatabase.getInstance().apply {
                // Must be set before any other database call. Keeps the last
                // known tree on disk so the application renders immediately on
                // launch and queues writes made while offline.
                runCatching { setPersistenceEnabled(true) }
            }
        }.getOrElse { error ->
            // Reached when google-services.json is absent from a live build.
            Log.w(TAG, "Firebase unavailable; falling back to the in-memory backend", error)
            null
        }
    }

    private fun createAuthRepository(): AuthRepository {
        val db = firebaseDatabase ?: return FakeAuthRepository()
        return runCatching {
            FirebaseAuthRepository(FirebaseAuth.getInstance(), db) as AuthRepository
        }.getOrElse { FakeAuthRepository() }
    }

    private fun createMembershipRepository(): MembershipRepository {
        val db = firebaseDatabase
            ?: return FakeMembershipRepository(authRepository, preferences)
        return runCatching {
            FirebaseMembershipRepository(db, authRepository, preferences) as MembershipRepository
        }.getOrElse { FakeMembershipRepository(authRepository, preferences) }
    }

    private fun createRemoteSource(): RemoteHomeSource {
        val db = firebaseDatabase
            ?: return FakeRemoteSource(scope).also { fakeSource = it }
        return RealtimeDatabaseSource(db, FirebaseAuth.getInstance())
    }

    // ---- session hygiene ----------------------------------------------------

    /**
     * Clears the on-device cache when the signed-in account changes.
     *
     * Room holds usage history, alerts and the floor layout. Without this, a
     * second person signing in on the same device would see the previous
     * account's household until the first cloud snapshot happened to overwrite
     * it. That is a disclosure of another person's data, not a display glitch,
     * so the cache is wiped on any change of account and on sign-out.
     */
    private fun observeAccountChanges() {
        scope.launch {
            authRepository.authState
                .map { state -> (state as? AuthState.SignedIn)?.account?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    val cachedFor = preferences.cachedAccountUid()
                    if (cachedFor != null && cachedFor != uid) {
                        Log.i(TAG, "Account changed; clearing the local cache")
                        clearLocalCache()
                        preferences.setActiveHomeId(null)
                    }
                    preferences.setCachedAccountUid(uid)
                }
        }
    }

    private suspend fun clearLocalCache() {
        runCatching {
            database.usageDao().clear()
            database.alertDao().clear()
            database.deviceLayoutDao().clear()
            database.floorDao().clear()
        }.onFailure { Log.w(TAG, "Could not clear the local cache", it) }
    }

    /**
     * Keeps the push subscription aligned with the active household.
     *
     * Cut-off alerts are published to a topic named after the household. A
     * single shared topic would deliver every household's safety alerts to every
     * installation, so the subscription follows the active home and the previous
     * one is released.
     */
    private fun observeHouseholdForNotifications() {
        if (isDemo || firebaseDatabase == null) return

        scope.launch {
            var subscribed: String? = null
            membershipRepository.activeHomeId.distinctUntilChanged().collect { homeId ->
                val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull()
                    ?: return@collect

                subscribed?.let { previous ->
                    runCatching { messaging.unsubscribeFromTopic(topicFor(previous)) }
                }
                subscribed = homeId
                homeId?.let {
                    runCatching { messaging.subscribeToTopic(topicFor(it)) }
                        .onFailure { error -> Log.w(TAG, "Could not subscribe to alerts", error) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AppContainer"

        /**
         * Must match the topic the worker publishes to, in
         * `worker/src/notifications.ts`.
         */
        fun topicFor(homeId: String): String = "home-$homeId"
    }
}
