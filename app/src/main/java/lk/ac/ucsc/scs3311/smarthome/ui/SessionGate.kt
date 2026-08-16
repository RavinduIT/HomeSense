package lk.ac.ucsc.scs3311.smarthome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.ucsc.scs3311.smarthome.ui.auth.AuthViewModel
import lk.ac.ucsc.scs3311.smarthome.ui.auth.AuthenticationScreen
import lk.ac.ucsc.scs3311.smarthome.ui.auth.OnboardingScreen
import lk.ac.ucsc.scs3311.smarthome.ui.auth.SessionLoadingScreen
import lk.ac.ucsc.scs3311.smarthome.ui.auth.SessionRoute

/**
 * Chooses between the authentication flow, household onboarding and the
 * application proper.
 *
 * Placing the decision above the navigation graph rather than inside it means
 * the dashboard is never composed for a session that is not entitled to it.
 * Signing out does not need to navigate anywhere: the session state changes,
 * this recomposes, and the whole graph below is discarded along with the
 * ViewModels holding the previous account's data.
 */
@Composable
fun SessionGate(
    onAuthenticated: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()

    when (val current = route) {
        SessionRoute.Loading -> SessionLoadingScreen(modifier)

        SessionRoute.Authentication -> AuthenticationScreen(
            state = form,
            onSignIn = viewModel::signIn,
            onSignUp = viewModel::signUp,
            onGuest = viewModel::continueAsGuest,
            onResetPassword = viewModel::sendPasswordReset,
            onDismissMessage = viewModel::dismissMessage,
            modifier = modifier,
        )

        is SessionRoute.Onboarding -> OnboardingScreen(
            displayName = current.account.displayName,
            state = form,
            onCreateHome = viewModel::createHome,
            onJoinHome = viewModel::joinHome,
            onDismissMessage = viewModel::dismissMessage,
            onSignOut = viewModel::signOut,
            modifier = modifier,
        )

        is SessionRoute.Ready -> onAuthenticated()
    }
}
