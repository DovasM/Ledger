package com.ledger.app.ui.screens

import com.ledger.app.ui.util.rememberMoneyFormatter
import com.ledger.app.ui.util.asUnits
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.TransactionViewModel
import com.ledger.app.ui.viewmodel.TransferViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel
import uniffi.ledger.Transfer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailsScreen(
    navController: NavController,
    walletId: String,
    walletViewModel: WalletViewModel = hiltViewModel(),
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    transferViewModel: TransferViewModel = hiltViewModel()
) {
    val money = rememberMoneyFormatter()
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val transferState by transferViewModel.state.collectAsStateWithLifecycle()
    val wallet = walletState.wallets.find { it.id == walletId }

    val today = java.time.LocalDate.now()
    var chartPeriodStart by remember { mutableStateOf(today.minusDays(29)) }

    LaunchedEffect(walletId) {
        transactionViewModel.loadForWallet(walletId)
        transferViewModel.load()
    }

    val periodTxs = txState.transactions.filter {
        try { java.time.LocalDate.parse(it.occurredAt.take(10)) >= chartPeriodStart } catch (e: Exception) { false }
    }
    val monthIncome = periodTxs.filter { it.isIncome }.sumOf { it.amountCents.asUnits }
    val monthExpenses = periodTxs.filter { !it.isIncome }.sumOf { it.amountCents.asUnits }
    val recentTxns = txState.transactions.take(5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(wallet?.name ?: "Wallet", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.EditWallet.createRoute(walletId)) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit wallet")
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
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("CURRENT BALANCE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(
                                "${money.ofUnits(wallet?.balanceCents?.asUnits ?: 0.0)}",
                                style = MaterialTheme.typography.displaySmall,
                                color = OnSurface, fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    LedgerTrendChart(
                        transactions = txState.transactions,
                        currentBalance = wallet?.balanceCents?.asUnits ?: 0.0,
                        periods = listOf("1W", "1M", "3M", "6M", "1Y"),
                        defaultPeriod = "1M",
                        accentColor = Primary,
                        onPeriodChanged = { chartPeriodStart = it }
                    )
                }
            }

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Period Stats", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WalletStatCol("Income", "+${money.ofUnits(monthIncome)}", Primary)
                        VerticalDivider(modifier = Modifier.height(40.dp), color = OutlineVariant.copy(alpha = 0.3f))
                        WalletStatCol("Expenses", "-${money.ofUnits(monthExpenses)}", Tertiary)
                        VerticalDivider(modifier = Modifier.height(40.dp), color = OutlineVariant.copy(alpha = 0.3f))
                        WalletStatCol("Net", "${money.ofUnits(monthIncome - monthExpenses)}", OnSurface)
                    }
                }
            }

            if (wallet?.description?.isNotBlank() == true) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Wallet Details", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                        WalletDetailRow("Description", wallet.description)
                        WalletDetailRow("Total transactions", txState.transactions.size.toString())
                    }
                }
            }

            Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            if (recentTxns.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        recentTxns.forEach { tx ->
                            TransactionRow(
                                tx.title,
                                tx.category,
                                (if (tx.isIncome) "+" else "-") + "${money.ofUnits(tx.amountCents.asUnits)}",
                                isIncome = tx.isIncome
                            )
                        }
                    }
                }
            }

            // Transfers are neither income nor expense, so they never appear above. Without this
            // they could be created and then never seen or undone again.
            val walletTransfers = transferState.transfers.filter {
                it.fromWalletId == walletId || it.toWalletId == walletId
            }
            var pendingDelete by remember { mutableStateOf<Transfer?>(null) }

            pendingDelete?.let { transfer ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("Remove this transfer?") },
                    text = {
                        Text(
                            "${money.of(transfer.amountCents)} will stop counting in both wallets. " +
                                "It is not income or spending, so no report changes."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            transferViewModel.deleteTransfer(transfer.id)
                            pendingDelete = null
                        }) { Text("Remove", color = Error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = OnSurfaceVariant) }
                    }
                )
            }

            Text("Transfers", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            if (walletTransfers.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No transfers to or from this wallet.",
                            style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        walletTransfers.forEach { transfer ->
                            val outgoing = transfer.fromWalletId == walletId
                            val otherId = if (outgoing) transfer.toWalletId else transfer.fromWalletId
                            val otherName = walletState.wallets.find { it.id == otherId }?.name ?: "another wallet"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (outgoing) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                    contentDescription = if (outgoing) "Sent" else "Received",
                                    tint = if (outgoing) Tertiary else Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (outgoing) "To $otherName" else "From $otherName",
                                        style = MaterialTheme.typography.bodyMedium, color = OnSurface
                                    )
                                    Text(
                                        listOfNotNull(transfer.createdAt.take(10), transfer.note).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                    )
                                }
                                Text(
                                    (if (outgoing) "-" else "+") + money.of(transfer.amountCents),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (outgoing) Tertiary else Primary,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(onClick = { pendingDelete = transfer }) {
                                    Icon(
                                        Icons.Filled.Delete, "Remove transfer",
                                        tint = OnSurfaceVariant, modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TextButton(onClick = { navController.navigate(Screen.AddTransfer.createRoute(walletId)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Transfer", color = Primary, style = MaterialTheme.typography.labelLarge)
            }

            TextButton(onClick = { navController.navigate(Screen.Transactions.route) }, modifier = Modifier.fillMaxWidth()) {
                Text("See All Transactions", color = Primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}


@Composable
private fun WalletStatCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun WalletDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
