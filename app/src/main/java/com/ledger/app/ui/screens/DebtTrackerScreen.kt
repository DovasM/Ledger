package com.ledger.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.load() }
    val debts = state.debts

    val totalDebt    = debts.sumOf { it.remainingAmount }
    val totalMonthly = debts.sumOf { it.monthlyPayment }
    var strategy by remember { mutableStateOf(0) } // 0=Avalanche, 1=Snowball

    val sorted = when (strategy) {
        0    -> debts.sortedByDescending { it.apr }
        else -> debts.sortedBy { it.remainingAmount }
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
                        Text("${"$%,.2f".format(totalDebt)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
                LedgerFloatingCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("MONTHLY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.2f".format(totalMonthly)}", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
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
                    DebtCard(debt, onClick = { navController.navigate(Screen.EditDebt.createRoute(debt.id)) })
                }
            }
        }
    }
}

@Composable
private fun DebtCard(debt: Debt, onClick: () -> Unit) {
    val color = debtColor(debt)
    val pct = if (debt.totalAmount > 0) ((debt.totalAmount - debt.remainingAmount) / debt.totalAmount).toFloat().coerceIn(0f, 1f) else 0f
    val monthsLeft = if (debt.monthlyPayment > 0) (debt.remainingAmount / debt.monthlyPayment).toInt() else 0

    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, color = Color.Transparent) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(debt.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(debt.debtType, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${"$%,.2f".format(debt.remainingAmount)}", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
                        Text("of ${"$%,.2f".format(debt.totalAmount)}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = color, trackColor = color.copy(alpha = 0.15f)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${"%.1f".format(pct * 100)}% paid off", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("${debt.apr}% APR · ${"$%,.0f".format(debt.monthlyPayment)}/mo · ~$monthsLeft months left",
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }
}
