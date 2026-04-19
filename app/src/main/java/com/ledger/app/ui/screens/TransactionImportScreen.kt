package com.ledger.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.ImportViewModel
import com.ledger.app.ui.viewmodel.MmAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionImportScreen(
    navController: NavController,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.parseMmBackup(uri)
    }

    // Derive step from state
    val step = when {
        state.isDone -> 3
        state.parseResult != null -> 2
        state.isLoading -> 1
        else -> 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Money Manager", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isDone) viewModel.reset()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Step indicator
            StepIndicator(step = step)

            // Error banner
            if (state.error != null) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Import failed", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                        TextButton(onClick = { viewModel.reset() }) { Text("Retry", color = Primary) }
                    }
                }
            }

            when (step) {
                0 -> SelectFileStep(onPickFile = { filePicker.launch("*/*") })
                1 -> LoadingStep()
                2 -> {
                    val result = state.parseResult!!
                    PreviewStep(
                        accounts = result.accounts,
                        categoryCount = result.categories.size,
                        transactionCount = result.transactions.size,
                        selectedAccountUids = state.selectedAccountUids,
                        replaceExisting = state.replaceExisting,
                        onToggleAccount = { viewModel.toggleAccount(it) },
                        onReplaceExistingChange = { viewModel.setReplaceExisting(it) },
                        onConfirm = { viewModel.confirmImport() },
                        isImporting = state.isLoading
                    )
                }
                3 -> DoneStep(
                    count = state.importedCount,
                    onDone = {
                        viewModel.reset()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val steps = listOf("Select file", "Parsing", "Review", "Done")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { i, label ->
            val isActive = step >= i
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Primary else SurfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (step > i) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (isActive) OnPrimary else OnSurfaceVariant)
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) Primary else OnSurfaceVariant)
            }
            if (i < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(1.dp)
                        .background(if (step > i) Primary else OutlineVariant)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectFileStep(onPickFile: () -> Unit) {
    // How-it-works card
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(18.dp))
                Text("How to export from Money Manager", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            }
            listOf(
                "Open Money Manager on your phone",
                "Go to Settings → Backup & Restore",
                "Tap \"Create Backup\" and save the .mmbackup file",
                "Transfer the file here and tap Select below"
            ).forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                    }
                    Text(step, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }

    // What gets imported card
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What will be imported", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            listOf(
                Icons.Filled.AccountBalance to "Accounts → Wallets",
                Icons.Filled.Category to "Categories",
                Icons.Filled.Receipt to "All transactions with dates and notes"
            ).forEach { (icon, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
            }
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, null, tint = Color(0xFFF9A825), modifier = Modifier.size(14.dp))
                Text("Existing wallets with matching names won't be duplicated.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
    }

    Button(
        onClick = onPickFile,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Select .mmbackup File", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LoadingStep() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Primary, modifier = Modifier.size(48.dp))
            Text("Reading backup file…", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun PreviewStep(
    accounts: List<MmAccount>,
    categoryCount: Int,
    transactionCount: Int,
    selectedAccountUids: Set<String>,
    replaceExisting: Boolean,
    onToggleAccount: (String) -> Unit,
    onReplaceExistingChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    isImporting: Boolean
) {
    // Summary card
    LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryStat(value = accounts.size.toString(), label = "Accounts")
            VerticalDivider(modifier = Modifier.height(36.dp), color = OutlineVariant.copy(alpha = 0.3f))
            SummaryStat(value = categoryCount.toString(), label = "Categories")
            VerticalDivider(modifier = Modifier.height(36.dp), color = OutlineVariant.copy(alpha = 0.3f))
            SummaryStat(value = transactionCount.toString(), label = "Transactions")
        }
    }

    // Account selection
    Text("Select accounts to import", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            accounts.forEachIndexed { idx, acc ->
                val isSelected = acc.uid in selectedAccountUids
                Surface(
                    onClick = { onToggleAccount(acc.uid) },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Primary)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(acc.title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                            Text(acc.currencyCode, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                        Text(
                            "${"$%,.2f".format(acc.balance)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (acc.balance >= 0) Primary else Tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (idx < accounts.lastIndex)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
            }
        }
    }

    // Replace / merge toggle
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { onReplaceExistingChange(!replaceExisting) },
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Replace existing data", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                    Text(
                        if (replaceExisting) "All current wallets, categories and transactions will be deleted first"
                        else "New data will be added alongside existing records",
                        style = MaterialTheme.typography.bodySmall, color = if (replaceExisting) Tertiary else OnSurfaceVariant
                    )
                }
                Switch(
                    checked = replaceExisting,
                    onCheckedChange = onReplaceExistingChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                )
            }
        }
    }

    if (isImporting) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(36.dp))
                Text("Importing data…", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
        }
    } else {
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(8.dp),
            enabled = selectedAccountUids.isNotEmpty()
        ) {
            Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import Data", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DoneStep(count: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Text("Import Complete", style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "$count transactions imported successfully.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            "Your wallets, categories, and transaction history are now available in Ledger.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Go to Dashboard", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}
