package lk.ac.ucsc.scs3311.smarthome.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.auth.AuthRepository
import lk.ac.ucsc.scs3311.smarthome.data.auth.AuthState
import lk.ac.ucsc.scs3311.smarthome.data.auth.MembershipRepository
import lk.ac.ucsc.scs3311.smarthome.domain.model.Account
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMembership

/**
 * Where the user should be sent.
 *
 * Derived from authentication and membership together, because being signed in
 * is not sufficient to show the dashboard: an account that belongs to no
 * household has nothing to display and must complete onboarding first.
 */
sealed interface SessionRoute {
    /** The persisted session is still being restored. Show nothing yet. */
    data object Loading : SessionRoute

    data object Authentication : SessionRoute

    /** Signed in, but belongs to no household. */
    data class Onboarding(val account: Account) : SessionRoute

    data class Ready(val account: Account, val homes: List<HomeMembership>) : SessionRoute
}

data class AuthFormState(
    val isBusy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Sign-in, registration and onboarding.
 *
 * Held above the navigation graph rather than per screen, so that the
 * destination follows session state rather than the interface having to
 * remember to navigate after every operation.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val membershipRepository: MembershipRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form

    val route: StateFlow<SessionRoute> =
        combine(
            authRepository.authState,
            membershipRepository.memberships,
        ) { auth, memberships ->
            when (auth) {
                AuthState.Unknown -> SessionRoute.Loading
                AuthState.SignedOut -> SessionRoute.Authentication
                is AuthState.SignedIn ->
                    if (memberships.isEmpty()) SessionRoute.Onboarding(auth.account)
                    else SessionRoute.Ready(auth.account, memberships)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SessionRoute.Loading,
        )

    // ---- authentication -----------------------------------------------------

    fun signIn(email: String, password: String) = runGuarded {
        require(email.isNotBlank() && password.isNotBlank()) {
            "Enter your email address and password."
        }
        authRepository.signIn(email, password)
    }

    fun signUp(displayName: String, email: String, password: String, confirm: String) = runGuarded {
        require(displayName.isNotBlank()) { "Enter your name." }
        require(password.length >= MIN_PASSWORD) {
            "Choose a password of at least $MIN_PASSWORD characters."
        }
        require(password == confirm) { "The passwords do not match." }
        authRepository.signUp(displayName, email, password)
    }

    fun continueAsGuest() = runGuarded { authRepository.signInAnonymously() }

    fun upgradeGuest(displayName: String, email: String, password: String, confirm: String) =
        runGuarded {
            require(displayName.isNotBlank()) { "Enter your name." }
            require(password.length >= MIN_PASSWORD) {
                "Choose a password of at least $MIN_PASSWORD characters."
            }
            require(password == confirm) { "The passwords do not match." }
            authRepository.linkAnonymousAccount(displayName, email, password)
            _form.update { it.copy(notice = "Your account is now permanent.") }
        }

    fun sendPasswordReset(email: String) = runGuarded {
        require(email.isNotBlank()) { "Enter your email address first." }
        authRepository.sendPasswordReset(email)
        // Deliberately does not confirm whether the address is registered:
        // doing so would disclose who holds an account.
        _form.update {
            it.copy(notice = "If that address has an account, a reset link is on its way.")
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _form.value = AuthFormState()
        }
    }

    // ---- onboarding ---------------------------------------------------------

    fun createHome(name: String) = runGuarded {
        require(name.isNotBlank()) { "Give the household a name." }
        membershipRepository.createHome(name)
    }

    fun joinHome(code: String) = runGuarded {
        require(code.isNotBlank()) { "Enter the invite code." }
        membershipRepository.joinHome(code)
    }

    fun selectHome(homeId: String) {
        viewModelScope.launch { membershipRepository.selectHome(homeId) }
    }

    fun dismissMessage() {
        _form.update { it.copy(error = null, notice = null) }
    }

    /**
     * Runs an operation with the busy flag set and any failure reported as a
     * message the user can act on. Validation uses [require], so a precondition
     * failure and a backend failure surface through one path.
     */
    private fun runGuarded(block: suspend () -> Unit) {
        if (_form.value.isBusy) return
        viewModelScope.launch {
            _form.update { it.copy(isBusy = true, error = null, notice = null) }
            val outcome = runCatching { block() }
            _form.update { current ->
                current.copy(
                    isBusy = false,
                    error = outcome.exceptionOrNull()?.let { error ->
                        error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
                    },
                )
            }
        }
    }

    companion object {
        const val MIN_PASSWORD = 8
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HomeSenseApp
                AuthViewModel(app.container.authRepository, app.container.membershipRepository)
            }
        }
    }
}
