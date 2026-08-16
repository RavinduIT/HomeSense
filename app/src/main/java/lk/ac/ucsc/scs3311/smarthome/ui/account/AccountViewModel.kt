package lk.ac.ucsc.scs3311.smarthome.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.auth.AuthRepository
import lk.ac.ucsc.scs3311.smarthome.data.auth.AuthState
import lk.ac.ucsc.scs3311.smarthome.data.auth.MembershipRepository
import lk.ac.ucsc.scs3311.smarthome.domain.model.Account
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMember
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMembership
import lk.ac.ucsc.scs3311.smarthome.domain.model.MemberRole

data class AccountUiState(
    val account: Account? = null,
    val memberships: List<HomeMembership> = emptyList(),
    val activeHomeId: String? = null,
    val members: List<HomeMember> = emptyList(),
    val role: MemberRole? = null,
    val inviteCode: String? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
) {
    val isOwner: Boolean get() = role == MemberRole.OWNER
}

/**
 * Account and household management.
 *
 * Separate from [lk.ac.ucsc.scs3311.smarthome.ui.auth.AuthViewModel], which
 * decides whether there is a session at all. This one operates within an
 * established session: ending it, making a guest session permanent, switching
 * household, and managing members.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val membershipRepository: MembershipRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(
        AccountUiState(isBusy = false, message = null, inviteCode = null),
    )

    private val members = membershipRepository.activeHomeId.flatMapLatest { homeId ->
        if (homeId == null) flowOf(emptyList()) else membershipRepository.members(homeId)
    }

    val uiState: StateFlow<AccountUiState> = combine(
        authRepository.authState.map { (it as? AuthState.SignedIn)?.account },
        membershipRepository.memberships,
        membershipRepository.activeHomeId,
        combine(members, membershipRepository.activeRole) { list, role -> list to role },
        transient,
    ) { account, memberships, activeHomeId, membersAndRole, local ->
        AccountUiState(
            account = account,
            memberships = memberships,
            activeHomeId = activeHomeId,
            members = membersAndRole.first,
            role = membersAndRole.second,
            inviteCode = local.inviteCode,
            isBusy = local.isBusy,
            message = local.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AccountUiState(),
    )

    /**
     * Ends the session.
     *
     * No navigation is required. The session gate above the navigation graph
     * observes authentication state, so the whole graph is discarded along with
     * the ViewModels holding this account's data.
     */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /**
     * Converts a guest session into a permanent account, keeping the same
     * account identifier so that the household and its history survive.
     */
    fun upgradeGuest(
        displayName: String,
        email: String,
        password: String,
        confirm: String,
        onSuccess: () -> Unit,
    ) = runGuarded(onSuccess) {
        require(displayName.isNotBlank()) { "Enter your name." }
        require(password.length >= MIN_PASSWORD) {
            "Choose a password of at least $MIN_PASSWORD characters."
        }
        require(password == confirm) { "The passwords do not match." }
        authRepository.linkAnonymousAccount(displayName, email, password)
    }

    fun selectHome(homeId: String) {
        viewModelScope.launch { membershipRepository.selectHome(homeId) }
    }

    fun createInvite() {
        transient.update { it.copy(inviteCode = null) }
        runGuarded {
            val homeId = membershipRepository.activeHomeId.first()
                ?: error("No household is selected.")
            val invite = membershipRepository.createInvite(homeId)
            transient.update { it.copy(inviteCode = invite.code) }
        }
    }

    fun clearInvite() {
        transient.update { it.copy(inviteCode = null) }
    }

    fun removeMember(uid: String) = runGuarded {
        val homeId = membershipRepository.activeHomeId.first()
            ?: error("No household is selected.")
        membershipRepository.removeMember(homeId, uid)
    }

    fun dismissMessage() {
        transient.update { it.copy(message = null) }
    }

    private fun runGuarded(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (transient.value.isBusy) return
        viewModelScope.launch {
            transient.update { it.copy(isBusy = true, message = null) }
            val outcome = runCatching { block() }
            transient.update { current ->
                current.copy(
                    isBusy = false,
                    message = outcome.exceptionOrNull()?.let { error ->
                        error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
                    },
                )
            }
            if (outcome.isSuccess) onSuccess()
        }
    }

    companion object {
        const val MIN_PASSWORD = 8
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HomeSenseApp
                AccountViewModel(app.container.authRepository, app.container.membershipRepository)
            }
        }
    }
}
