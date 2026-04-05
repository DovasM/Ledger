package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

private data class TxItem(
    val title: String, val subtitle: String, val amount: String, val isIncome: Boolean,
    val date: String, val wallet: String, val category: String,
    val categoryIcon: ImageVector, val categoryColor: Color, val note: String = ""
)

private val allTransactions = listOf(
    TxItem("Salary", "Apr 1 · Work", "+\$5,200.00", true, "Apr 1, 2026", "Checking Account", "Work", Icons.Filled.Work, Color(0xFF00513F), "April paycheck"),
    TxItem("Rent", "Apr 1 · Housing", "-\$1,200.00", false, "Apr 1, 2026", "Checking Account", "Housing", Icons.Filled.Home, Color(0xFF00513F), "Monthly rent"),
    TxItem("Groceries", "Apr 2 · Food", "-\$124.50", false, "Apr 2, 2026", "Checking Account", "Food & Dining", Icons.Filled.Restaurant, Color(0xFF1565C0)),
    TxItem("Netflix", "Apr 3 · Entertainment", "-\$15.99", false, "Apr 3, 2026", "Checking Account", "Entertainment", Icons.Filled.Movie, Color(0xFFE65100)),
    TxItem("Freelance", "Apr 3 · Work", "+\$800.00", true, "Apr 3, 2026", "Checking Account", "Freelance", Icons.Filled.Laptop, Color(0xFF1565C0), "Web project invoice"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var sheetTx by remember { mutableStateOf<TxItem?>(null) }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            TxItemDetailSheet(tx) { sheetTx = null }
        }
    }

    val filtered = if (searchQuery.isBlank()) allTransactions
    else allTransactions.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.category.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                containerColor = Primary,
                contentColor = OnPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            LedgerTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search transactions",
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = OnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("April 2026", style = MaterialTheme.typography.titleMedium, color = OnSurfaceVariant)
                Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    filtered.forEachIndexed { idx, tx ->
                        TransactionRow(
                            tx.title, tx.subtitle, tx.amount, tx.isIncome,
                            onClick = { sheetTx = tx },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (idx < filtered.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TxItemDetailSheet(tx: TxItem, onDismiss: () -> Unit) {
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
            TxItemDetailRow(Icons.Filled.CalendarToday, "Date", tx.date)
            TxItemDetailRow(Icons.Filled.Category, "Category", tx.category)
            TxItemDetailRow(Icons.Filled.AccountBalanceWallet, "Wallet", tx.wallet)
            TxItemDetailRow(
                if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type",
                if (tx.isIncome) "Income" else "Expense"
            )
            if (tx.note.isNotBlank()) {
                TxItemDetailRow(Icons.Filled.Notes, "Note", tx.note)
            }
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", color = Primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TxItemDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
