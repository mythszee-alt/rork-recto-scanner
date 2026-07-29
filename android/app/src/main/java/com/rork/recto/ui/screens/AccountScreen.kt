package com.rork.recto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(state: AppUiState, appViewModel: AppViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showDeletion by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    state.session == null -> "RECTO / ON THIS DEVICE"
                    state.hasProAccess -> "RECTO PRO"
                    else -> "RECTO FREE"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
            )
            Text(
                when {
                    state.session == null -> "No account"
                    state.hasProAccess -> "Subscription active"
                    else -> "Free access"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    state.session == null -> "Your scans are saved on this device only. Sign in to turn on encrypted cloud backup and restore."
                    state.hasProAccess -> "Manage billing and cancellation in Google Play."
                    else -> "Upgrade for encrypted backup, verified redaction and every export format."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.hasProAccess) Button(onClick = appViewModel::showPaywall, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("VIEW RECTO PRO") }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            ListItem(headlineContent = { Text("Restore purchases") }, leadingContent = { Icon(Icons.Outlined.Restore, null) }, modifier = Modifier.fillMaxWidth().clickable(onClick = appViewModel::restorePurchases))
            ListItem(headlineContent = { Text("Export my account data") }, supportingContent = { Text("Creates a portable archive of account metadata and local documents") }, leadingContent = { Icon(Icons.Outlined.Download, null) })
            ListItem(headlineContent = { Text("Privacy and retention") }, supportingContent = { Text("What Recto stores and when it is erased") }, leadingContent = { Icon(Icons.Outlined.Security, null) }, modifier = Modifier.fillMaxWidth().clickable { showPrivacy = true })
            if (state.session == null) {
                Button(onClick = appViewModel::exitGuestMode, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Text("SIGN IN TO ENABLE CLOUD BACKUP", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = appViewModel::signOut, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Logout, null); Text("  SIGN OUT") }
                    Button(onClick = { showDeletion = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.DeleteForever, null); Text("  DELETE") }
                }
            }
            TextButton(onClick = { showPrivacy = true }, Modifier.fillMaxWidth()) { Text("PRIVACY • TERMS • DELETION POLICY") }
        }
    }
    if (showDeletion) AlertDialog(
        onDismissRequest = { showDeletion = false },
        title = { Text("Delete your Recto account?") },
        text = { Text("Access will be disabled immediately. Cloud documents and account data are permanently erased after a 30-day recovery period. Local scans remain on this device until you remove the app or delete them.") },
        confirmButton = { TextButton(onClick = { showDeletion = false; appViewModel.requestAccountDeletion() }) { Text("SCHEDULE DELETION", color = Color.Red, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { showDeletion = false }) { Text("KEEP ACCOUNT") } }
    )
    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false },
        title = { Text("Privacy at a glance") },
        text = { Text("Recto requests camera access only while scanning. Documents are stored locally first. If backup is enabled, encrypted document bytes and minimum sync metadata are stored in your private account. Recto contains no ads and does not request broad photo-library access. Subscription status is processed by Google Play and RevenueCat. Account deletion removes cloud data after 30 days.") },
        confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("DONE") } }
    )
}
