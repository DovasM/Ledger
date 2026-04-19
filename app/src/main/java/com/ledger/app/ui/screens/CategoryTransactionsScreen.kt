package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import uniffi.ledger.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTransactionsScreen(
    navController: NavController,
    categoryName: String,
    txViewModel: TransactionViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val txState  by txViewModel.state.collectAsStateWithLifecycle()
    val catState by categoryViewModel.state.collectAsStateWithLifecycle()

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) {
        txViewModel.loadAll()
        categoryViewModel.load()
    }

    // Resolve icon + color from the DB category; fall back to generic if not found
    val category = remember(catState.categories, categoryName) {
        catState.categories.find { it.name == categoryName }
    }
    val icon  = remember(category) { category?.let { iconNameToVector(it.iconName) } ?: Icons.Filled.Category }
    val color = remember(category) { category?.let { colorHexToColor(it.colorHex) } ?: Color(0xFF00513F) }

    // All transactions for this category, newest first
    val allTxs = remember(txState.transactions, categoryName) {
        txState.transactions
            .filter { it.category == categoryName }
            .sortedByDescending { it.createdAt }
    }

    val totalSpent  = allTxs.filter { !it.isIncome }.sumOf { it.amount }
    val totalEarned = allTxs.filter { it.isIncome }.sumOf { it.amount }

    // Group by year-month label, newest month first
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
    val grouped: List<Pair<String, List<Transaction>>> = remember(allTxs) {
        allTxs
            .groupBy {
                runCatching {
                    val d = LocalDate.parse(it.createdAt.take(10))
                    YearMonth.of(d.year, d.monthValue)
                }.getOrNull()
            }
            .entries
            .filter { it.key != null }
            .sortedByDescending { it.key }
            .map { (ym, txs) -> ym!!.format(monthFmt) to txs }
    }

    val dayFmt = DateTimeFormatter.ofPattern("MMM d")

    var sheetTx by remember { mutableStateOf<Transaction?>(null) }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            CatTxDetailSheet(
                tx = tx,
                icon = icon,
                color = color,
                categoryName = categoryName,
                onDismiss = { sheetTx = null },
                onEdit = {
                    sheetTx = null
                    navController.navigate(Screen.EditTransaction.createRoute(tx.id))
                },
                onDelete = {
                    txViewModel.deleteTransaction(tx.id)
                    sheetTx = null
                }
            )
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            item {
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
                            if (txState.isLoading) {
                                Text("Loading…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            } else {
                                Text("${allTxs.size} transaction${if (allTxs.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (totalSpent > 0)
                                Text("-${"$%,.2f".format(totalSpent)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.Bold)
                            if (totalEarned > 0)
                                Text("+${"$%,.2f".format(totalEarned)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (txState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            } else if (allTxs.isEmpty()) {
                item {
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No transactions in this category yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        }
                    }
                }
            } else {
                grouped.forEach { (monthLabel, txs) ->
                    item(key = monthLabel) {
                        Text(monthLabel, style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                    item(key = "card_$monthLabel") {
                        LedgerCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                txs.forEachIndexed { idx, tx ->
                                    val txDate = runCatching { LocalDate.parse(tx.createdAt.take(10)).format(dayFmt) }.getOrElse { tx.createdAt.take(10) }
                                    Surface(
                                        onClick = { sheetTx = tx },
                                        color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(tx.title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                                Text(
                                                    if (tx.note != null && tx.note!!.isNotBlank()) "$txDate · ${tx.note}" else txDate,
                                                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                            Text(
                                                "${if (tx.isIncome) "+" else "-"}${"$%,.2f".format(tx.amount)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (tx.isIncome) Primary else Tertiary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (idx < txs.lastIndex)
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
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
private fun CatTxDetailSheet(
    tx: Transaction,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    categoryName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val displayDate = runCatching { LocalDate.parse(tx.createdAt.take(10)).format(dateFmt) }.getOrElse { tx.createdAt.take(10) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("\"${tx.title}\" will be permanently removed.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = Tertiary) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(
                    "${if (tx.isIncome) "+" else "-"}${"$%,.2f".format(tx.amount)}",
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
            CatDetailRow(Icons.Filled.CalendarToday, "Date", displayDate)
            CatDetailRow(Icons.Filled.Category, "Category", categoryName)
            CatDetailRow(
                if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type", if (tx.isIncome) "Income" else "Expense"
            )
            if (!tx.note.isNullOrBlank())
                CatDetailRow(Icons.Filled.Notes, "Note", tx.note!!)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit")
            }
        }
    }
}

@Composable
private fun CatDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
