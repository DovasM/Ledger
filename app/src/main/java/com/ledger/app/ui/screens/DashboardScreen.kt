package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.*
import uniffi.ledger.Transaction
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    goalViewModel: GoalViewModel = hiltViewModel(),
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    recurringViewModel: RecurringViewModel = hiltViewModel()
) {
    val txState by txViewModel.state.collectAsStateWithLifecycle()
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val goalState by goalViewModel.state.collectAsStateWithLifecycle()
    val budgetState by budgetViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()
    val recurringState by recurringViewModel.state.collectAsStateWithLifecycle()

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) {
        txViewModel.loadAll()
        walletViewModel.load()
        goalViewModel.load()
        budgetViewModel.load()
        categoryViewModel.load()
        recurringViewModel.load()
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var sheetTx by remember { mutableStateOf<Transaction?>(null) }

    // ── Derived data ─────────────────────────────────────────────────────────

    val totalBalance = walletState.wallets.sumOf { it.balance }

    val today = LocalDate.now()
    var chartPeriodStart by remember { mutableStateOf(today.withDayOfMonth(1)) }

    // Period-filtered income/expenses (synced with chart selection)
    val periodTxs = txState.transactions.filter {
        try { LocalDate.parse(it.createdAt.take(10)) >= chartPeriodStart } catch (e: Exception) { false }
    }
    val periodIncome = periodTxs.filter { it.isIncome }.sumOf { it.amount }
    val periodExpenses = periodTxs.filter { !it.isIncome }.sumOf { it.amount }

    val greeting = when (today.let { java.time.LocalTime.now().hour }) {
        in 0..11 -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }

    // Current month transactions
    val currentMonthTxs = txState.transactions.filter {
        try {
            val d = LocalDate.parse(it.createdAt.take(10))
            d.year == today.year && d.monthValue == today.monthValue
        } catch (e: Exception) { false }
    }

    // Cash flow forecast: recurring items due before end of month
    val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
    val pendingRecurring = recurringState.recurring.filter {
        try {
            val d = LocalDate.parse(it.nextDate.take(10))
            !d.isBefore(today) && !d.isAfter(endOfMonth)
        } catch (e: Exception) { false }
    }
    val pendingIncome = pendingRecurring.filter { it.isIncome }.sumOf { it.amount }
    val pendingExpenses = pendingRecurring.filter { !it.isIncome }.sumOf { it.amount }
    val forecastBalance = totalBalance + pendingIncome - pendingExpenses
    val forecastDelta = forecastBalance - totalBalance

    // Budget rows (reuse BudgetsScreen logic, first 3)
    val spentByCategoryName = currentMonthTxs
        .filter { !it.isIncome }
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }

    val budgetRows = budgetState.budgets.map { budget ->
        val cat = categoryState.categories.find { it.id == budget.categoryId }
        val spent = spentByCategoryName[cat?.name] ?: 0.0
        Triple(budget, cat, spent)
    }.take(3)

    // Top categories (expenses only, top 4)
    val totalExpenses = currentMonthTxs.filter { !it.isIncome }.sumOf { it.amount }
    val topCategories = spentByCategoryName.entries
        .sortedByDescending { it.value }
        .take(4)

    // ── Sheets ────────────────────────────────────────────────────────────────

    if (sheetTx != null) {
        val tx = sheetTx!!
        val accentColor = if (tx.isIncome) Primary else Tertiary
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                    Text(
                        (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                        style = MaterialTheme.typography.titleLarge, color = accentColor, fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashDetailRow(Icons.Filled.CalendarToday, "Date", tx.createdAt.take(10))
                    DashDetailRow(Icons.Filled.Category, "Category", tx.category.ifBlank { "—" })
                    DashDetailRow(if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, "Type", if (tx.isIncome) "Income" else "Expense")
                    if (!tx.note.isNullOrBlank()) DashDetailRow(Icons.Filled.Notes, "Note", tx.note!!)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { sheetTx = null }, modifier = Modifier.weight(1f)) { Text("Close") }
                    Button(onClick = { sheetTx = null; navController.navigate(Screen.EditTransaction.createRoute(tx.id)) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Edit") }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add New", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                AddActionCard(Icons.Filled.SwapHoriz, "Add Transaction", "Record income or expense", Primary) {
                    showAddSheet = false; navController.navigate(Screen.AddTransaction.route)
                }
                AddActionCard(Icons.Filled.EmojiEvents, "New Savings Goal", "Set a target and track progress", Color(0xFF1565C0)) {
                    showAddSheet = false; navController.navigate(Screen.AddGoal.route)
                }
                AddActionCard(Icons.Filled.AccountBalanceWallet, "Add Wallet", "Bank account, cash, or investment", Color(0xFF6A1B9A)) {
                    showAddSheet = false; navController.navigate(Screen.AddWallet.route)
                }
                AddActionCard(Icons.Filled.LocalOffer, "New Category", "Custom category with icon", Color(0xFFE65100)) {
                    showAddSheet = false; navController.navigate(Screen.AddCategory.route)
                }
                AddActionCard(Icons.Filled.CameraAlt, "Skenuoti čekį", "AI automatiškai užpildo duomenis", Color(0xFF2E7D32)) {
                    showAddSheet = false; navController.navigate(Screen.ReceiptScan.route)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    Scaffold(
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }, containerColor = Primary, contentColor = OnPrimary) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(greeting, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("Ledger", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.navigate(Screen.GlobalSearch.route) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = OnSurface)
                    }
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = OnSurface)
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = OnSurface)
                    }
                }
            }

            // ── Total Balance ─────────────────────────────────────────────────
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Balance — centered, full width
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "TOTAL BALANCE",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceVariant,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "${"$%,.2f".format(totalBalance)}",
                            style = MaterialTheme.typography.displaySmall,
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    // Income | divider | Expenses — each centered in their half
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("INCOME", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                            Text("+${"$%,.2f".format(periodIncome)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                        VerticalDivider(modifier = Modifier.height(36.dp), color = OutlineVariant.copy(alpha = 0.4f))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("EXPENSES", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                            Text("-${"$%,.2f".format(periodExpenses)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    LedgerTrendChart(
                        transactions = txState.transactions,
                        currentBalance = totalBalance,
                        onPeriodChanged = { chartPeriodStart = it }
                    )
                }
            }

            // ── Portfolio (connect when investments page is ready) ─────────────
//            Text("Portfolio", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
//            LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.InvestmentPortfolio.route) }) {
//                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
//                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
//                        Column {
//                            Text("PORTFOLIO VALUE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
//                            Text("$3,560.00", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
//                            Text("+$124.50 (3.6%) today", style = MaterialTheme.typography.bodySmall, color = Primary)
//                        }
//                        Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
//                    }
//                }
//            }

            // ── Savings Goals ─────────────────────────────────────────────────
            Text("Savings Goals", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.SavingsGoals.route) }) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (goalState.goals.isEmpty()) {
                        Text("No savings goals yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        goalState.goals.take(2).forEach { goal ->
                            GoalProgressRow(
                                name = goal.name,
                                current = goal.currentAmount.toFloat(),
                                target = goal.targetAmount.toFloat(),
                                color = Primary,
                                onClick = { navController.navigate(Screen.GoalDetails.createRoute(goal.id)) }
                            )
                        }
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    TextButton(onClick = { navController.navigate(Screen.SavingsGoals.route) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View All Goals", color = Primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Recent Transactions ───────────────────────────────────────────
            Text("Recent Transactions", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (txState.transactions.isEmpty()) {
                        Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        txState.transactions.take(5).forEach { tx ->
                            TransactionRow(
                                title = tx.title,
                                subtitle = "${tx.createdAt.take(10)} · ${tx.category}",
                                amount = (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                                isIncome = tx.isIncome,
                                onClick = { sheetTx = tx }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = OutlineVariant.copy(alpha = 0.2f))
                    TextButton(onClick = { navController.navigate(Screen.Activity.route) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Timeline, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("See Full Activity", color = Primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ExpandMore, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Cash Flow Forecast ────────────────────────────────────────────
            Text("Cash Flow", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.CashFlowForecast.route) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.WaterDrop, null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "End of ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} forecast",
                            style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold
                        )
                        val recurringCount = recurringState.recurring.size
                        Text(
                            if (recurringCount > 0) "Based on $recurringCount recurring transaction${if (recurringCount != 1) "s" else ""}"
                            else "No recurring transactions set up",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${"$%,.0f".format(forecastBalance)}", style = MaterialTheme.typography.titleMedium,
                            color = if (forecastDelta >= 0) Primary else Tertiary, fontWeight = FontWeight.Bold)
                        Text(
                            (if (forecastDelta >= 0) "+" else "") + "${"$%,.0f".format(forecastDelta)}",
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            // ── Budgets ───────────────────────────────────────────────────────
            Text("Budgets", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.Budgets.route) }) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (budgetRows.isEmpty()) {
                        Text("No budgets yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        budgetRows.forEach { (budget, cat, spent) ->
                            val limit = budget.limitAmount
                            val pct = if (limit > 0) (spent / limit).coerceIn(0.0, 1.5).toFloat() else 0f
                            val color = cat?.let { colorHexToColor(it.colorHex) } ?: Primary
                            val label = cat?.name ?: "Budget"
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.EditBudget.createRoute(budget.id)) },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${"$%.0f".format(spent)} / ${"$%.0f".format(limit)}",
                                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                        )
                                        Surface(shape = RoundedCornerShape(4.dp), color = (if (pct > 1f) Tertiary else color).copy(alpha = 0.12f)) {
                                            Text(
                                                "${"%.0f".format(pct * 100)}%",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (pct > 1f) Tertiary else color,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { pct.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = if (pct > 1f) Tertiary else color,
                                    trackColor = SurfaceContainerHighest
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    TextButton(onClick = { navController.navigate(Screen.Budgets.route) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PieChart, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View All Budgets", color = Primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Top Categories ────────────────────────────────────────────────
            Text("Top Categories", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (topCategories.isEmpty()) {
                        Text("No expenses this month.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        topCategories.forEach { (catName, spent) ->
                            val cat = categoryState.categories.find { it.name.equals(catName, ignoreCase = true) }
                            val color = cat?.let { colorHexToColor(it.colorHex) } ?: Primary
                            val icon = cat?.let { iconNameToVector(it.iconName) } ?: Icons.Filled.Category
                            TopCategoryRow(catName, icon, spent.toFloat(), totalExpenses.toFloat().coerceAtLeast(1f), color) {
                                navController.navigate(Screen.CategoryTransactions.createRoute(catName))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Composable helpers ───────────────────────────────────────────────────────

@Composable
private fun AddActionCard(icon: ImageVector, title: String, description: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = SurfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TopCategoryRow(name: String, icon: ImageVector, spent: Float, totalSpent: Float, color: Color, onClick: () -> Unit) {
    val share = (spent / totalSpent).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
            Text("-${"$%.0f".format(spent)}", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.10f)) {
                Text("${"%.0f".format(share * 100)}%", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = color, trackColor = SurfaceContainerHighest
        )
    }
}

@Composable
private fun GoalProgressRow(name: String, current: Float, target: Float, color: Color, onClick: (() -> Unit)? = null) {
    val progress = if (target > 0f) (current / target).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.10f)) {
                Text("${"%.0f".format(progress * 100)}%", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color, trackColor = SurfaceContainerHighest)
        Text("${"$%.0f".format(current)} saved of ${"$%.0f".format(target)}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}


@Composable
private fun DashDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
