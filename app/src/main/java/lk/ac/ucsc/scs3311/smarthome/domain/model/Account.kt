package lk.ac.ucsc.scs3311.smarthome.domain.model

/**
 * A signed-in account.
 *
 * [isAnonymous] matters beyond bookkeeping: an anonymous session is tied to one
 * app installation and cannot be recovered, so the account is offered an upgrade
 * to a permanent credential rather than being allowed to accumulate a household
 * it will silently lose.
 */
data class Account(
    val uid: String = "",
    val email: String? = null,
    val displayName: String = "",
    val isAnonymous: Boolean = false,
    val isEmailVerified: Boolean = false,
) {
    val label: String get() = displayName.ifBlank { email ?: "Guest" }

    /** Initials for the avatar, derived without assuming a name has two parts. */
    val initials: String
        get() = label.trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
}

/**
 * What a member may do within a household.
 *
 * The distinction that matters is safety configuration. Any member may switch a
 * light on, but altering `max_on_duration` or disabling a cut-off changes the
 * protection applied to everyone in the house, so it is reserved to the owner.
 * The database rules enforce this; the interface merely reflects it.
 */
enum class MemberRole {
    OWNER,
    MEMBER,
    ;

    val canManageMembers: Boolean get() = this == OWNER
    val canConfigureSafety: Boolean get() = this == OWNER
    val canDeleteHome: Boolean get() = this == OWNER

    /** Every member may operate devices and edit ordinary schedules. */
    val canOperateDevices: Boolean get() = true
}

/** A household the signed-in account belongs to. */
data class HomeMembership(
    val homeId: String = "",
    val homeName: String = "",
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = 0L,
)

/** Another person in the household, as shown on the members screen. */
data class HomeMember(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = 0L,
)

/**
 * A short code that admits its bearer to a household.
 *
 * Codes are single-purpose and time limited. They are stored under a top-level
 * node keyed by the code itself, so redemption is a read of one known key; the
 * rules permit reading a code but not listing them, which is what stops the
 * collection being enumerated.
 */
data class HomeInvite(
    val code: String = "",
    val homeId: String = "",
    val homeName: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
) {
    fun isValidAt(nowMillis: Long): Boolean = expiresAt == 0L || nowMillis < expiresAt

    companion object {
        /** Codes live for 24 hours; a household invite has no reason to be permanent. */
        const val VALIDITY_MS = 24 * 60 * 60 * 1000L

        /**
         * Excludes characters that are misread aloud or in handwriting, since
         * these codes are typically dictated across a room.
         */
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val LENGTH = 8
    }
}
