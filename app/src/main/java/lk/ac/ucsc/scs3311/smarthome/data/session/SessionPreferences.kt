package lk.ac.ucsc.scs3311.smarthome.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * The small amount of state that must outlive the process but does not belong
 * in the cloud: which household the user was last looking at, and which account
 * the on-device cache belongs to.
 */
class SessionPreferences(private val context: Context) {

    val activeHomeId: Flow<String?> =
        context.sessionDataStore.data.map { it[KEY_ACTIVE_HOME] }

    suspend fun setActiveHomeId(homeId: String?) {
        context.sessionDataStore.edit { preferences ->
            if (homeId == null) preferences.remove(KEY_ACTIVE_HOME)
            else preferences[KEY_ACTIVE_HOME] = homeId
        }
    }

    /**
     * The account the Room cache was populated for.
     *
     * Room holds usage history, alerts and the floor layout. If a second person
     * signs in on the same device and this is not checked, they see the previous
     * account's data until the first cloud snapshot happens to overwrite it,
     * which is a privacy leak rather than a cosmetic glitch.
     */
    suspend fun cachedAccountUid(): String? =
        context.sessionDataStore.data.first()[KEY_CACHE_OWNER]

    suspend fun setCachedAccountUid(uid: String?) {
        context.sessionDataStore.edit { preferences ->
            if (uid == null) preferences.remove(KEY_CACHE_OWNER)
            else preferences[KEY_CACHE_OWNER] = uid
        }
    }

    private companion object {
        val KEY_ACTIVE_HOME = stringPreferencesKey("active_home_id")
        val KEY_CACHE_OWNER = stringPreferencesKey("cache_owner_uid")
    }
}
