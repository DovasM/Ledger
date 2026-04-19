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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.BudgetViewModel
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.RecurringViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import uniffi.ledger.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetInsightsScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel(),
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    recurringViewModel: RecurringViewModel = hiltViewModel()
) {
    val txState        by txViewModel.state.collectAsStateWithLifecycle()
    val budgetState    by budgetViewModel.state.collectAsStateWithLifecycle()
    val catState       by categoryViewModel.state.collectAsStateWithLifecycle()
    val recurringState by recurringViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        txViewModel.loadAll()
        budgetViewModel.load()
        categoryViewModel.load()
        recurringViewModel.load()
    }

    val today = remember { LocalDate.now() }
    val thisYm = remember { YearMonth.now() }

    // ── Core data ──────────────────────────────────────────────────────────────

    val thisMonthTxs = remember(txState.transactions, thisYm) {
        txState.transactions.filter {
            runCatching { LocalDate.parse(it.createdAt.take(10)).let { d ->
                d.year == thisYm.year && d.monthValue == thisYm.monthValue
            }}.getOrElse { false }
        }
    }

    val thisMonthExpenses = remember(thisMonthTxs) { thisMonthTxs.filter { !it.isIncome }.sumOf { it.amount } }
    val thisMonthIncome   = remember(thisMonthTxs) { thisMonthTxs.filter { it.isIncome }.sumOf { it.amount } }

    // category name → amount spent this month
    val spentByCategory = remember(thisMonthTxs) {
        thisMonthTxs.filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
    }

    // Spending in budgeted categories only — matches what BudgetsScreen shows
    val budgetedSpent = remember(budgetState.budgets, spentByCategory, catState.categories) {
        budgetState.budgets.sumOf { b ->
            val catName = catState.categories.find { it.id == b.categoryId }?.name ?: return@sumOf 0.0
            spentByCategory[catName] ?: 0.0
        }
    }

    // category id → category object (for icon/color lookup)
    val categoryById = remember(catState.categories) { catState.categories.associateBy { it.id } }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Fixed Costs")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget & Insights", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (txState.isLoading || budgetState.isLoading) {
                        Box(modifier = Modifier.padding(end = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Budgets.route) }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Manage Budgets")
                    }
                    IconButton(onClick = { navController.navigate(Screen.CategoriesManagement.route) }) {
                        Icon(Icons.Filled.Category, contentDescription = "Categories")
                    }
                },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceContainerLow,
                contentColor = Primary
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> OverviewTab(
                        navController = navController,
                        thisMonthIncome = thisMonthIncome,
                        thisMonthExpenses = thisMonthExpenses,
                        budgetedSpent = budgetedSpent,
                        recentTxs = thisMonthTxs.sortedByDescending { it.createdAt }.take(5),
                        budgets = budgetState.budgets,
                        spentByCategory = spentByCategory,
                        categoryById = categoryById,
                        today = today
                    )
                    1 -> FixedCostsTab(
                        recurringExpenses = recurringState.recurring.filter { !it.isIncome }
                    )
                }
            }
        }
    }
}

