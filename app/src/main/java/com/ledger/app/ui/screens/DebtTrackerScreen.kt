package com.ledger.app.ui.screens

import com.ledger.app.ui.util.rememberMoneyFormatter
import com.ledger.app.ui.util.toCents
import com.ledger.app.ui.util.asUnits
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.DebtViewModel
import uniffi.ledger.Debt
import uniffi.ledger.DebtPayment

private val debtColors = listOf(
    Color(0xFF1565C0), Color(0xFF6A1B9A), Color(0xFFE65100),
    Color(0xFF00838F), Color(0xFF558B2F), Color(0xFF920009),
)

private fun debtColor(debt: Debt) =
    debtColors[debt.id.hashCode().let { if (it < 0) -it else it } % debtColors.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtTrackerScreen(
    navController: NavController,
    viewModel: DebtViewModel = hiltViewModel()
) {
    val money = rememberMoneyFormatter()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.load() }
    val debts = state.debts

    val totalDebt    = debts.sumOf { it.remainingAmountCents.asUnits }
    val totalMonthly = debts.sumOf { it.monthlyPaymentCents.asUnits }
    var strategy by remember { mutableStateOf(0) } // 0=Avalanche, 1=Snowball
    var payFor by remember { mutableStateOf<Debt?>(null) }
    var payAmount by remember { mutableStateOf("") }
    var payNote by remember { mutableStateOf("") }

    payFor?.let { debt ->
        AlertDialog(
            onDismissRequest = { payFor = null; payAmount = ""; payNote = "" },
            title = { Text("Record payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${money.ofUnits(debt.remainingAmountCents.asUnits)} left on ${debt.name}.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                    )
                    LedgerTextField(
                        value = payAmount,
                        onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) payAmount = v },
                        label = "Amount",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    LedgerTextField(
                        value = payNote,
                        onValueChange = { payNote = it },
                        label = "Note (optional)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = payAmount.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        viewModel.addPayment(debt.id, amount.toCents(), payNote.ifBlank { null }) {
                            payFor = null; payAmount = ""; payNote = ""
                        }
                    }
                }) { Text("Record", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { payFor = null; payAmount = ""; payNote = "" }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        )
    }

    val sorted = when (strategy) {
        0    -> debts.sortedByDescending { it.apr }
        else -> debts.sortedBy { it.remainingAmountCents.asUnits }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debt Tracker", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddDebt.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "Add debt") }
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
            // Summary
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerFloatingCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("TOTAL DEBT", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${money.ofUnits(totalDebt)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
                LedgerFloatingCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("MONTHLY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${money.ofUnits(totalMonthly)}", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Payoff strategy
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Payoff Strategy", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Avalanche (lowest interest)", "Snowball (smallest balance)").forEachIndexed { i, label ->
                            FilterChip(
                                selected = strategy == i, onClick = { strategy = i },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                            )
                        }
                    }
                    Text(
                        if (strategy == 0) "Pay minimum on all debts, put extra toward highest APR first to save the most on interest."
                        else "Pay minimum on all debts, put extra toward smallest balance first for quick wins.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                    )
                }
            }

            Text("Your Debts", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (debts.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No debts tracked. Add one to get started.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                sorted.forEach { debt ->
                    DebtCard(
                        debt = debt,
                        payments = state.payments[debt.id].orEmpty(),
                        onClick = { navController.navigate(Screen.EditDebt.createRoute(debt.id)) },
                        onLoadPayments = { viewModel.loadPayments(debt.id) },
                        onRecordPayment = { payFor = debt },
                        onDeletePayment = { viewModel.deletePayment(it, debt.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DebtCard(
    debt: Debt,
    payments: List<DebtPayment>,
    onClick: () -> Unit,
    onLoadPayments: () -> Unit,
    onRecordPayment: () -> Unit,
    onDeletePayment: (String) -> Unit
) {
    val money = rememberMoneyFormatter()
    val color = debtColor(debt)
    val pct = if (debt.totalAmountCents.asUnits > 0) ((debt.totalAmountCents.asUnits - debt.remainingAmountCents.asUnits) / debt.totalAmountCents.asUnits).toFloat().coerceIn(0f, 1f) else 0f
    val monthsLeft = if (debt.monthlyPaymentCents.asUnits > 0) (debt.remainingAmountCents.asUnits / debt.monthlyPaymentCents.asUnits).toInt() else 0
    var showHistory by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DebtPayment?>(null) }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove payment?") },
            text = { Text("${money.ofUnits(p.amountCents.asUnits)} will go back onto what you owe.") },
            confirmButton = {
                TextButton(onClick = { onDeletePayment(p.id); pendingDelete = null }) { Text("Remove", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = OnSurfaceVariant) }
            }
        )
    }

    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Surface(onClick = onClick, color = Color.Transparent) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debt.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(debt.debtType, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${money.ofUnits(debt.remainingAmountCents.asUnits)}", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
                            Text("of ${money.ofUnits(debt.totalAmountCents.asUnits)}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = color, trackColor = color.copy(alpha = 0.15f)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${"%.1f".format(pct * 100)}% paid off", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${debt.apr}% APR · ${money.ofUnits(debt.monthlyPaymentCents.asUnits, decimals = 0)}/mo · ~$monthsLeft months left",
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    shape = RoundedCornerShape(6.dp)
                ) { Text("Record payment", style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(
                    onClick = {
                        showHistory = !showHistory
                        if (showHistory) onLoadPayments()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(if (showHistory) "Hide history" else "History", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                }
            }

            if (showHistory) {
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                if (payments.isEmpty()) {
                    Text(
                        "No payments recorded yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                    )
                } else {
                    payments.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${money.ofUnits(p.amountCents.asUnits)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (p.amountCents.asUnits < 0) Error else OnSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    listOfNotNull(
                                        p.occurredAt.take(10),
                                        p.note,
                                        if (p.kind != "payment") p.kind else null
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pendingDelete = p }) {
                                Icon(Icons.Filled.Delete, "Remove", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
