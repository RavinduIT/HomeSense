package lk.ac.ucsc.scs3311.smarthome.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors

/**
 * Sign-in and registration.
 *
 * One screen with two modes rather than two destinations: the fields overlap
 * almost entirely, and switching between them should not lose what has already
 * been typed.
 */
@Composable
fun AuthenticationScreen(
    state: AuthFormState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String, email: String, password: String, confirm: String) -> Unit,
    onGuest: () -> Unit,
    onResetPassword: (email: String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var revealPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Default.Home,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text("HomeSense", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = if (registering) {
                "Create an account to set up your household."
            } else {
                "Sign in to your household."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        AnimatedVisibility(visible = registering) {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            singleLine = true,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !state.isBusy,
            visualTransformation = if (revealPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { revealPassword = !revealPassword }) {
                    Icon(
                        imageVector = if (revealPassword) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (revealPassword) {
                            "Hide the password"
                        } else {
                            "Show the password"
                        },
                    )
                }
            },
            supportingText = if (registering) {
                { Text("At least ${AuthViewModel.MIN_PASSWORD} characters.") }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (registering) ImeAction.Next else ImeAction.Done,
            ),
        )

        AnimatedVisibility(visible = registering) {
            Column {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    isError = confirm.isNotEmpty() && confirm != password,
                    visualTransformation = if (revealPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        }

        if (!registering) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { onResetPassword(email) },
                    enabled = !state.isBusy,
                ) { Text("Forgot password?") }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        FormMessage(state = state, onDismiss = onDismissMessage)

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onDismissMessage()
                if (registering) onSignUp(name, email, password, confirm)
                else onSignIn(email, password)
            },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(if (registering) "Create account" else "Sign in")
            }
        }

        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = {
                registering = !registering
                onDismissMessage()
            },
            enabled = !state.isBusy,
        ) {
            Text(
                if (registering) {
                    "I already have an account"
                } else {
                    "Create an account"
                },
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "  or  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onGuest,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Continue as a guest") }

        Text(
            text = "A guest session is tied to this device and cannot be recovered. " +
                "It can be turned into a full account later without losing anything.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Household onboarding.
 *
 * Reached when an account is signed in but belongs to no household. Both routes
 * are offered with equal weight, because whether a user creates or joins depends
 * entirely on whether someone in the house got there first.
 */
@Composable
fun OnboardingScreen(
    displayName: String,
    state: AuthFormState,
    onCreateHome: (String) -> Unit,
    onJoinHome: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var homeName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Text(
            text = if (displayName.isBlank()) "Welcome" else "Welcome, $displayName",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Set up a household, or join one that already exists.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        Text("Create a household", style = MaterialTheme.typography.titleMedium)
        Text(
            "You become its owner, and can invite others.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = homeName,
            onValueChange = { homeName = it },
            label = { Text("Household name") },
            placeholder = { Text("Home") },
            singleLine = true,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onCreateHome(homeName) },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Create") }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "  or  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))

        Text("Join a household", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ask the owner for an invite code. Codes expire after a day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
            label = { Text("Invite code") },
            placeholder = { Text("ABCD2345") },
            singleLine = true,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onJoinHome(code) },
            enabled = !state.isBusy && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Join") }

        FormMessage(state = state, onDismiss = onDismissMessage)

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Sign out")
        }
    }
}

/** A single place for the error and confirmation lines, so both look the same. */
@Composable
private fun FormMessage(state: AuthFormState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice ?: return
    val isError = state.error != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) StatusColors.error else StatusColors.on,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

/** Shown while the persisted session is restored, so the sign-in form does not flash. */
@Composable
fun SessionLoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AuthenticationScreenPreview() {
    HomeSenseTheme {
        AuthenticationScreen(
            state = AuthFormState(),
            onSignIn = { _, _ -> },
            onSignUp = { _, _, _, _ -> },
            onGuest = {},
            onResetPassword = {},
            onDismissMessage = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun OnboardingScreenPreview() {
    HomeSenseTheme {
        OnboardingScreen(
            displayName = "Ravindu",
            state = AuthFormState(error = "That code is not valid."),
            onCreateHome = {},
            onJoinHome = {},
            onDismissMessage = {},
            onSignOut = {},
        )
    }
}
