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
import com.ledger.app.ui.util.AllowanceSettings
import com.ledger.app.ui.util.BudgetPeriod
import com.ledger.app.ui.util.CategoryPace
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.util.computeStreakStats
import com.ledger.app.ui.util.formatAmount
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.BudgetViewModel
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.SettingsViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import uniffi.ledger.Budget
import uniffi.ledger.Category
import java.time.LocalDate

// A row pairs the shared CategoryPace (which knows the budget's own period window) with the DB
// Budget for edit/delete and the Category for icon and colour.
private data class BudgetRow(
    val pace: CategoryPace,
    val budget: Budget,
    val category: Category?
) {
    val limit get() = pace.limit
    val spent get() = pace.periodSpent
    val pct get() = pace.ratio.coerceIn(0.0, 1.5)
    val remaining get() = pace.remaining.coerceAtLeast(0.0)
    val isOver get() = pace.isOver
    val alertFraction get() = pace.alertThreshold / 100.0
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
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val budgetState by budgetViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()
    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val currency by settingsViewModel.currencyCode.collectAsStateWithLifecycle()
    val numberFormat by settingsViewModel.numberFormatIndex.collectAsStateWithLifecycle()
    val rollover by settingsViewModel.allowanceRollover.collectAsStateWithLifecycle()
    val window by settingsViewModel.allowanceWindow.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { budgetViewModel.load(); categoryViewModel.load(); transactionViewModel.loadAll() }

    fun money(amount: Double) = formatAmount(amount, currency, numberFormat, decimals = 0)

    val today = LocalDate.now()

    // Each budget is measured inside its own period — a weekly limit against this week's spending,
    // not the month's. Shared with the widgets so both agree.
    val stats = remember(txState.transactions, budgetState.budgets, categoryState.categories, today, rollover, window) {
        computeStreakStats(
            txState.transactions, budgetState.budgets, categoryState.categories, today,
            AllowanceSettings.of(rollover, window)
        )
    }
    val rows = stats.categoryPaces.mapNotNull { pace ->
        val budget = budgetState.budgets.find { it.id == pace.budgetId } ?: return@mapNotNull null
        BudgetRow(pace, budget, categoryState.categories.find { it.id == budget.categoryId })
    }

    // The overall budget is the only real total. Falling back to the sum of category limits is a
    // last resort and is labelled as such — that sum was never a figure the user chose.
    val overall = stats.overall
    val totalLimit = overall?.effectiveLimit ?: stats.monthlyBudgetEquivalent
    val totalSpent = overall?.periodSpent ?: rows.sumOf { it.pace.monthSpent }
    val overCount  = rows.count { it.isOver }
    val scaledTotal = overall == null && rows.any { it.pace.period != BudgetPeriod.MONTHLY }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.BudgetInsights.route) }) {
                        Icon(Icons.Filled.Insights, contentDescription = "Insights")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
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
                        Text(
                            when {
                                overall != null -> "OVERALL BUDGET · ${overall.period.label.uppercase()}"
                                scaledTotal     -> "SUM OF CATEGORY LIMITS · MONTHLY EQUIVALENT"
                                else            -> "SUM OF CATEGORY LIMITS"
                            },
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Budget", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text(money(totalLimit), style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Spent", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text(money(totalSpent), style = MaterialTheme.typography.titleLarge,
                                    color = if (totalSpent > totalLimit) Tertiary else Primary, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Remaining", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text(money((totalLimit - totalSpent).coerceAtLeast(0.0)),
                                    style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            when {
                                overall != null && overall.carried != 0.0 ->
                                    "${money(stats.dailyAllowance)}/day · ${if (overall.carried > 0) "+" else ""}${money(overall.carried)} carried from last ${overall.period.label.lowercase().removeSuffix("ly")}"
                                overall != null -> "${money(stats.dailyAllowance)}/day"
                                // No overall budget means no daily allowance at all — say so
                                // rather than quietly showing nothing.
                                else -> "No overall budget, so there is no daily allowance" +
                                    if (scaledTotal) " · weekly and yearly limits scaled to a month" else ""
                            },
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                        )
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
                                    if (row.isOver) "${row.category?.name ?: "Budget"} is ${money(row.spent - row.limit)} over this ${row.pace.period.label.lowercase().removeSuffix("ly")}"
                                    else "${row.category?.name ?: "Budget"} is at ${"%.0f".format(row.pct * 100)}% of limit",
                                    style = MaterialTheme.typography.bodySmall, color = OnSurface
                                )
                            }
                        }
                    }
                }
            }

            // Without a row of its own the overall budget is invisible here, even though it is the
            // one driving the daily allowance on the widget.
            overall?.let { o ->
                val budget = budgetState.budgets.find { it.id == o.budgetId }
                val over = o.periodRemaining < 0
                LedgerCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { budget?.let { navController.navigate(Screen.EditBudget.createRoute(it.id)) } }
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.AllInclusive, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Everything", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Text(
                                    o.period.label + if (budget?.carryOver == true) " · carries over" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (over) Tertiary else Primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${money(o.periodSpent)} / ${money(o.effectiveLimit)}",
                                    style = MaterialTheme.typography.labelMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (over) "${money(-o.periodRemaining)} over" else "${money(o.periodRemaining)} left",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (over) Tertiary else OnSurfaceVariant
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = {
                                if (o.effectiveLimit > 0) (o.periodSpent / o.effectiveLimit).toFloat().coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (over) Tertiary else Primary, trackColor = SurfaceContainerHighest
                        )
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
                        subtitle = "${money(row.spent)} / ${money(row.limit)} · ${row.pace.period.label} · ${row.status}",
                        onDismiss = { longPressRow = null },
                        onEdit = { longPressRow = null; navController.navigate(Screen.EditBudget.createRoute(row.budget.id)) },
                        onDelete = { budgetViewModel.deleteBudget(row.budget.id) {}; longPressRow = null }
                    )
                }

                rows.forEach { row ->
                    BudgetCard(
                        row,
                        money = ::money,
                        onClick = { navController.navigate(Screen.EditBudget.createRoute(row.budget.id)) },
                        onLongClick = { longPressRow = row }
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(
    row: BudgetRow,
    money: (Double) -> String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
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
                    // The period has to be visible: "$50 / $2 000" means something very different
                    // for a weekly limit than a monthly one.
                    Text(
                        "${row.pace.period.label} · ${row.status}",
                        style = MaterialTheme.typography.labelSmall,
                        color = row.statusColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${money(row.spent)} / ${money(row.limit)}",
                        style = MaterialTheme.typography.labelMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    if (row.isOver) {
                        Text("${money(row.spent - row.limit)} over", style = MaterialTheme.typography.labelSmall, color = Tertiary)
                    } else {
                        Text("${money(row.remaining)} left", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
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
