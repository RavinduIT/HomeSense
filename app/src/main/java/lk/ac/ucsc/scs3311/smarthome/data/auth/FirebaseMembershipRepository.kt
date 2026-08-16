package lk.ac.ucsc.scs3311.smarthome.data.auth

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.ExperimentalCoroutinesApi
import lk.ac.ucsc.scs3311.smarthome.data.session.SessionPreferences
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeInvite
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMember
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMembership
import lk.ac.ucsc.scs3311.smarthome.domain.model.MemberRole
import java.security.SecureRandom

/** Raised when a membership operation fails, with a reason worth showing. */
sealed class MembershipError(message: String) : Exception(message) {
    data object InviteNotFound : MembershipError("That code is not valid.")
    data object InviteExpired : MembershipError("That code has expired. Ask for a new one.")
    data object AlreadyMember : MembershipError("You are already a member of this household.")
    data object NotSignedIn : MembershipError("Sign in first.")
    data object NotPermitted : MembershipError("Only the household owner can do that.")
    data class Failed(val detail: String) : MembershipError(detail)
}

/**
 * Household membership against Realtime Database.
 *
 * The data is deliberately held in two places. `/users/{uid}/homes` lets a
 * client discover its own households without reading anything it does not own,
 * and `/homes/{homeId}/members` is what the security rules consult to decide
 * whether a read or write is allowed. Both are written together whenever
 * membership changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseMembershipRepository(
    private val database: FirebaseDatabase,
    private val authRepository: AuthRepository,
    private val preferences: SessionPreferences,
) : MembershipRepository {

    private val root get() = database.reference

    private val uidFlow: Flow<String?> = authRepository.authState
        .map { state -> (state as? AuthState.SignedIn)?.account?.uid }
        .distinctUntilChanged()

    override val memberships: Flow<List<HomeMembership>> =
        uidFlow.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else root.child(NODE_USERS).child(uid).child(NODE_HOMES).valueFlow { snapshot ->
                snapshot.children.mapNotNull { child ->
                    val homeId = child.key ?: return@mapNotNull null
                    val raw = child.value as? Map<*, *>
                    HomeMembership(
                        homeId = homeId,
                        homeName = raw?.get("homeName") as? String ?: homeId,
                        role = roleOf(raw?.get("role") as? String),
                        joinedAt = (raw?.get("joinedAt") as? Number)?.toLong() ?: 0L,
                    )
                }.sortedBy { it.joinedAt }
            }
        }

    /**
     * The household in view.
     *
     * A stored preference is only honoured while it names a household the
     * account still belongs to; otherwise the first membership is used. This is
     * what stops a removed member being left pointed at a home they can no
     * longer read, which would otherwise surface as a permission error rather
     * than as an empty state.
     */
    override val activeHomeId: Flow<String?> =
        combine(memberships, preferences.activeHomeId) { available, stored ->
            when {
                available.isEmpty() -> null
                stored != null && available.any { it.homeId == stored } -> stored
                else -> available.first().homeId
            }
        }.distinctUntilChanged()

    override val activeRole: Flow<MemberRole?> =
        combine(memberships, activeHomeId) { available, active ->
            available.firstOrNull { it.homeId == active }?.role
        }.distinctUntilChanged()

    override fun members(homeId: String): Flow<List<HomeMember>> =
        root.child(NODE_HOMES).child(homeId).child(NODE_MEMBERS).valueFlow { snapshot ->
            snapshot.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                val raw = child.value as? Map<*, *> ?: return@mapNotNull null
                HomeMember(
                    uid = uid,
                    displayName = raw["displayName"] as? String ?: "Member",
                    email = raw["email"] as? String ?: "",
                    role = roleOf(raw["role"] as? String),
                    joinedAt = (raw["joinedAt"] as? Number)?.toLong() ?: 0L,
                )
            }.sortedWith(compareBy({ it.role != MemberRole.OWNER }, { it.joinedAt }))
        }

    override suspend fun selectHome(homeId: String) = preferences.setActiveHomeId(homeId)

    override suspend fun createHome(name: String): String {
        val account = authRepository.currentAccount ?: throw MembershipError.NotSignedIn
        val homeId = root.child(NODE_HOMES).push().key
            ?: throw MembershipError.Failed("Could not allocate a household identifier.")

        val homeName = name.trim().ifBlank { "My home" }

        // One multi-path update, so a half-created household cannot exist: the
        // home, its first member, the owner's index entry and the worker's
        // discovery entry all land together or not at all.
        root.updateChildren(
            mapOf(
                "$NODE_HOMES/$homeId/meta/name" to homeName,
                "$NODE_HOMES/$homeId/meta/ownerUid" to account.uid,
                "$NODE_HOMES/$homeId/meta/createdAt" to ServerValue.TIMESTAMP,
                "$NODE_HOMES/$homeId/$NODE_MEMBERS/${account.uid}" to memberRecord(
                    account.displayName.ifBlank { account.label },
                    account.email.orEmpty(),
                    MemberRole.OWNER,
                ),
                "$NODE_USERS/${account.uid}/$NODE_HOMES/$homeId" to mapOf(
                    "homeName" to homeName,
                    "role" to MemberRole.OWNER.name,
                    "joinedAt" to ServerValue.TIMESTAMP,
                ),
                "$NODE_HOME_INDEX/$homeId" to true,
            ),
        ).await()

        preferences.setActiveHomeId(homeId)
        return homeId
    }

    override suspend fun joinHome(code: String): String {
        val account = authRepository.currentAccount ?: throw MembershipError.NotSignedIn
        val normalised = code.trim().uppercase()

        val snapshot = root.child(NODE_INVITES).child(normalised).get().await()
        if (!snapshot.exists()) throw MembershipError.InviteNotFound

        val raw = snapshot.value as? Map<*, *> ?: throw MembershipError.InviteNotFound
        val homeId = raw["homeId"] as? String ?: throw MembershipError.InviteNotFound
        val homeName = raw["homeName"] as? String ?: homeId
        val expiresAt = (raw["expiresAt"] as? Number)?.toLong() ?: 0L
        if (expiresAt != 0L && System.currentTimeMillis() > expiresAt) {
            throw MembershipError.InviteExpired
        }

        val already = root.child(NODE_HOMES).child(homeId)
            .child(NODE_MEMBERS).child(account.uid).get().await()
        if (already.exists()) throw MembershipError.AlreadyMember

        // The code is carried into the member record because the security rule
        // verifies it against /invites. Without it the write is rejected: the
        // server checks the invitation rather than trusting the client's claim
        // to hold one.
        root.updateChildren(
            mapOf(
                "$NODE_HOMES/$homeId/$NODE_MEMBERS/${account.uid}" to memberRecord(
                    account.displayName.ifBlank { account.label },
                    account.email.orEmpty(),
                    MemberRole.MEMBER,
                    viaCode = normalised,
                ),
                "$NODE_USERS/${account.uid}/$NODE_HOMES/$homeId" to mapOf(
                    "homeName" to homeName,
                    "role" to MemberRole.MEMBER.name,
                    "joinedAt" to ServerValue.TIMESTAMP,
                ),
            ),
        ).await()

        preferences.setActiveHomeId(homeId)
        return homeId
    }

    override suspend fun createInvite(homeId: String): HomeInvite {
        val account = authRepository.currentAccount ?: throw MembershipError.NotSignedIn
        val homeName = root.child(NODE_HOMES).child(homeId).child("meta/name")
            .get().await().value as? String ?: homeId

        val code = generateCode()
        val now = System.currentTimeMillis()
        val invite = HomeInvite(
            code = code,
            homeId = homeId,
            homeName = homeName,
            createdBy = account.uid,
            createdAt = now,
            expiresAt = now + HomeInvite.VALIDITY_MS,
        )

        root.child(NODE_INVITES).child(code).setValue(
            mapOf(
                "homeId" to invite.homeId,
                "homeName" to invite.homeName,
                "createdBy" to invite.createdBy,
                "createdAt" to ServerValue.TIMESTAMP,
                "expiresAt" to invite.expiresAt,
            ),
        ).await()

        return invite
    }

    override suspend fun removeMember(homeId: String, uid: String) {
        root.updateChildren(
            mapOf(
                "$NODE_HOMES/$homeId/$NODE_MEMBERS/$uid" to null,
                "$NODE_USERS/$uid/$NODE_HOMES/$homeId" to null,
            ),
        ).await()
    }

    override suspend fun leaveHome(homeId: String) {
        val account = authRepository.currentAccount ?: throw MembershipError.NotSignedIn
        removeMember(homeId, account.uid)
        preferences.setActiveHomeId(null)
    }

    // ---- helpers ------------------------------------------------------------

    private fun memberRecord(
        displayName: String,
        email: String,
        role: MemberRole,
        viaCode: String? = null,
    ) = buildMap<String, Any?> {
        put("displayName", displayName)
        put("email", email)
        put("role", role.name)
        put("joinedAt", ServerValue.TIMESTAMP)
        viaCode?.let { put("viaCode", it) }
    }

    private fun roleOf(value: String?): MemberRole =
        runCatching { MemberRole.valueOf(value.orEmpty()) }.getOrDefault(MemberRole.MEMBER)

    /**
     * Codes are drawn from a cryptographic source rather than [kotlin.random.Random].
     * An invite code is a bearer credential for a household, so a predictable
     * sequence would let one be guessed.
     */
    private fun generateCode(): String {
        val random = SecureRandom()
        return (1..HomeInvite.LENGTH)
            .map { HomeInvite.ALPHABET[random.nextInt(HomeInvite.ALPHABET.length)] }
            .joinToString("")
    }

    private fun <T> com.google.firebase.database.DatabaseReference.valueFlow(
        transform: (DataSnapshot) -> T,
    ): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                runCatching { transform(snapshot) }.onSuccess { trySend(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }

    private companion object {
        const val NODE_USERS = "users"
        const val NODE_HOMES = "homes"
        const val NODE_MEMBERS = "members"
        const val NODE_INVITES = "invites"
        const val NODE_HOME_INDEX = "homeIndex"
    }
}
