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
import com.ledger.app.ui.viewmodel.BudgetViewModel
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import uniffi.ledger.Budget
import uniffi.ledger.Category
import java.time.LocalDate

// Computed view model combining DB Budget + Category + spent amount
private data class BudgetRow(
    val budget: Budget,
    val category: Category?,
    val spent: Double
) {
    val limit get() = budget.limitAmount
    val pct get() = if (limit > 0) (spent / limit).coerceIn(0.0, 1.5) else 0.0
    val remaining get() = (limit - spent).coerceAtLeast(0.0)
    val isOver get() = spent > limit
    val alertFraction get() = budget.alertThreshold / 100.0
    val color get() = category?.let { colorHexToColor(it.colorHex) } ?: Color(0xFF00513F)
    val status get() = when {
        pct >= 1.0            -> "Over budget"
        pct >= alertFraction  -> "Almost full"
        else                  -> "On track"
    }
    val statusColor get() = when {
        pct >= 1.0            -> Color(0xFF920009)
        pct >= alertFraction  -> Color(0xFFE65100)
        else                  -> Color(0xFF00513F)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    navController: NavController,
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    transactionViewModel: TransactionViewModel = hiltViewModel()
) {
    val budgetState by budgetViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()
    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { budgetViewModel.load(); categoryViewModel.load(); transactionViewModel.loadAll() }

    // Build rows by joining budget → category → spent (current month only)
    val today = LocalDate.now()
    val currentMonthTxs = txState.transactions.filter {
        try {
            val d = LocalDate.parse(it.createdAt.take(10))
            d.year == today.year && d.monthValue == today.monthValue
        } catch (e: Exception) { false }
    }
    val rows = budgetState.budgets.map { budget ->
        val category = categoryState.categories.find { it.id == budget.categoryId }
        val spent = currentMonthTxs
            .filter { !it.isIncome && it.category.equals(category?.name, ignoreCase = true) }
            .sumOf { it.amount }
        BudgetRow(budget, category, spent)
    }

    val totalLimit = rows.sumOf { it.limit }
    val totalSpent = rows.sumOf { it.spent }
    val overCount  = rows.count { it.isOver }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets", style = MaterialTheme.typography.headlineSmall) },
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddBudget.route) },
                containerColor = Primary, contentColor = OnPrimary) {
                Icon(Icons.Filled.Add, contentDescription = "Add budget")
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
            // Overview card
            if (totalLimit > 0) {
                LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("MONTHLY OVERVIEW", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Budget", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text("${"$%,.0f".format(totalLimit)}", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Spent", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text("${"$%,.0f".format(totalSpent)}", style = MaterialTheme.typography.titleLarge,
                                    color = if (totalSpent > totalLimit) Tertiary else Primary, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Remaining", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text("${"$%,.0f".format((totalLimit - totalSpent).coerceAtLeast(0.0))}",
                                    style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { (totalSpent / totalLimit).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = if (totalSpent > totalLimit) Tertiary else Primary,
                                trackColor = SurfaceContainerHighest
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${"%.0f".format(totalSpent / totalLimit * 100)}% used",
                                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                if (overCount > 0) {
                                    Text("$overCount over budget", style = MaterialTheme.typography.labelSmall,
                                        color = Tertiary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // Alerts
            val alertRows = rows.filter { it.pct >= it.alertFraction }
            if (alertRows.isNotEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Alerts", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        alertRows.forEach { row ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (row.isOver) Icons.Filled.Warning else Icons.Filled.NotificationsActive,
                                    null, tint = row.statusColor, modifier = Modifier.size(18.dp))
                                Text(
                                    if (row.isOver) "${row.category?.name ?: "Budget"} is ${"$%,.0f".format(row.spent - row.limit)} over"
                                    else "${row.category?.name ?: "Budget"} is at ${"%.0f".format(row.pct * 100)}% of limit",
                                    style = MaterialTheme.typography.bodySmall, color = OnSurface
                                )
                            }
                        }
                    }
                }
            }

            Text("Category Budgets", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

            if (budgetState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (rows.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No budgets yet. Add one to get started.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                var longPressRow by remember { mutableStateOf<BudgetRow?>(null) }

                if (longPressRow != null) {
                    val row = longPressRow!!
                    LedgerActionDialog(
                        title = row.category?.name ?: "Budget",
                        subtitle = "${"$%,.0f".format(row.spent)} / ${"$%,.0f".format(row.limit)} · ${row.status}",
                        onDismiss = { longPressRow = null },
                        onEdit = { longPressRow = null; navController.navigate(Screen.EditBudget.createRoute(row.budget.id)) },
                        onDelete = { budgetViewModel.deleteBudget(row.budget.id) {}; longPressRow = null }
                    )
                }

                rows.forEach { row ->
                    BudgetCard(
                        row,
                        onClick = { navController.navigate(Screen.EditBudget.createRoute(row.budget.id)) },
                        onLongClick = { longPressRow = row }
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(row: BudgetRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    val icon = row.category?.let { iconNameToVector(it.iconName) } ?: Icons.Filled.Category
    LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(row.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = row.color, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.category?.name ?: "Unknown", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(row.status, style = MaterialTheme.typography.labelSmall, color = row.statusColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${"$%,.0f".format(row.spent)} / ${"$%,.0f".format(row.limit)}",
                        style = MaterialTheme.typography.labelMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    if (row.isOver) {
                        Text("${"$%,.0f".format(row.spent - row.limit)} over", style = MaterialTheme.typography.labelSmall, color = Tertiary)
                    } else {
                        Text("${"$%,.0f".format(row.remaining)} left", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }
            LinearProgressIndicator(
                progress = { row.pct.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = row.statusColor, trackColor = SurfaceContainerHighest
            )
        }
    }
}
