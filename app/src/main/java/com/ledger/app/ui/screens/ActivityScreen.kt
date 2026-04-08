package com.ledger.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.TagViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import uniffi.ledger.Tag
import uniffi.ledger.Transaction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(
    navController: NavController,
    viewModel: TransactionViewModel = hiltViewModel(),
    tagViewModel: TagViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.loadAll() }

    var sheetTx by remember { mutableStateOf<Transaction?>(null) }
    var sheetTxTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var longPressTx by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(sheetTx?.id) {
        val id = sheetTx?.id
        sheetTxTags = if (id != null) tagViewModel.loadTagsForTransaction(id) else emptyList()
    }

    // Long-press action dialog
    if (longPressTx != null) {
        val tx = longPressTx!!
        LedgerActionDialog(
            title = tx.title,
            subtitle = (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
            onDismiss = { longPressTx = null },
            onEdit = { longPressTx = null; navController.navigate(Screen.EditTransaction.createRoute(tx.id)) },
            onDelete = { viewModel.deleteTransaction(tx.id) {}; longPressTx = null }
        )
    }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            ActivityTxSheet(tx, tags = sheetTxTags, onDismiss = { sheetTx = null },
                onEdit = { navController.navigate(Screen.EditTransaction.createRoute(tx.id)); sheetTx = null })
        }
    }

    // Group transactions by date (first 10 chars of createdAt ISO string)
    val grouped = state.transactions.groupBy { it.createdAt.take(10) }
        .entries.sortedByDescending { it.key }

    val totalIncome = state.transactions.filter { it.isIncome }.sumOf { it.amount }
    val totalExpenses = state.transactions.filter { !it.isIncome }.sumOf { it.amount }

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
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTransaction.route) },
                containerColor = Primary, contentColor = OnPrimary) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
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
            // Summary card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("SUMMARY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+${"$%,.2f".format(totalIncome)}", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Expenses", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("-${"$%,.2f".format(totalExpenses)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    val net = totalIncome - totalExpenses
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net savings", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Text("${"$%,.2f".format(net)}", style = MaterialTheme.typography.titleMedium,
                            color = if (net >= 0) Primary else Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (state.transactions.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ReceiptLong, null, tint = OnSurfaceVariant, modifier = Modifier.size(40.dp))
                            Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                            TextButton(onClick = { navController.navigate(Screen.AddTransaction.route) }) {
                                Text("Add your first transaction", color = Primary)
                            }
                        }
                    }
                }
            } else {
                // Transaction feed grouped by day
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("Tap · Hold to edit/delete", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                grouped.forEach { (date, txns) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val dayIncome = txns.filter { it.isIncome }.sumOf { it.amount }
                        val dayExpenses = txns.filter { !it.isIncome }.sumOf { it.amount }
                        val dayNet = dayIncome - dayExpenses
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(date, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (dayNet >= 0) "+" else "") + "${"$%,.2f".format(dayNet)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dayNet >= 0) Primary else Tertiary
                            )
                        }
                        LedgerCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                txns.forEachIndexed { idx, tx ->
                                    TransactionRow(
                                        tx.title,
                                        tx.category,
                                        (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                                        isIncome = tx.isIncome,
                                        onClick = { sheetTx = tx },
                                        onLongClick = { longPressTx = tx }
                                    )
                                    if (idx < txns.size - 1) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityTxSheet(tx: Transaction, tags: List<Tag>, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val accentColor = if (tx.isIncome) Primary else Tertiary
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(
                    (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ReceiptLong, null, tint = accentColor, modifier = Modifier.size(26.dp))
            }
        }
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityDetailRow(Icons.Filled.CalendarToday, "Date", tx.createdAt.take(10))
            ActivityDetailRow(Icons.Filled.Category, "Category", tx.category.ifBlank { "—" })
            ActivityDetailRow(
                if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type", if (tx.isIncome) "Income" else "Expense"
            )
            if (!tx.note.isNullOrBlank()) {
                ActivityDetailRow(Icons.Filled.Notes, "Note", tx.note!!)
            }
        }
        if (tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Label, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("Tags", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("#${tag.name}", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = accentColor.copy(alpha = 0.1f),
                                labelColor = accentColor
                            )
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
            Button(onClick = onEdit, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("Edit") }
        }
    }
}

@Composable
private fun ActivityDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
