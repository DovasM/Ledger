package com.ledger.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.BackupViewModel
import uniffi.ledger.BackupInfo

/**
 * Export and restore. The file goes wherever the user chooses through the system picker, which is
 * the only place it survives the app being uninstalled — which is the case a backup exists for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    navController: NavController,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val autoEnabled by viewModel.autoEnabled.collectAsStateWithLifecycle(initialValue = false)
    val autoFolder by viewModel.autoFolder.collectAsStateWithLifecycle(initialValue = "")
    val autoKeep by viewModel.autoKeep.collectAsStateWithLifecycle(initialValue = "7")
    val autoLastAt by viewModel.autoLastAt.collectAsStateWithLifecycle(initialValue = "")
    val autoLastError by viewModel.autoLastError.collectAsStateWithLifecycle(initialValue = "")

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.setAutoFolder(context, it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.export(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.stageRestore(it) } }

    // Replacing everything is not a question to answer blind, so the file is read first and its
    // contents shown before anything is overwritten.
    state.pending?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            icon = { Icon(Icons.Filled.Warning, null, tint = Tertiary) },
            title = { Text("Replace everything with this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Everything currently in the app will be replaced. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium, color = OnSurface
                    )
                    BackupContents(info)
                    if (info.schemaVersion < CURRENT_BACKUP_FORMAT) {
                        Text(
                            "This backup was made by an older version of the app. It will be brought up to date as it is restored.",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }) { Text("Replace", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) { Text("Cancel", color = OnSurfaceVariant) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save a backup", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Writes everything — transactions, wallets, budgets, goals, debts and their history — to a single file you choose where to keep.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                    )
                    Button(
                        onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save backup", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            state.lastExport?.let { info ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text("Backup saved", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        }
                        BackupContents(info)
                    }
                }
            }

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Back up automatically", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text(
                                "Once a day, into a folder you choose. A backup you have to remember to take is one you will not have.",
                                style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoEnabled,
                            onCheckedChange = { viewModel.setAutoEnabled(context, it) }
                        )
                    }

                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Folder", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                if (autoFolder.isBlank()) "Not chosen yet" else prettyFolder(autoFolder),
                                style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                            )
                        }
                        TextButton(onClick = { folderLauncher.launch(null) }) {
                            Text(if (autoFolder.isBlank()) "Choose" else "Change", color = Primary)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep the most recent", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                "Older ones this app wrote are removed. Nothing else in the folder is touched.",
                                style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("3", "7", "30").forEach { option ->
                                FilterChip(
                                    selected = autoKeep == option,
                                    onClick = { viewModel.setAutoKeep(option) },
                                    label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary, selectedLabelColor = OnPrimary
                                    )
                                )
                            }
                        }
                    }

                    if (autoLastAt.isNotBlank() || autoLastError.isNotBlank()) {
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (autoLastError.isBlank()) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                null,
                                tint = if (autoLastError.isBlank()) Primary else Tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                if (autoLastError.isBlank()) "Last backup ${autoLastAt.take(16).replace('T', ' ')}"
                                else autoLastError,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoLastError.isBlank()) OnSurfaceVariant else Tertiary
                            )
                        }
                    }
                }
            }

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore from a backup", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Replaces everything in the app with the contents of a backup file. A backup from an older version is brought up to date as it is restored.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.Restore, null, modifier = Modifier.size(18.dp), tint = OnSurface)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose a backup file", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                    }
                }
            }

            state.restored?.let { info ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text("Restored", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        }
                        BackupContents(info)
                    }
                }
            }

            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Working…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }

            state.error?.let { message ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK", color = Primary) }
                    }
                }
            }
        }
    }
}

/** A tree URI is unreadable as-is; show the part a person can recognise. */
private fun prettyFolder(uri: String): String =
    java.net.URLDecoder.decode(uri, "UTF-8").substringAfterLast(':').ifBlank { uri }

/**
 * Asked of the Rust side rather than written down here. The number changes with every migration, and
 * a copy of it in the UI is a copy that goes stale silently.
 */
private val CURRENT_BACKUP_FORMAT: Long get() = uniffi.ledger.currentSchemaVersion()

@Composable
private fun BackupContents(info: BackupInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            "Transactions" to info.transactions,
            "Wallets" to info.wallets,
            "Categories" to info.categories,
            "Budgets" to info.budgets,
            "Goals" to info.goals,
            "Debts" to info.debts,
            "Transfers" to info.transfers,
            "Recurring" to info.recurring
        ).filter { it.second > 0 }.forEach { (label, count) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                Text("$count", style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            "Backup format ${info.schemaVersion}",
            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
        )
    }
}
