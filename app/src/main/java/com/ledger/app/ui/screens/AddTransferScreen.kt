package com.ledger.app.ui.screens

import com.ledger.app.ui.util.toCents
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.currencySymbol
import com.ledger.app.ui.util.formatCents
import com.ledger.app.ui.util.toCentsOrNull
import com.ledger.app.ui.viewmodel.SettingsViewModel
import com.ledger.app.ui.viewmodel.TransferViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel

// Moving money between your own wallets is not spending, so this never creates a transaction —
// it writes to the transfers table, which reports deliberately ignore.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransferScreen(
    navController: NavController,
    /** Set when the screen is opened from a wallet, so that wallet starts as the one money leaves. */
    initialFromWalletId: String? = null,
    walletViewModel: WalletViewModel = hiltViewModel(),
    transferViewModel: TransferViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val transferState by transferViewModel.state.collectAsStateWithLifecycle()
    val currency by settingsViewModel.currencyCode.collectAsStateWithLifecycle()
    val numberFormat by settingsViewModel.numberFormatIndex.collectAsStateWithLifecycle()

    val wallets = walletState.wallets
    // Opened from a wallet, that wallet is what you meant to send from; "to" then starts on the
    // first wallet that is not it, so the form is valid before anything is touched.
    var fromIndex by remember(wallets, initialFromWalletId) {
        mutableStateOf(wallets.indexOfFirst { it.id == initialFromWalletId }.coerceAtLeast(0))
    }
    var toIndex by remember(wallets, initialFromWalletId) {
        val from = wallets.indexOfFirst { it.id == initialFromWalletId }.coerceAtLeast(0)
        mutableStateOf(wallets.indices.firstOrNull { it != from } ?: from)
    }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var showCalc by remember { mutableStateOf(false) }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }

    val from = wallets.getOrNull(fromIndex)
    val to = wallets.getOrNull(toIndex)
    // The typed amount becomes cents immediately, because every balance it meets is in cents.
    // Subtracting units from cents is what made the preview read a hundred times too large.
    val parsedCents = amount.toCentsOrNull()
    val sameWallet = from != null && to != null && from.id == to.id
    val canSave = from != null && to != null && !sameWallet && parsedCents != null && parsedCents > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
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
            if (wallets.size < 2) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Two wallets needed", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            "A transfer moves money between your own wallets, so there has to be somewhere to move it to.",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            LedgerAmountField(
                amount = amount,
                onAmountChange = { amount = it },
                onCalculatorOpen = { showCalc = true },
                prefix = currencySymbol(currency),
                showError = showErrors && (parsedCents == null || parsedCents <= 0),
                modifier = Modifier.fillMaxWidth()
            )

            WalletPicker(
                label = "From",
                wallets = wallets.map { it.name },
                selectedIndex = fromIndex,
                expanded = fromMenu,
                onExpandedChange = { fromMenu = it },
                onSelect = { fromIndex = it; fromMenu = false }
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.ArrowDownward, null, tint = Primary, modifier = Modifier.size(20.dp)) }
            }

            WalletPicker(
                label = "To",
                wallets = wallets.map { it.name },
                selectedIndex = toIndex,
                expanded = toMenu,
                onExpandedChange = { toMenu = it },
                onSelect = { toIndex = it; toMenu = false }
            )

            if (sameWallet) {
                Text(
                    "Pick two different wallets.",
                    style = MaterialTheme.typography.bodySmall, color = Tertiary
                )
            }

            LedgerTextField(value = note, onValueChange = { note = it }, label = "Note (optional)", modifier = Modifier.fillMaxWidth())

            // Balances after the move, so a transfer that would overdraw is visible before saving.
            if (from != null && to != null && !sameWallet) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // What is in each wallet right now, so the amount can be judged against it
                        // without leaving the screen.
                        Text("Now", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        BalancePreviewRow(from.name, from.balanceCents, currency, numberFormat)
                        BalancePreviewRow(to.name, to.balanceCents, currency, numberFormat)

                        if (parsedCents != null && parsedCents > 0) {
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                            Text("After this transfer", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            BalancePreviewRow(from.name, from.balanceCents - parsedCents, currency, numberFormat)
                            BalancePreviewRow(to.name, to.balanceCents + parsedCents, currency, numberFormat)
                        }
                    }
                }
            }

            transferState.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Tertiary)
            }

            Button(
                onClick = {
                    showErrors = true
                    if (canSave && from != null && to != null && parsedCents != null) {
                        transferViewModel.createTransfer(
                            fromWalletId = from.id,
                            toWalletId = to.id,
                            amountCents = parsedCents,
                            note = note.ifBlank { null }
                        ) {
                            walletViewModel.load()
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Transfer", style = MaterialTheme.typography.titleSmall, color = OnPrimary) }
        }
    }

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = amount,
            onDismiss = { showCalc = false },
            onConfirm = { amount = it; showCalc = false }
        )
    }
}

@Composable
private fun BalancePreviewRow(name: String, balanceCents: Long, currency: String, numberFormat: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = OnSurface)
        Text(
            formatCents(balanceCents, currency, numberFormat),
            style = MaterialTheme.typography.bodySmall,
            color = if (balanceCents < 0) Tertiary else OnSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletPicker(
    label: String,
    wallets: List<String>,
    selectedIndex: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = wallets.getOrNull(selectedIndex) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            wallets.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(index) })
            }
        }
    }
}
