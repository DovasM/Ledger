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
import com.ledger.app.ui.theme.*

private data class CatTx(
    val title: String, val subtitle: String, val amount: String, val isIncome: Boolean,
    val date: String, val wallet: String, val note: String = ""
)

private data class CategoryMeta(val icon: ImageVector, val color: Color, val transactions: List<CatTx>)

private val categoryData: Map<String, CategoryMeta> = mapOf(
    "Housing" to CategoryMeta(
        Icons.Filled.Home, Color(0xFF00513F),
        listOf(
            CatTx("Rent", "Apr 1 · Housing", "-\$1,200.00", false, "Apr 1, 2026", "Checking Account", "Monthly rent"),
            CatTx("Electricity", "Mar 18 · Housing", "-\$85.00", false, "Mar 18, 2026", "Checking Account"),
            CatTx("Internet", "Mar 5 · Housing", "-\$45.00", false, "Mar 5, 2026", "Checking Account"),
            CatTx("Rent", "Mar 1 · Housing", "-\$1,200.00", false, "Mar 1, 2026", "Checking Account", "Monthly rent"),
        )
    ),
    "Food & Dining" to CategoryMeta(
        Icons.Filled.Restaurant, Color(0xFF1565C0),
        listOf(
            CatTx("Groceries", "Apr 3 · Food", "-\$124.50", false, "Apr 3, 2026", "Checking Account"),
            CatTx("Coffee", "Apr 2 · Food", "-\$4.50", false, "Apr 2, 2026", "Cash"),
            CatTx("Restaurant", "Mar 28 · Food", "-\$62.00", false, "Mar 28, 2026", "Checking Account", "Dinner with friends"),
            CatTx("Groceries", "Mar 15 · Food", "-\$98.30", false, "Mar 15, 2026", "Checking Account"),
        )
    ),
    "Transport" to CategoryMeta(
        Icons.Filled.DirectionsCar, Color(0xFF6A1B9A),
        listOf(
            CatTx("Bus pass", "Apr 3 · Transport", "-\$45.00", false, "Apr 3, 2026", "Cash", "Monthly bus pass"),
            CatTx("Taxi", "Mar 22 · Transport", "-\$18.00", false, "Mar 22, 2026", "Cash"),
            CatTx("Fuel", "Mar 10 · Transport", "-\$72.00", false, "Mar 10, 2026", "Checking Account"),
            CatTx("Bus pass", "Mar 1 · Transport", "-\$45.00", false, "Mar 1, 2026", "Cash", "Monthly bus pass"),
        )
    ),
    "Entertainment" to CategoryMeta(
        Icons.Filled.Movie, Color(0xFFE65100),
        listOf(
            CatTx("Spotify", "Apr 4 · Entertainment", "-\$9.99", false, "Apr 4, 2026", "Checking Account"),
            CatTx("Netflix", "Apr 1 · Entertainment", "-\$15.99", false, "Apr 1, 2026", "Checking Account"),
            CatTx("Cinema", "Mar 25 · Entertainment", "-\$24.00", false, "Mar 25, 2026", "Cash"),
            CatTx("Spotify", "Mar 4 · Entertainment", "-\$9.99", false, "Mar 4, 2026", "Checking Account"),
        )
    ),
    "Health" to CategoryMeta(
        Icons.Filled.HealthAndSafety, Color(0xFF920009),
        listOf(
            CatTx("Pharmacy", "Mar 31 · Health", "-\$22.00", false, "Mar 31, 2026", "Savings Account"),
            CatTx("Doctor", "Mar 14 · Health", "-\$50.00", false, "Mar 14, 2026", "Savings Account", "Checkup"),
        )
    ),
    "Work" to CategoryMeta(
        Icons.Filled.Work, Color(0xFF00513F),
        listOf(
            CatTx("Salary", "Apr 1 · Work", "+\$5,200.00", true, "Apr 1, 2026", "Checking Account", "April paycheck"),
            CatTx("Freelance", "Mar 30 · Work", "+\$800.00", true, "Mar 30, 2026", "Checking Account", "Web project invoice"),
            CatTx("Salary", "Mar 1 · Work", "+\$5,200.00", true, "Mar 1, 2026", "Checking Account", "March paycheck"),
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTransactionsScreen(navController: NavController, categoryName: String) {
    val meta = categoryData[categoryName]
    val icon = meta?.icon ?: Icons.Filled.Category
    val color = meta?.color ?: Color(0xFF00513F)
    val transactions = meta?.transactions ?: emptyList()

    val totalSpent = transactions
        .filter { !it.isIncome }
        .sumOf { it.amount.replace("-\$", "").replace(",", "").toDoubleOrNull() ?: 0.0 }
    val totalEarned = transactions
        .filter { it.isIncome }
        .sumOf { it.amount.replace("+\$", "").replace(",", "").toDoubleOrNull() ?: 0.0 }

    var sheetTx by remember { mutableStateOf<CatTx?>(null) }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            CatTxDetailSheet(tx, icon, color, categoryName) { sheetTx = null }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName, style = MaterialTheme.typography.headlineSmall) },
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Category header card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(categoryName, style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("${transactions.size} transactions", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (totalSpent > 0) {
                            Text(
                                "-\$${"%.2f".format(totalSpent)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (totalEarned > 0) {
                            Text(
                                "+\$${"%.2f".format(totalEarned)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Transaction list
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("All Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }

            if (transactions.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions in this category", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        transactions.forEachIndexed { idx, tx ->
                            TransactionRow(
                                tx.title, tx.subtitle, tx.amount, tx.isIncome,
                                onClick = { sheetTx = tx }
                            )
                            if (idx < transactions.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatTxDetailSheet(tx: CatTx, icon: ImageVector, color: Color, category: String, onDismiss: () -> Unit) {
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
                modifier = Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
        }

        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CatDetailRow(Icons.Filled.CalendarToday, "Date", tx.date)
            CatDetailRow(Icons.Filled.Category, "Category", category)
            CatDetailRow(Icons.Filled.AccountBalanceWallet, "Wallet", tx.wallet)
            CatDetailRow(
                if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type",
                if (tx.isIncome) "Income" else "Expense"
            )
            if (tx.note.isNotBlank()) {
                CatDetailRow(Icons.Filled.Notes, "Note", tx.note)
            }
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", color = Primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CatDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