// ── Tab 1: Overview ───────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(
    navController: NavController,
    thisMonthIncome: Double,
    thisMonthExpenses: Double,
    budgetedSpent: Double,
    recentTxs: List<Transaction>,
    budgets: List<uniffi.ledger.Budget>,
    spentByCategory: Map<String, Double>,
    categoryById: Map<String, uniffi.ledger.Category>,
    today: LocalDate
) {
    val monthName = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val totalBudget = budgets.sumOf { it.limitAmount }
    val hasBudgets = budgets.isNotEmpty() && totalBudget > 0
    val savingsRate = if (thisMonthIncome > 0) (thisMonthIncome - thisMonthExpenses) / thisMonthIncome * 100 else null

    // Hero card — budget vs spending (budgeted categories only) or plain spending if no budgets
    LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${monthName.uppercase()} SPENDING",
                style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
            )
            Text(
                "${"$%,.2f".format(if (hasBudgets) budgetedSpent else thisMonthExpenses)}",
                style = MaterialTheme.typography.displaySmall, color = OnSurface, fontWeight = FontWeight.Bold
            )
            if (hasBudgets) {
                val progress = (budgetedSpent / totalBudget).coerceIn(0.0, 1.0).toFloat()
                val isOver = budgetedSpent >= totalBudget
                Text(
                    "of ${"$%,.2f".format(totalBudget)} total budget",
                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (isOver) Tertiary else Primary,
                    trackColor = SurfaceContainerHighest
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${"%.0f".format(progress * 100)}% used",
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                    )
                    if (isOver) {
                        Text(
                            "${"$%,.2f".format(budgetedSpent - totalBudget)} over budget",
                            style = MaterialTheme.typography.labelSmall, color = Tertiary, fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "${"$%,.2f".format(totalBudget - budgetedSpent)} remaining",
                            style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                savingsRate?.let { rate ->
                    Text(
                        "Savings rate: ${"%.1f".format(rate)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rate >= 0) Primary else Tertiary
                    )
                }
            }
        }
    }

    // Income / Expense summary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LedgerCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("+${"$%,.2f".format(thisMonthIncome)}", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                Text("this month", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
        LedgerCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("-${"$%,.2f".format(thisMonthExpenses)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                Text("this month", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
    }

    // Budget alerts
    if (hasBudgets) {
        val overBudget = budgets.filter { b ->
            val catName = categoryById[b.categoryId]?.name ?: return@filter false
            (spentByCategory[catName] ?: 0.0) >= b.limitAmount
        }
        if (overBudget.isNotEmpty()) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = Tertiary, modifier = Modifier.size(18.dp))
                        Text("Budget Alerts", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    }
                    overBudget.forEach { b ->
                        val cat = categoryById[b.categoryId]
                        val spent = spentByCategory[cat?.name ?: ""] ?: 0.0
                        val over = spent - b.limitAmount
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat?.name ?: b.categoryId, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            Text(
                                "${"$%,.2f".format(over)} over",
                                style = MaterialTheme.typography.bodySmall, color = Tertiary, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Recent transactions
    if (recentTxs.isNotEmpty()) {
        Text("Recent This Month", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
        val dayFmt = DateTimeFormatter.ofPattern("MMM d")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                recentTxs.forEachIndexed { idx, tx ->
                    val date = runCatching { LocalDate.parse(tx.createdAt.take(10)).format(dayFmt) }.getOrElse { "" }
                    TransactionRow(
                        title = tx.title,
                        subtitle = "$date · ${tx.category}",
                        amount = "${if (tx.isIncome) "+" else "-"}${"$%,.2f".format(tx.amount)}",
                        isIncome = tx.isIncome,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    if (idx < recentTxs.lastIndex)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }

    // Link to full statistics
    LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.MonthlyStatistics.route) }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BarChart, null, tint = Primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Monthly Statistics & Reports", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Text("Detailed breakdown, comparisons", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant)
        }
    }
}

// ── Tab 2: Fixed Costs ────────────────────────────────────────────────────────

@Composable
private fun FixedCostsTab(recurringExpenses: List<uniffi.ledger.RecurringTransaction>) {
    fun toMonthly(amount: Double, frequency: String): Double = when (frequency.lowercase()) {
        "daily"     -> amount * 30.44
        "weekly"    -> amount * 4.33
        "biweekly"  -> amount * 2.17
        "monthly"   -> amount
        "quarterly" -> amount / 3.0
        "yearly"    -> amount / 12.0
        else        -> amount
    }

    val totalMonthly = remember(recurringExpenses) {
        recurringExpenses.sumOf { toMonthly(it.amount, it.frequency) }
    }

    if (recurringExpenses.isEmpty()) {
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No recurring expenses set up.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    Text("Add recurring transactions to track fixed costs.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        return
    }

    LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TOTAL FIXED / MONTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("${"$%,.2f".format(totalMonthly)}", style = MaterialTheme.typography.displaySmall, color = OnSurface, fontWeight = FontWeight.Bold)
            Text("${recurringExpenses.size} recurring expense${if (recurringExpenses.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
    }

    Text("Recurring Expenses", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            recurringExpenses
                .sortedByDescending { toMonthly(it.amount, it.frequency) }
                .forEachIndexed { idx, r ->
                    val monthly = toMonthly(r.amount, r.frequency)
                    val nextDate = runCatching {
                        LocalDate.parse(r.nextDate.take(10)).format(DateTimeFormatter.ofPattern("MMM d"))
                    }.getOrElse { r.nextDate.take(10) }

                    Surface(
                        onClick = {},
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(r.title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                Text(
                                    "${r.frequency.replaceFirstChar { it.uppercase() }} · Next: $nextDate · ${r.category}",
                                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "-${"$%,.2f".format(r.amount)}",
                                    style = MaterialTheme.typography.bodyMedium, color = Tertiary, fontWeight = FontWeight.SemiBold
                                )
                                if (r.frequency.lowercase() != "monthly") {
                                    Text(
                                        "≈${"$%,.2f".format(monthly)}/mo",
                                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (idx < recurringExpenses.lastIndex)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                }
        }
    }
}

