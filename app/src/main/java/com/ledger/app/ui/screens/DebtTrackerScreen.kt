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
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

private data class Debt(
    val id: Long, val name: String, val type: String,
    val total: Double, val remaining: Double,
    val apr: Double, val monthlyPayment: Double,
    val color: Color
) {
    val pct get() = ((total - remaining) / total).toFloat().coerceIn(0f, 1f)
    val paid get() = total - remaining
    val monthsLeft get() = if (monthlyPayment > 0) (remaining / monthlyPayment).toInt() else 0
}

private val debts = listOf(
    Debt(1, "Chase Sapphire", "Credit Card", 3_000.0, 1_240.0, 24.99, 200.0, Color(0xFF1565C0)),
    Debt(2, "Student Loan", "Federal Loan", 25_000.0, 8_500.0, 4.5, 280.0, Color(0xFF6A1B9A)),
    Debt(3, "Toyota Car Loan", "Auto Loan", 18_000.0, 4_200.0, 3.9, 380.0, Color(0xFFE65100)),
    Debt(4, "Personal Loan", "Personal", 5_000.0, 2_800.0, 8.9, 150.0, Color(0xFF00838F)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtTrackerScreen(navController: NavController) {
    val totalDebt = debts.sumOf { it.remaining }
    val totalMonthly = debts.sumOf { it.monthlyPayment }
    var strategy by remember { mutableStateOf(0) } // 0=Avalanche, 1=Snowball

    val sorted = when (strategy) {
        0 -> debts.sortedByDescending { it.apr }       // Avalanche: highest APR first
        else -> debts.sortedBy { it.remaining }        // Snowball: smallest balance first
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

            // Debt cards
            Text("Your Debts", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            sorted.forEach { debt ->
                DebtCard(debt, onClick = { navController.navigate(Screen.EditDebt.createRoute(debt.id)) })
            }
        }
    }
}

@Composable
private fun DebtCard(debt: Debt, onClick: () -> Unit) {
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, color = Color.Transparent) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(debt.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(debt.type, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${"$%,.2f".format(debt.remaining)}", style = MaterialTheme.typography.titleMedium, color = debt.color, fontWeight = FontWeight.Bold)
                        Text("of ${"$%,.2f".format(debt.total)}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
                LinearProgressIndicator(
                    progress = { debt.pct },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = debt.color,
                    trackColor = debt.color.copy(alpha = 0.15f)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${"%.1f".format(debt.pct * 100)}% paid off", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("${debt.apr}% APR · ${"$%,.0f".format(debt.monthlyPayment)}/mo · ~${debt.monthsLeft} months left",
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }
}
