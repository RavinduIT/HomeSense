package lk.ac.ucsc.scs3311.smarthome.ui.account

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.domain.model.Account
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMember
import lk.ac.ucsc.scs3311.smarthome.domain.model.HomeMembership
import lk.ac.ucsc.scs3311.smarthome.domain.model.MemberRole
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors

/**
 * Account and household management.
 *
 * This is where a signed-in session can be ended, a guest session made
 * permanent, households switched, and members invited or removed. Without it
 * the application is enterable but not leaveable, and a guest has no route to
 * an account that survives reinstalling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val account = state.account

    var showSignOut by remember { mutableStateOf(false) }
    var showUpgrade by rememberSaveable { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<HomeMember?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Account") }) },
    ) { padding ->
        if (account == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AccountHeader(account) }

            if (account.isAnonymous) {
                item { GuestUpgradePrompt(onUpgrade = { showUpgrade = true }) }
            }

            if (state.memberships.size > 1) {
                item {
                    SectionCard(title = "Households", icon = Icons.Default.Group) {
                        state.memberships.forEach { membership ->
                            HouseholdRow(
                                membership = membership,
                                selected = membership.homeId == state.activeHomeId,
                                onSelect = { viewModel.selectHome(membership.homeId) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Members", icon = Icons.Default.Group) {
                    if (state.members.isEmpty()) {
                        Text(
                            "No members listed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.members.forEach { member ->
                        MemberRow(
                            member = member,
                            isSelf = member.uid == account.uid,
                            canRemove = state.isOwner && member.uid != account.uid,
                            onRemove = { pendingRemoval = member },
                        )
                    }

                    if (state.isOwner) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.createInvite()
                                showInvite = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Invite someone")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Your permissions", icon = Icons.Default.Shield) {
                    Text(
                        text = if (state.isOwner) {
                            "As the owner you can add and remove devices, configure " +
                                "safety cut-offs, and manage who belongs to this household."
                        } else {
                            "As a member you can operate devices and edit schedules. " +
                                "Adding devices and changing safety cut-offs is reserved " +
                                "to the household owner, because a cut-off protects everyone."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Button(
                    onClick = { showSignOut = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }

            state.message?.let { message ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusColors.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
                    }
                }
            }
        }
    }

    // ---- dialogs ------------------------------------------------------------

    if (showSignOut) {
        // Captured before the dialog's lambdas, since a property read inside a
        // lambda cannot be smart-cast from the nullable state above.
        val guestSession = state.account?.isAnonymous == true

        AlertDialog(
            onDismissRequest = { showSignOut = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (guestSession) {
                        "This is a guest session. It is tied to this installation and " +
                            "cannot be recovered, so signing out will lose the household " +
                            "and everything in it. Create an account first to keep it."
                    } else {
                        "Devices keep running and the safety worker keeps watching them. " +
                            "Only this device stops showing them."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) { Text("Cancel") }
            },
        )
    }

    if (showUpgrade) {
        UpgradeAccountDialog(
            isBusy = state.isBusy,
            error = state.message,
            onDismiss = { showUpgrade = false },
            onConfirm = { name, email, password, confirm ->
                viewModel.upgradeGuest(name, email, password, confirm) { showUpgrade = false }
            },
        )
    }

    if (showInvite) {
        InviteDialog(
            code = state.inviteCode,
            onDismiss = {
                showInvite = false
                viewModel.clearInvite()
            },
        )
    }

    pendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${member.displayName}?") },
            text = { Text("They will lose access to this household immediately.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMember(member.uid)
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            },
        )
    }
}

// ---- pieces -----------------------------------------------------------------

@Composable
private fun AccountHeader(account: Account) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.initials,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(account.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    account.isAnonymous -> "Guest session"
                    account.isEmailVerified -> account.email.orEmpty()
                    else -> "${account.email.orEmpty()} · unverified"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuestUpgradePrompt(onUpgrade: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Keep this household", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "A guest session exists only on this device. Adding an email address " +
                    "and password makes it permanent and keeps everything already set up.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onUpgrade) { Text("Create an account") }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun HouseholdRow(
    membership: HomeMembership,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(membership.homeName, style = MaterialTheme.typography.bodyMedium)
            Text(
                membership.role.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: HomeMember,
    isSelf: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = member.displayName + if (isSelf) " (you)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = buildString {
                    append(member.role.name.lowercase().replaceFirstChar { it.uppercase() })
                    if (member.email.isNotBlank()) append(" · ${member.email}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canRemove) {
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
    HorizontalDivider()
}

@Composable
private fun UpgradeAccountDialog(
    isBusy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, password: String, confirm: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create an account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Everything already set up is kept — this adds a way to sign in " +
                        "again rather than starting over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    singleLine = true,
                    enabled = !isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    enabled = !isBusy,
                    isError = confirm.isNotEmpty() && confirm != password,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = StatusColors.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, email, password, confirm) },
                enabled = !isBusy,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InviteDialog(code: String?, onDismiss: () -> Unit) {
    // The current clipboard API is suspending, because writing to the system
    // clipboard can block. A scope is therefore needed to call it from a click.
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (code == null) {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("Invite code", code)),
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy the code")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Give this to the person joining. It expires in 24 hours and " +
                            "admits them as a member, not an owner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AccountHeaderPreview() {
    HomeSenseTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AccountHeader(
                Account(
                    uid = "u1",
                    email = "demo@homesense.lk",
                    displayName = "Ravindu Weerasinghe",
                    isEmailVerified = true,
                ),
            )
            AccountHeader(Account(uid = "guest", displayName = "Guest", isAnonymous = true))
            GuestUpgradePrompt(onUpgrade = {})
            SectionCard(title = "Members", icon = Icons.Default.Group) {
                MemberRow(
                    HomeMember("u1", "Ravindu Weerasinghe", "demo@homesense.lk", MemberRole.OWNER),
                    isSelf = true,
                    canRemove = false,
                    onRemove = {},
                )
                MemberRow(
                    HomeMember("u2", "W.T. Mahagamage", "wt@example.lk", MemberRole.MEMBER),
                    isSelf = false,
                    canRemove = true,
                    onRemove = {},
                )
            }
        }
    }
}
