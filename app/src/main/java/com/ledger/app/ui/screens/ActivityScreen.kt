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

private data class DayGroup(val label: String, val transactions: List<TxEntry>)
private data class TxEntry(
    val title: String,
    val category: String,
    val amount: String,
    val isIncome: Boolean,
    val time: String,
    val date: String,
    val wallet: String,
    val categoryIcon: ImageVector,
    val categoryColor: Color,
    val note: String = ""
)

private val activityFeed = listOf(
    DayGroup("Today", listOf(
        TxEntry("Freelance Payment", "Work · 2:14 PM", "+\$800.00", true, "2:14 PM", "Apr 4, 2026", "Checking Account", Icons.Filled.Laptop, Color(0xFF1565C0), "Web project invoice"),
        TxEntry("Spotify", "Entertainment · 10:02 AM", "-\$9.99", false, "10:02 AM", "Apr 4, 2026", "Checking Account", Icons.Filled.Movie, Color(0xFFE65100)),
    )),
    DayGroup("Yesterday", listOf(
        TxEntry("Groceries", "Food & Dining · 5:40 PM", "-\$124.50", false, "5:40 PM", "Apr 3, 2026", "Checking Account", Icons.Filled.Restaurant, Color(0xFF1565C0)),
        TxEntry("Bus pass", "Transport · 8:15 AM", "-\$45.00", false, "8:15 AM", "Apr 3, 2026", "Cash", Icons.Filled.DirectionsCar, Color(0xFF6A1B9A), "Monthly bus pass"),
    )),
    DayGroup("Apr 1", listOf(
        TxEntry("Salary", "Work · 9:00 AM", "+\$5,200.00", true, "9:00 AM", "Apr 1, 2026", "Checking Account", Icons.Filled.Work, Color(0xFF00513F), "April paycheck"),
        TxEntry("Rent", "Housing · 9:05 AM", "-\$1,200.00", false, "9:05 AM", "Apr 1, 2026", "Checking Account", Icons.Filled.Home, Color(0xFF00513F), "Monthly rent"),
        TxEntry("Netflix", "Entertainment · 11:30 AM", "-\$15.99", false, "11:30 AM", "Apr 1, 2026", "Checking Account", Icons.Filled.Movie, Color(0xFFE65100)),
    )),
    DayGroup("Mar 31", listOf(
        TxEntry("Coffee", "Food & Dining · 8:22 AM", "-\$4.50", false, "8:22 AM", "Mar 31, 2026", "Cash", Icons.Filled.Restaurant, Color(0xFF1565C0)),
        TxEntry("Pharmacy", "Health · 3:10 PM", "-\$22.00", false, "3:10 PM", "Mar 31, 2026", "Savings Account", Icons.Filled.HealthAndSafety, Color(0xFF920009)),
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(navController: NavController) {
    val months = listOf("Jan", "Feb", "Mar", "Apr")
    var selectedMonth by remember { mutableStateOf("Apr") }
    var sheetTx by remember { mutableStateOf<TxEntry?>(null) }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            ActivityTxSheet(tx) { sheetTx = null }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.GlobalSearch.route) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { navController.navigate(Screen.RecurringTransactions.route) }) {
                        Icon(Icons.Filled.Repeat, contentDescription = "Recurring")
                    }
                    IconButton(onClick = { navController.navigate(Screen.AddTransaction.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddTransaction.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Month selector
            ScrollableTabRow(
                selectedTabIndex = months.indexOf(selectedMonth),
                containerColor = SurfaceContainerLow,
                contentColor = Primary,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                months.forEach { month ->
                    Tab(
                        selected = selectedMonth == month,
                        onClick = { selectedMonth = month },
                        text = { Text(month, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            // Monthly summary card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("$selectedMonth SUMMARY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+\$6,000", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                            Text("+9.1% vs Mar", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Expenses", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("-\$1,422", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                            Text("-32.3% vs Mar", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Income share", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("80.8%", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceContainerHighest)) {
                            Box(modifier = Modifier.fillMaxWidth(0.808f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Primary))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net savings", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text("+\$4,578", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Top category this month
            Text("Top Spending Category", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.CategoryTransactions.createRoute("Housing")) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF00513F).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = Color(0xFF00513F), modifier = Modifier.size(26.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Housing", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        Text("50.0% of total spending", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        LinearProgressIndicator(
                            progress = { 0.50f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF00513F),
                            trackColor = SurfaceContainerHighest
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("-\$1,200", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("= last month", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            // Category breakdown mini
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("All Categories", style = MaterialTheme.typography.titleSmall, color = OnSurface,
                        modifier = Modifier.padding(bottom = 6.dp))
                    listOf(
                        Triple("Housing", 1200f, Color(0xFF00513F)),
                        Triple("Food & Dining", 340f, Color(0xFF1565C0)),
                        Triple("Transport", 180f, Color(0xFF6A1B9A)),
                        Triple("Entertainment", 95f, Color(0xFFE65100)),
                        Triple("Health", 22f, Color(0xFF920009)),
                    ).forEach { (name, amount, color) ->
                        Surface(
                            onClick = { navController.navigate(Screen.CategoryTransactions.createRoute(name)) },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                Text(name, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                                LinearProgressIndicator(
                                    progress = { (amount / 1837f).coerceIn(0f, 1f) },
                                    modifier = Modifier.width(72.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                    color = color, trackColor = SurfaceContainerHighest
                                )
                                Text(
                                    "${"%.0f".format(amount / 1837f * 100)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(28.dp)
                                )
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Transaction feed grouped by day
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            activityFeed.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            group.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        val dayTotal = group.transactions.sumOf {
                            val raw = it.amount.replace("$", "").replace(",", "")
                            raw.toDoubleOrNull() ?: 0.0
                        }
                        Text(
                            "${if (dayTotal >= 0) "+" else ""}\$${String.format("%.2f", dayTotal)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dayTotal >= 0) Primary else Tertiary
                        )
                    }
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            group.transactions.forEachIndexed { idx, tx ->
                                TransactionRow(
                                    tx.title, tx.category, tx.amount, tx.isIncome,
                                    onClick = { sheetTx = tx }
                                )
                                if (idx < group.transactions.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTxSheet(tx: TxEntry, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(
                    tx.amount,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (tx.isIncome) Primary else Tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(tx.categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tx.categoryIcon, contentDescription = null, tint = tx.categoryColor, modifier = Modifier.size(26.dp))
            }
        }

        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityDetailRow(Icons.Filled.CalendarToday, "Date", "${tx.date} at ${tx.time}")
            ActivityDetailRow(Icons.Filled.Category, "Category", tx.category.substringBefore(" ·"))
            ActivityDetailRow(Icons.Filled.AccountBalanceWallet, "Wallet", tx.wallet)
            ActivityDetailRow(
                if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type",
                if (tx.isIncome) "Income" else "Expense"
            )
            if (tx.note.isNotBlank()) {
                ActivityDetailRow(Icons.Filled.Notes, "Note", tx.note)
            }
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", color = Primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ActivityDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
