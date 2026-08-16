package lk.ac.ucsc.scs3311.smarthome.data.auth

import kotlinx.coroutines.flow.Flow
import lk.ac.ucsc.scs3311.smarthome.domain.model.Account
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeInvite
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMember
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMembership

/**
 * Authentication state.
 *
 * [Unknown] is distinct from [SignedOut] on purpose. Firebase restores a
 * persisted session asynchronously, so treating "not yet known" as "signed out"
 * would flash the sign-in screen on every cold start of an already signed-in
 * user.
 */
sealed interface AuthState {
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val account: Account) : AuthState
}

/** Failures that the interface distinguishes between when reporting to the user. */
sealed class AuthError(message: String) : Exception(message) {
    data object InvalidCredentials : AuthError("Incorrect email address or password.")
    data object EmailAlreadyUsed : AuthError("An account already exists with that email address.")
    data object WeakPassword : AuthError("Choose a password of at least eight characters.")
    data object InvalidEmail : AuthError("That does not look like an email address.")
    data object NetworkUnavailable : AuthError("No connection. Check your network and try again.")
    data object TooManyAttempts : AuthError("Too many attempts. Try again in a few minutes.")
    data object RequiresRecentLogin : AuthError("Please sign in again to confirm this change.")
    data class Unknown(val detail: String) : AuthError(detail)
}

interface AuthRepository {

    val authState: Flow<AuthState>

    /** The current account, or null when signed out. Synchronous read for guards. */
    val currentAccount: Account?

    suspend fun signUp(displayName: String, email: String, password: String): Account

    suspend fun signIn(email: String, password: String): Account

    /**
     * Signs in without an identity, for trying the application out.
     *
     * The resulting session cannot be recovered if the installation is removed,
     * which is why [linkAnonymousAccount] exists and why the interface prompts
     * for an upgrade before a guest accumulates anything worth keeping.
     */
    suspend fun signInAnonymously(): Account

    /**
     * Converts the current anonymous session into a permanent account, keeping
     * the same uid so that household membership and history are preserved.
     */
    suspend fun linkAnonymousAccount(displayName: String, email: String, password: String): Account

    suspend fun sendPasswordReset(email: String)

    suspend fun sendEmailVerification()

    /** Re-reads the account from the server, picking up a completed verification. */
    suspend fun reloadAccount(): Account?

    suspend fun signOut()

    /** Removes the account and the profile record. Requires a recent sign-in. */
    suspend fun deleteAccount()
}

/**
 * Household membership, kept separate from [AuthRepository] because identity and
 * tenancy are different concerns: a signed-in account may belong to no household
 * at all, which is the state the onboarding flow exists to resolve.
 */
interface MembershipRepository {

    /** Households the signed-in account belongs to. Empty until onboarding completes. */
    val memberships: Flow<List<HomeMembership>>

    /** The household currently being viewed, or null when none has been chosen. */
    val activeHomeId: Flow<String?>

    /** Role within the active household, used to gate safety configuration. */
    val activeRole: Flow<lk.ac.ucsc.scs3311.smarthome.domain.model.MemberRole?>

    fun members(homeId: String): Flow<List<HomeMember>>

    suspend fun selectHome(homeId: String)

    /** Creates a household and makes the caller its owner. Returns the home id. */
    suspend fun createHome(name: String): String

    /** Redeems an invite code, joining the household it names. Returns the home id. */
    suspend fun joinHome(code: String): String

    suspend fun createInvite(homeId: String): HomeInvite

    suspend fun removeMember(homeId: String, uid: String)

    suspend fun leaveHome(homeId: String)
}
