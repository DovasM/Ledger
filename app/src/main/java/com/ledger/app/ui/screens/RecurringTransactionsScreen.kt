package com.ledger.app.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

private data class RecurringTx(
    val title: String, val category: String, val icon: ImageVector, val color: Color,
    val amount: String, val isIncome: Boolean, val frequency: String,
    val nextDate: String, val wallet: String, val active: Boolean = true
)

private val recurringList = listOf(
    RecurringTx("Salary",        "Work",          Icons.Filled.Work,             Color(0xFF00513F), "+\$5,200.00", true,  "Monthly",    "May 1, 2026",  "Checking Account"),
    RecurringTx("Rent",          "Housing",       Icons.Filled.Home,             Color(0xFF00513F), "-\$1,200.00", false, "Monthly",    "May 1, 2026",  "Checking Account"),
    RecurringTx("Netflix",       "Entertainment", Icons.Filled.Movie,            Color(0xFFE65100), "-\$15.99",    false, "Monthly",    "May 1, 2026",  "Checking Account"),
    RecurringTx("Spotify",       "Entertainment", Icons.Filled.MusicNote,        Color(0xFFE65100), "-\$9.99",     false, "Monthly",    "May 4, 2026",  "Checking Account"),
    RecurringTx("Gym",           "Health",        Icons.Filled.FitnessCenter,    Color(0xFF920009), "-\$40.00",    false, "Monthly",    "May 5, 2026",  "Checking Account"),
    RecurringTx("Internet",      "Housing",       Icons.Filled.Wifi,             Color(0xFF00513F), "-\$45.00",    false, "Monthly",    "May 5, 2026",  "Checking Account"),
    RecurringTx("Bus Pass",      "Transport",     Icons.Filled.DirectionsBus,    Color(0xFF6A1B9A), "-\$45.00",    false, "Monthly",    "May 1, 2026",  "Cash"),
    RecurringTx("Freelance",     "Work",          Icons.Filled.Laptop,           Color(0xFF1565C0), "+\$800.00",   true,  "Bi-weekly",  "Apr 18, 2026", "Checking Account", active = false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionsScreen(navController: NavController) {
    var showActiveOnly by remember { mutableStateOf(false) }

    val active   = recurringList.filter { it.active }
    val inactive = recurringList.filter { !it.active }
    val monthly  = active.filter { !it.isIncome }.sumOf { it.amount.replace("-\$","").replace(",","").toDoubleOrNull() ?: 0.0 }
    val income   = active.filter { it.isIncome }.sumOf { it.amount.replace("+\$","").replace(",","").toDoubleOrNull() ?: 0.0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddRecurring.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add recurring")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Summary
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MONTHLY RECURRING", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+\$${"%.2f".format(income)}", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Expenses", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("-\$${"%.2f".format(monthly)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net monthly recurring", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        val net = income - monthly
                        Text(
                            "${if (net >= 0) "+" else ""}\$${"%.2f".format(net)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (net >= 0) Primary else Tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Upcoming this month
            Text("Upcoming", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    active.forEachIndexed { idx, tx ->
                        RecurringRow(tx) {}
                        if (idx < active.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }

            if (inactive.isNotEmpty()) {
                Text("Paused", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        inactive.forEachIndexed { idx, tx ->
                            RecurringRow(tx) {}
                            if (idx < inactive.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(tx: RecurringTx, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (tx.active) tx.color.copy(alpha = 0.12f) else SurfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(tx.icon, contentDescription = null, tint = if (tx.active) tx.color else OnSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tx.title, style = MaterialTheme.typography.titleSmall, color = if (tx.active) OnSurface else OnSurfaceVariant, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tx.frequency, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("·", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("Next: ${tx.nextDate}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tx.amount, style = MaterialTheme.typography.titleSmall,
                    color = if (!tx.active) OnSurfaceVariant else if (tx.isIncome) Primary else Tertiary,
                    fontWeight = FontWeight.SemiBold)
                if (!tx.active) {
                    Surface(shape = RoundedCornerShape(4.dp), color = SurfaceContainerHighest) {
                        Text("Paused", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}
