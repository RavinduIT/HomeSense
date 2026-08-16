package lk.ac.ucsc.scs3311.smarthome.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import lk.ac.ucsc.scs3311.smarthome.data.session.SessionPreferences
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeInvite
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMember
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMembership
import lk.ac.ucsc.scs3311.smarthome.domain.model.MemberRole
import lk.ac.ucsc.scs3311.smarthome.domain.model.Account

/**
 * In-memory authentication for the `demo` flavour and for tests.
 *
 * It is a real implementation of the contract rather than a bypass: credentials
 * are validated, duplicate registration is rejected, a wrong password fails, and
 * anonymous sessions upgrade in place. The demo build therefore exercises the
 * same sign-in flow, the same error handling and the same session-scoped teardown
 * as the live build, without a Firebase project.
 *
 * Accounts live only for the lifetime of the process, which is the correct
 * behaviour for a build whose purpose is to be demonstrated and discarded.
 */
class FakeAuthRepository : AuthRepository {

    private data class StoredAccount(val account: Account, val password: String)

    private val accounts = mutableMapOf<String, StoredAccount>()
    private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)

    override val authState: Flow<AuthState> = state.asStateFlow()

    override val currentAccount: Account?
        get() = (state.value as? AuthState.SignedIn)?.account

    /** A convenience account so the demo build can be entered in one tap. */
    init {
        accounts["demo@homesense.app"] = StoredAccount(
            Account(
                uid = "demo-user",
                email = "demo@homesense.app",
                displayName = "Demo User",
                isEmailVerified = true,
            ),
            password = "demo1234",
        )
    }

    override suspend fun signUp(displayName: String, email: String, password: String): Account {
        pause()
        val key = email.trim().lowercase()
        validateEmail(key)
        if (password.length < MIN_PASSWORD) throw AuthError.WeakPassword
        if (accounts.containsKey(key)) throw AuthError.EmailAlreadyUsed

        val account = Account(
            uid = "user-${accounts.size + 1}",
            email = key,
            displayName = displayName.trim(),
            isEmailVerified = true,
        )
        accounts[key] = StoredAccount(account, password)
        state.value = AuthState.SignedIn(account)
        return account
    }

    override suspend fun signIn(email: String, password: String): Account {
        pause()
        val key = email.trim().lowercase()
        validateEmail(key)
        val stored = accounts[key] ?: throw AuthError.InvalidCredentials
        if (stored.password != password) throw AuthError.InvalidCredentials
        state.value = AuthState.SignedIn(stored.account)
        return stored.account
    }

    override suspend fun signInAnonymously(): Account {
        pause()
        currentAccount?.let { return it }
        val account = Account(uid = "guest-session", displayName = "Guest", isAnonymous = true)
        state.value = AuthState.SignedIn(account)
        return account
    }

    override suspend fun linkAnonymousAccount(
        displayName: String,
        email: String,
        password: String,
    ): Account {
        pause()
        val current = currentAccount ?: throw AuthError.Unknown("No session to upgrade.")
        val key = email.trim().lowercase()
        validateEmail(key)
        if (password.length < MIN_PASSWORD) throw AuthError.WeakPassword
        if (accounts.containsKey(key)) throw AuthError.EmailAlreadyUsed

        // The uid is preserved, so anything the guest accumulated survives.
        val upgraded = current.copy(
            email = key,
            displayName = displayName.trim(),
            isAnonymous = false,
            isEmailVerified = true,
        )
        accounts[key] = StoredAccount(upgraded, password)
        state.value = AuthState.SignedIn(upgraded)
        return upgraded
    }

    override suspend fun sendPasswordReset(email: String) {
        pause()
        val key = email.trim().lowercase()
        validateEmail(key)
        // Deliberately silent about whether the address is registered, matching
        // the live behaviour: confirming it would disclose who has an account.
    }

    override suspend fun sendEmailVerification() = pause()

    override suspend fun reloadAccount(): Account? = currentAccount

    override suspend fun signOut() {
        state.value = AuthState.SignedOut
    }

    override suspend fun deleteAccount() {
        currentAccount?.email?.let { accounts.remove(it) }
        state.value = AuthState.SignedOut
    }

    private fun validateEmail(email: String) {
        if (!email.contains('@') || !email.substringAfter('@').contains('.')) {
            throw AuthError.InvalidEmail
        }
    }

    /** A short delay so progress indicators and disabled states are visible. */
    private suspend fun pause() = delay(NETWORK_PAUSE_MS)

    private companion object {
        const val MIN_PASSWORD = 8
        const val NETWORK_PAUSE_MS = 450L
    }
}

/**
 * In-memory household membership for the `demo` flavour.
 *
 * A household is created on first use so the demo build lands directly on the
 * dashboard, while still exercising the onboarding types and the active-home
 * selection that the live build depends on.
 */
class FakeMembershipRepository(
    private val authRepository: AuthRepository,
    private val preferences: SessionPreferences,
) : MembershipRepository {

    private val homes = MutableStateFlow(
        listOf(
            HomeMembership(
                homeId = DEMO_HOME,
                homeName = "Demo house",
                role = MemberRole.OWNER,
                joinedAt = 0L,
            ),
        ),
    )

    private val memberList = MutableStateFlow(
        listOf(
            HomeMember(
                uid = "demo-user",
                displayName = "Demo User",
                email = "demo@homesense.app",
                role = MemberRole.OWNER,
            ),
        ),
    )

    override val memberships: Flow<List<HomeMembership>> =
        combine(authRepository.authState, homes) { auth, list ->
            if (auth is AuthState.SignedIn) list else emptyList()
        }.distinctUntilChanged()

    override val activeHomeId: Flow<String?> =
        memberships.map { it.firstOrNull()?.homeId }.distinctUntilChanged()

    override val activeRole: Flow<MemberRole?> =
        memberships.map { it.firstOrNull()?.role }.distinctUntilChanged()

    override fun members(homeId: String): Flow<List<HomeMember>> = memberList

    override suspend fun selectHome(homeId: String) = preferences.setActiveHomeId(homeId)

    override suspend fun createHome(name: String): String {
        val id = "home-${homes.value.size + 1}"
        homes.update {
            it + HomeMembership(id, name.trim().ifBlank { "My home" }, MemberRole.OWNER)
        }
        return id
    }

    override suspend fun joinHome(code: String): String {
        if (code.trim().uppercase() != DEMO_CODE) throw MembershipError.InviteNotFound
        return DEMO_HOME
    }

    override suspend fun createInvite(homeId: String): HomeInvite {
        val now = System.currentTimeMillis()
        return HomeInvite(
            code = DEMO_CODE,
            homeId = homeId,
            homeName = "Demo house",
            createdBy = "demo-user",
            createdAt = now,
            expiresAt = now + HomeInvite.VALIDITY_MS,
        )
    }

    override suspend fun removeMember(homeId: String, uid: String) {
        memberList.update { list -> list.filterNot { it.uid == uid } }
    }

    override suspend fun leaveHome(homeId: String) {
        homes.update { list -> list.filterNot { it.homeId == homeId } }
    }

    private companion object {
        const val DEMO_HOME = "demo-home"
        const val DEMO_CODE = "DEMO2026"
    }
}
