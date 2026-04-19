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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class NotifItem(
    val key: String,
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val body: String,
    val isAlert: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel(),
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    goalViewModel: GoalViewModel = hiltViewModel(),
    recurringViewModel: RecurringViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val txState        by txViewModel.state.collectAsStateWithLifecycle()
    val budgetState    by budgetViewModel.state.collectAsStateWithLifecycle()
    val catState       by categoryViewModel.state.collectAsStateWithLifecycle()
    val goalState      by goalViewModel.state.collectAsStateWithLifecycle()
    val recurringState by recurringViewModel.state.collectAsStateWithLifecycle()
    val walletState    by walletViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        txViewModel.loadAll()
        budgetViewModel.load()
        categoryViewModel.load()
        goalViewModel.load()
        recurringViewModel.load()
        walletViewModel.load()
    }

    val today = remember { LocalDate.now() }

    val thisMonthTxs = remember(txState.transactions, today) {
        txState.transactions.filter {
            runCatching {
                val d = LocalDate.parse(it.createdAt.take(10))
                d.year == today.year && d.monthValue == today.monthValue
            }.getOrDefault(false)
        }
    }

    val spentByCategory = remember(thisMonthTxs) {
        thisMonthTxs.filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
    }

    val notifications = remember(
        budgetState.budgets, catState.categories, goalState.goals,
        recurringState.recurring, walletState.wallets,
        txState.transactions, spentByCategory, today
    ) {
        val list = mutableListOf<NotifItem>()

        // ── Budget alerts ─────────────────────────────────────────────────────
        budgetState.budgets.forEach { budget ->
            val catName = catState.categories.find { it.id == budget.categoryId }?.name ?: return@forEach
            val spent = spentByCategory[catName] ?: 0.0
            val pct = if (budget.limitAmount > 0) spent / budget.limitAmount else 0.0
            val alertFraction = budget.alertThreshold / 100.0
            when {
                pct >= 1.0 -> list += NotifItem(
                    key = "budget_over_${budget.id}",
                    icon = Icons.Filled.Warning,
                    iconColor = Color(0xFF920009),
                    title = "Over budget — $catName",
                    body = "${"$%,.2f".format(spent)} spent of ${"$%,.2f".format(budget.limitAmount)} limit (${"%.0f".format(pct * 100)}%).",
                    isAlert = true
                )
                pct >= alertFraction -> list += NotifItem(
                    key = "budget_near_${budget.id}",
                    icon = Icons.Filled.NotificationsActive,
                    iconColor = Color(0xFFE65100),
                    title = "Budget alert — $catName",
                    body = "${"%.0f".format(pct * 100)}% of ${"$%,.2f".format(budget.limitAmount)} used · ${"$%,.2f".format(budget.limitAmount - spent)} remaining.",
                    isAlert = true
                )
            }
        }

        // ── Low wallet balance ────────────────────────────────────────────────
        walletState.wallets.filter { it.balance in 0.01..99.99 }.forEach { wallet ->
            list += NotifItem(
                key = "low_balance_${wallet.id}",
                icon = Icons.Filled.AccountBalanceWallet,
                iconColor = Tertiary,
                title = "Low balance — ${wallet.name}",
                body = "${wallet.name} has only ${"$%,.2f".format(wallet.balance)} remaining.",
                isAlert = true
            )
        }

        // ── Recurring due today or tomorrow ───────────────────────────────────
        recurringState.recurring.forEach { r ->
            val daysAway = runCatching {
                ChronoUnit.DAYS.between(today, LocalDate.parse(r.nextDate.take(10)))
            }.getOrDefault(-1L)
            if (daysAway in 0L..1L) {
                val whenStr = if (daysAway == 0L) "today" else "tomorrow"
                list += NotifItem(
                    key = "recurring_due_${r.id}",
                    icon = if (r.isIncome) Icons.Filled.TrendingUp else Icons.Filled.Schedule,
                    iconColor = if (r.isIncome) Primary else Color(0xFF1565C0),
                    title = "${r.title} due $whenStr",
                    body = "${if (r.isIncome) "+" else "-"}${"$%,.2f".format(r.amount)} · ${r.category}",
                    isAlert = true
                )
            }
        }

        // ── Spending exceeds income ───────────────────────────────────────────
        val thisMonthIncome   = thisMonthTxs.filter {  it.isIncome }.sumOf { it.amount }
        val thisMonthExpenses = thisMonthTxs.filter { !it.isIncome }.sumOf { it.amount }
        if (thisMonthIncome > 0 && thisMonthExpenses > thisMonthIncome) {
            list += NotifItem(
                key = "spending_over_income",
                icon = Icons.Filled.TrendingDown,
                iconColor = Tertiary,
                title = "Spending exceeds income",
                body = "Expenses are ${"$%,.2f".format(thisMonthExpenses - thisMonthIncome)} over income this month.",
                isAlert = true
            )
        }

        // ── End-of-month warning when budgets are over ────────────────────────
        val daysLeft = ChronoUnit.DAYS.between(today, today.withDayOfMonth(today.lengthOfMonth()))
        if (daysLeft in 1L..5L) {
            val overCount = budgetState.budgets.count { b ->
                val cn = catState.categories.find { it.id == b.categoryId }?.name ?: return@count false
                (spentByCategory[cn] ?: 0.0) > b.limitAmount
            }
            if (overCount > 0) {
                list += NotifItem(
                    key = "month_end_warning",
                    icon = Icons.Filled.CalendarToday,
                    iconColor = Color(0xFFE65100),
                    title = "$daysLeft days left this month",
                    body = "$overCount budget${if (overCount > 1) "s" else ""} over limit with $daysLeft days remaining.",
                    isAlert = true
                )
            }
        }

        // ── Last month summary — only if savings rate hit ≥20% ───────────────
        val prevMonth = today.minusMonths(1)
        val prevMonthTxs = txState.transactions.filter {
            runCatching {
                val d = LocalDate.parse(it.createdAt.take(10))
                d.year == prevMonth.year && d.monthValue == prevMonth.monthValue
            }.getOrDefault(false)
        }
        val prevIncome   = prevMonthTxs.filter {  it.isIncome }.sumOf { it.amount }
        val prevExpenses = prevMonthTxs.filter { !it.isIncome }.sumOf { it.amount }
        val prevRate     = if (prevIncome > 0) (prevIncome - prevExpenses) / prevIncome * 100 else 0.0
        if (prevRate >= 20.0 && prevIncome > 0) {
            val monthName = prevMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            val isBest = (1..6).all { off ->
                if (off.toLong() == 1L) return@all true
                val m = today.minusMonths(off.toLong())
                val inc = txState.transactions.filter { tx ->
                    runCatching {
                        val d = LocalDate.parse(tx.createdAt.take(10))
                        d.year == m.year && d.monthValue == m.monthValue && tx.isIncome
                    }.getOrDefault(false)
                }.sumOf { it.amount }
                val exp = txState.transactions.filter { tx ->
                    runCatching {
                        val d = LocalDate.parse(tx.createdAt.take(10))
                        d.year == m.year && d.monthValue == m.monthValue && !tx.isIncome
                    }.getOrDefault(false)
                }.sumOf { it.amount }
                val r = if (inc > 0) (inc - exp) / inc * 100 else 0.0
                prevRate >= r
            }
            list += NotifItem(
                key = "monthly_summary_${prevMonth.year}_${prevMonth.monthValue}",
                icon = if (isBest) Icons.Filled.EmojiEvents else Icons.Filled.CheckCircle,
                iconColor = if (isBest) Color(0xFFF9A825) else Primary,
                title = if (isBest) "$monthName — best month in 6 months!" else "$monthName — great savings month",
                body = "${"%.1f".format(prevRate)}% savings rate · +${"$%,.0f".format(prevIncome)} income · -${"$%,.0f".format(prevExpenses)} expenses.",
                isAlert = false
            )
        }

        // ── Goal milestones (insights) ────────────────────────────────────────
        goalState.goals.forEach { goal ->
            val pct = if (goal.targetAmount > 0) goal.currentAmount / goal.targetAmount else 0.0
            val milestone = when {
                pct >= 1.0  -> 100
                pct >= 0.75 -> 75
                pct >= 0.5  -> 50
                pct >= 0.25 -> 25
                else        -> null
            }
            if (milestone != null) {
                list += NotifItem(
                    key = "goal_${goal.id}_$milestone",
                    icon = if (milestone == 100) Icons.Filled.EmojiEvents else Icons.Filled.Savings,
                    iconColor = if (milestone == 100) Color(0xFFF9A825) else Color(0xFF1565C0),
                    title = if (milestone == 100) "${goal.name} — Goal reached!" else "${goal.name} — $milestone% funded",
                    body = "${"$%,.2f".format(goal.currentAmount)} saved of ${"$%,.2f".format(goal.targetAmount)} target.",
                    isAlert = false
                )
            }
        }

        // ── Strong savings rate (insight) ─────────────────────────────────────
        if (thisMonthIncome > 0) {
            val rate = (thisMonthIncome - thisMonthExpenses) / thisMonthIncome * 100
            if (rate >= 30) {
                list += NotifItem(
                    key = "savings_rate_good",
                    icon = Icons.Filled.TrendingUp,
                    iconColor = Primary,
                    title = "Strong savings month",
                    body = "You're saving ${"%.1f".format(rate)}% of your income this month — above the 20% target.",
                    isAlert = false
                )
            }
        }

        // ── Largest expense vs recent average (insight) ───────────────────────
        val largest = thisMonthTxs.filter { !it.isIncome }.maxByOrNull { it.amount }
        if (largest != null && largest.amount > 200.0) {
            val avg3MonthLargest = (1..3).mapNotNull { off ->
                val m = today.minusMonths(off.toLong())
                txState.transactions.filter { tx ->
                    !tx.isIncome && runCatching {
                        val d = LocalDate.parse(tx.createdAt.take(10))
                        d.year == m.year && d.monthValue == m.monthValue
                    }.getOrDefault(false)
                }.maxByOrNull { it.amount }?.amount
            }.let { if (it.isEmpty()) null else it.average() }

            if (avg3MonthLargest != null && avg3MonthLargest > 0 && largest.amount > avg3MonthLargest * 1.5) {
                list += NotifItem(
                    key = "large_expense_${largest.id}",
                    icon = Icons.Filled.Receipt,
                    iconColor = Color(0xFFE65100),
                    title = "Large expense — ${largest.title}",
                    body = "${"$%,.2f".format(largest.amount)} in ${largest.category} · ${"%.0f".format(largest.amount / avg3MonthLargest * 100 - 100)}% above your usual largest expense.",
                    isAlert = false
                )
            }
        }

        list
    }

    // In-memory dismiss state for alerts
    var dismissedKeys by remember { mutableStateOf(setOf<String>()) }

    val activeAlerts   = notifications.filter { it.isAlert && it.key !in dismissedKeys }
    val insights       = notifications.filter { !it.isAlert }
    val dismissedItems = notifications.filter { it.isAlert && it.key in dismissedKeys }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeAlerts.isNotEmpty()) {
                        TextButton(onClick = { dismissedKeys = dismissedKeys + activeAlerts.map { it.key } }) {
                            Text("Dismiss all", color = Primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        if (notifications.isEmpty() && !txState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(56.dp))
                    Text("All caught up!", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text("No alerts or insights right now.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeAlerts.isNotEmpty()) {
                    Text("Alerts", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    activeAlerts.forEach { notif ->
                        NotifCard(notif, unread = true) { dismissedKeys = dismissedKeys + notif.key }
                    }
                }

                if (insights.isNotEmpty()) {
                    if (activeAlerts.isNotEmpty()) Spacer(Modifier.height(4.dp))
                    Text("Insights", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    insights.forEach { notif ->
                        NotifCard(notif, unread = false, onClick = null)
                    }
                }

                if (dismissedItems.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Dismissed", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    dismissedItems.forEach { notif ->
                        NotifCard(notif, unread = false, onClick = null)
                    }
                }

                if (notifications.isEmpty() && txState.isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifCard(notif: NotifItem, unread: Boolean, onClick: (() -> Unit)?) {
    LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(notif.iconColor.copy(alpha = if (unread) 0.15f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    notif.icon, contentDescription = null,
                    tint = notif.iconColor.copy(alpha = if (unread) 1f else 0.55f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    notif.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (unread) OnSurface else OnSurfaceVariant,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
                )
                Text(notif.body, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                if (unread && onClick != null) {
                    Text("Tap to dismiss", style = MaterialTheme.typography.labelSmall, color = Primary.copy(alpha = 0.7f))
                }
            }
            if (unread) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Primary).align(Alignment.Top))
            }
        }
    }
}
