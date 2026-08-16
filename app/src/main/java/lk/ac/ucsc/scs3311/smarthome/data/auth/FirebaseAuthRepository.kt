package lk.ac.ucsc.scs3311.smarthome.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.ucsc.scs3311.smarthome.domain.model.Account
import java.io.IOException

/**
 * Firebase Authentication backed implementation.
 *
 * Only two responsibilities live here: proving who the caller is, and keeping
 * the mirrored profile record under `/users/{uid}` in step. Which households
 * that account may see is a separate concern, handled by
 * [FirebaseMembershipRepository] and enforced by the database rules.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
) : AuthRepository {

    override val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            trySend(if (user == null) AuthState.SignedOut else AuthState.SignedIn(user.toAccount()))
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentAccount: Account?
        get() = auth.currentUser?.toAccount()

    override suspend fun signUp(displayName: String, email: String, password: String): Account =
        translating {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = requireNotNull(result.user) { "sign-up returned no user" }
            user.applyDisplayName(displayName)
            writeProfile(user.uid, displayName, email.trim())
            runCatching { user.sendEmailVerification().await() }
            user.toAccount().copy(displayName = displayName)
        }

    override suspend fun signIn(email: String, password: String): Account = translating {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = requireNotNull(result.user) { "sign-in returned no user" }
        // The profile is refreshed on every sign-in so that a display name
        // changed on another device is reflected for other household members.
        writeProfile(user.uid, user.displayName.orEmpty(), user.email.orEmpty())
        user.toAccount()
    }

    override suspend fun signInAnonymously(): Account = translating {
        val existing = auth.currentUser
        if (existing != null) return@translating existing.toAccount()
        val result = auth.signInAnonymously().await()
        requireNotNull(result.user) { "anonymous sign-in returned no user" }.toAccount()
    }

    /**
     * Upgrades the anonymous session in place.
     *
     * `linkWithCredential` keeps the same uid, so household membership, device
     * history and alerts that the guest accumulated all survive. Signing up
     * separately and signing out would discard them.
     */
    override suspend fun linkAnonymousAccount(
        displayName: String,
        email: String,
        password: String,
    ): Account = translating {
        val user = requireNotNull(auth.currentUser) { "no session to upgrade" }
        val credential = EmailAuthProvider.getCredential(email.trim(), password)
        val result = user.linkWithCredential(credential).await()
        val linked = requireNotNull(result.user) { "linking returned no user" }
        linked.applyDisplayName(displayName)
        writeProfile(linked.uid, displayName, email.trim())
        runCatching { linked.sendEmailVerification().await() }
        linked.toAccount().copy(displayName = displayName, isAnonymous = false)
    }

    override suspend fun sendPasswordReset(email: String) {
        translating {
            auth.sendPasswordResetEmail(email.trim()).await()
        }
    }

    override suspend fun sendEmailVerification() = translating {
        auth.currentUser?.sendEmailVerification()?.await()
        Unit
    }

    override suspend fun reloadAccount(): Account? = translating {
        val user = auth.currentUser ?: return@translating null
        user.reload().await()
        auth.currentUser?.toAccount()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteAccount() {
        translating {
            val user = requireNotNull(auth.currentUser) { "not signed in" }
            val uid = user.uid
            // The profile record is removed first: once the account is gone the
            // client no longer satisfies the rule guarding its own node.
            runCatching { database.reference.child(NODE_USERS).child(uid).removeValue().await() }
            user.delete().await()
        }
    }

    // ---- helpers ------------------------------------------------------------

    private suspend fun FirebaseUser.applyDisplayName(displayName: String) {
        if (displayName.isBlank()) return
        runCatching {
            updateProfile(userProfileChangeRequest { this.displayName = displayName.trim() }).await()
        }
    }

    /**
     * Mirrors the account into the database so that other household members can
     * see a name rather than a uid. Only fields the owner is permitted to write
     * are sent; roles live under the household, not here.
     */
    private suspend fun writeProfile(uid: String, displayName: String, email: String) {
        runCatching {
            database.reference.child(NODE_USERS).child(uid).child(NODE_PROFILE)
                .updateChildren(
                    mapOf(
                        "displayName" to displayName.trim(),
                        "email" to email,
                        "lastSeenAt" to ServerValue.TIMESTAMP,
                    ),
                ).await()
        }
    }

    private fun FirebaseUser.toAccount() = Account(
        uid = uid,
        email = email,
        displayName = displayName.orEmpty(),
        isAnonymous = isAnonymous,
        isEmailVerified = isEmailVerified,
    )

    /**
     * Converts Firebase's exception hierarchy into the small set of causes the
     * interface can act on. A raw Firebase message is not something to put in
     * front of a user.
     */
    private inline fun <T> translating(block: () -> T): T = try {
        block()
    } catch (error: FirebaseAuthWeakPasswordException) {
        throw AuthError.WeakPassword
    } catch (error: FirebaseAuthUserCollisionException) {
        throw AuthError.EmailAlreadyUsed
    } catch (error: FirebaseAuthRecentLoginRequiredException) {
        throw AuthError.RequiresRecentLogin
    } catch (error: FirebaseAuthInvalidCredentialsException) {
        // Firebase uses this for both a malformed address and a wrong password.
        if (error.errorCode == "ERROR_INVALID_EMAIL") {
            throw AuthError.InvalidEmail
        } else {
            throw AuthError.InvalidCredentials
        }
    } catch (error: FirebaseAuthInvalidUserException) {
        throw AuthError.InvalidCredentials
    } catch (error: IOException) {
        throw AuthError.NetworkUnavailable
    } catch (error: Exception) {
        val message = error.message.orEmpty()
        when {
            message.contains("blocked all requests", ignoreCase = true) ||
                message.contains("too many", ignoreCase = true) -> throw AuthError.TooManyAttempts
            message.contains("network", ignoreCase = true) -> throw AuthError.NetworkUnavailable
            else -> throw AuthError.Unknown(message.ifBlank { "Sign-in failed." })
        }
    }

    private companion object {
        const val NODE_USERS = "users"
        const val NODE_PROFILE = "profile"
    }
}
