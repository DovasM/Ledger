package com.ledger.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.viewmodel.*
import uniffi.ledger.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.*

// ── Shared data holders ───────────────────────────────────────────────────────

private data class CfEvent(val title: String, val amount: Double, val isIncome: Boolean, val date: LocalDate)
private data class CfMonthBar(val label: String, val income: Float, val expenses: Float)
private data class CfSlice(val name: String, val amount: Float, val color: Color)
private data class CfBudgetRow(val label: String, val spent: Double, val limit: Double, val color: Color)
private data class CfUnusual(val category: String, val thisMonth: Double, val avg3: Double)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowForecastScreen(
    navController: NavController,
    txViewModel: TransactionViewModel      = hiltViewModel(),
    walletViewModel: WalletViewModel       = hiltViewModel(),
    recurringViewModel: RecurringViewModel = hiltViewModel(),
    budgetViewModel: BudgetViewModel       = hiltViewModel(),
    categoryViewModel: CategoryViewModel   = hiltViewModel(),
    debtViewModel: DebtViewModel           = hiltViewModel()
) {
    val txState        by txViewModel.state.collectAsStateWithLifecycle()
    val walletState    by walletViewModel.state.collectAsStateWithLifecycle()
    val recurringState by recurringViewModel.state.collectAsStateWithLifecycle()
    val budgetState    by budgetViewModel.state.collectAsStateWithLifecycle()
    val categoryState  by categoryViewModel.state.collectAsStateWithLifecycle()
    val debtState      by debtViewModel.state.collectAsStateWithLifecycle()

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) {
        txViewModel.loadAll()
        walletViewModel.load()
        recurringViewModel.load()
        budgetViewModel.load()
        categoryViewModel.load()
        debtViewModel.load()
    }

    val today        = LocalDate.now()
    val totalBalance = walletState.wallets.sumOf { it.balance }

    fun inMonth(s: String, y: Int, m: Int) = runCatching {
        val d = LocalDate.parse(s.take(10)); d.year == y && d.monthValue == m
    }.getOrDefault(false)

    // ── This month ────────────────────────────────────────────────────────────
    val thisMonthTxs      = txState.transactions.filter { inMonth(it.createdAt, today.year, today.monthValue) }
    val thisMonthIncome   = thisMonthTxs.filter {  it.isIncome }.sumOf { it.amount }
    val thisMonthExpenses = thisMonthTxs.filter { !it.isIncome }.sumOf { it.amount }
    val thisMonthNet      = thisMonthIncome - thisMonthExpenses
    val daysElapsed       = today.dayOfMonth.coerceAtLeast(1)

    // ── Scheduled events next 30 days ─────────────────────────────────────────
    val horizon = today.plusDays(30)
    val scheduledEvents: List<CfEvent> = buildList {
        for (r in recurringState.recurring) {
            var next = runCatching { LocalDate.parse(r.nextDate.take(10)) }.getOrNull() ?: continue
            while (!next.isAfter(horizon)) {
                if (!next.isBefore(today)) add(CfEvent(r.title, r.amount, r.isIncome, next))
                next = cfAdvanceDate(next, r.frequency)
            }
        }
    }.sortedBy { it.date }

    // ── 30-day projection ─────────────────────────────────────────────────────
    // Variable burn rate from 3-month rolling average of non-recurring expenses.
    // Recurring amounts are excluded to avoid double-counting — they're applied
    // explicitly per day below. If ≥7 days of current month data exist, blend in
    // 30% current month to let recent habits nudge the projection.
    val approxMonthlyRecurring = recurringState.recurring.filter { !it.isIncome }.sumOf { it.amount }
    fun variableExpensesForMonth(offset: Int): Double {
        val m = today.minusMonths(offset.toLong())
        val total = txState.transactions
            .filter { !it.isIncome && inMonth(it.createdAt, m.year, m.monthValue) }
            .sumOf { it.amount }
        return (total - approxMonthlyRecurring).coerceAtLeast(0.0)
    }
    val rolling3MonthAvgDaily = (1..3)
        .map { variableExpensesForMonth(it) / today.minusMonths(it.toLong()).lengthOfMonth() }
        .average()
    val currentMonthVariableDaily = if (daysElapsed >= 7)
        (thisMonthExpenses - approxMonthlyRecurring * daysElapsed / 30.0).coerceAtLeast(0.0) / daysElapsed
    else null
    val dailyBurnRate     = thisMonthExpenses / daysElapsed
    val variableDailyBurn = if (currentMonthVariableDaily != null)
        rolling3MonthAvgDaily * 0.7 + currentMonthVariableDaily * 0.3
    else
        rolling3MonthAvgDaily

    val eventsByDay   = scheduledEvents.groupBy { it.date }
    val projectedBalances: List<Float> = buildList {
        var bal = totalBalance
        for (i in 0..30) {
            // Day 0 is today — balance already reflects actual spending, so burn starts tomorrow
            if (i > 0) bal -= variableDailyBurn
            eventsByDay[today.plusDays(i.toLong())]?.forEach { e ->
                bal += if (e.isIncome) e.amount else -e.amount
            }
            add(bal.toFloat())
        }
    }
    val projectedEnd   = projectedBalances.last().toDouble()
    val projectedDelta = projectedEnd - totalBalance

    // ── Monthly bars last 6 months ────────────────────────────────────────────
    val monthBars: List<CfMonthBar> = (5 downTo 0).map { offset ->
        val m = today.minusMonths(offset.toLong())
        val txs = txState.transactions.filter { inMonth(it.createdAt, m.year, m.monthValue) }
        CfMonthBar(
            label    = m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            income   = txs.filter {  it.isIncome }.sumOf { it.amount }.toFloat(),
            expenses = txs.filter { !it.isIncome }.sumOf { it.amount }.toFloat()
        )
    }

    // ── Expense donut slices ───────────────────────────────────────────────────
    val spentByCategory = thisMonthTxs.filter { !it.isIncome }
        .groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount }.toFloat() }
    val donutPalette = listOf(
        Color(0xFF5C6BC0), Color(0xFFEF5350), Color(0xFF26A69A), Color(0xFFFFA726),
        Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFF29B6F6), Color(0xFFFF7043)
    )
    val catSlices: List<CfSlice> = spentByCategory.entries
        .sortedByDescending { it.value }.take(8)
        .mapIndexed { i, (name, amt) ->
            val cat   = categoryState.categories.find { it.name.equals(name, ignoreCase = true) }
            val color = cat?.let { runCatching { colorHexToColor(it.colorHex) }.getOrNull() } ?: donutPalette[i % donutPalette.size]
            CfSlice(name.ifBlank { "Other" }, amt, color)
        }

    // ── Budget rows ───────────────────────────────────────────────────────────
    val budgetRows: List<CfBudgetRow> = budgetState.budgets.map { b ->
        val cat   = categoryState.categories.find { it.id == b.categoryId }
        val spent = (spentByCategory[cat?.name] ?: 0f).toDouble()
        val color = cat?.let { runCatching { colorHexToColor(it.colorHex) }.getOrNull() } ?: Primary
        CfBudgetRow(cat?.name ?: "Budget", spent, b.limitAmount, color)
    }

    // ── Insights data ─────────────────────────────────────────────────────────
    val savingsRate   = if (thisMonthIncome > 0) (thisMonthNet / thisMonthIncome * 100).coerceIn(-999.0, 999.0) else 0.0
    val remainingDays = (today.lengthOfMonth() - today.dayOfMonth).coerceAtLeast(0)
    val endOfMonth    = today.withDayOfMonth(today.lengthOfMonth())
    // Known fixed costs still due this month from recurring transactions
    val pendingRecurringExp = recurringState.recurring.filter { !it.isIncome }.sumOf { r ->
        var next = runCatching { LocalDate.parse(r.nextDate.take(10)) }.getOrNull() ?: return@sumOf 0.0
        var total = 0.0
        while (!next.isAfter(endOfMonth)) {
            if (next.isAfter(today)) total += r.amount
            next = cfAdvanceDate(next, r.frequency)
        }
        total
    }
    // Predicted = actual so far + known recurring + variable discretionary extrapolation
    val predictedExp  = thisMonthExpenses + pendingRecurringExp + dailyBurnRate * remainingDays

    val prevM              = today.minusMonths(1)
    val prevMonthExpenses  = txState.transactions.filter { inMonth(it.createdAt, prevM.year, prevM.monthValue) && !it.isIncome }.sumOf { it.amount }
    val expenseMoM         = if (prevMonthExpenses > 0) ((thisMonthExpenses - prevMonthExpenses) / prevMonthExpenses * 100) else 0.0

    val avgMonthlyExpenses = (1..6).mapNotNull { off ->
        val m   = today.minusMonths(off.toLong())
        val sum = txState.transactions.filter { inMonth(it.createdAt, m.year, m.monthValue) && !it.isIncome }.sumOf { it.amount }
        sum.takeIf { it > 0 }
    }.let { if (it.isEmpty()) thisMonthExpenses else it.average() }
    val runway = if (avgMonthlyExpenses > 0) totalBalance / avgMonthlyExpenses else 0.0

    val incomeHistory: List<Float> = (5 downTo 0).map { off ->
        val m = today.minusMonths(off.toLong())
        txState.transactions.filter { inMonth(it.createdAt, m.year, m.monthValue) && it.isIncome }.sumOf { it.amount }.toFloat()
    }
    val incomeMean   = incomeHistory.average().toFloat()
    val incomeStdDev = if (incomeMean > 0) sqrt(incomeHistory.map { (it - incomeMean).pow(2) }.average()).toFloat() else 0f
    val incomeCoV    = if (incomeMean > 0) incomeStdDev / incomeMean else 0f

    val breakEvenDay = if (dailyBurnRate > 0) (thisMonthIncome / dailyBurnRate).toInt() else today.lengthOfMonth()

    val last3AvgByCategory = mutableMapOf<String, Double>()
    for (off in 1..3) {
        val m = today.minusMonths(off.toLong())
        txState.transactions.filter { inMonth(it.createdAt, m.year, m.monthValue) && !it.isIncome }
            .groupBy { it.category }
            .forEach { (cat, txs) ->
                last3AvgByCategory[cat] = (last3AvgByCategory[cat] ?: 0.0) + txs.sumOf { it.amount } / 3.0
            }
    }
    val unusualSpending: List<CfUnusual> = spentByCategory
        .filter { (cat, amt) ->
            val avg = last3AvgByCategory[cat] ?: 0.0
            avg > 15.0 && amt > avg * 1.35 && amt > 20.0
        }
        .map { (cat, amt) -> CfUnusual(cat, amt.toDouble(), last3AvgByCategory[cat] ?: 1.0) }
        .sortedByDescending { it.thisMonth / it.avg3 }

    val largestUpcoming      = scheduledEvents.filter { !it.isIncome }.maxByOrNull { it.amount }
    val totalMonthlyDebt     = debtState.debts.sumOf { it.monthlyPayment }
    val debtRatio            = if (thisMonthIncome > 0) totalMonthlyDebt / thisMonthIncome * 100 else 0.0
    val payYourselfFirst     = projectedDelta.coerceAtLeast(0.0)

    // ── Tabs ──────────────────────────────────────────────────────────────────
    val tabs = listOf("Overview", "Calendar", "Analytics", "Insights")
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cash Flow", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = SurfaceContainerLow, contentColor = Primary) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) })
                }
            }
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    0 -> OverviewTab(today, thisMonthIncome, thisMonthExpenses, thisMonthNet, projectedBalances, projectedDelta, scheduledEvents, payYourselfFirst, largestUpcoming, variableDailyBurn)
                    1 -> CalendarTab(today, scheduledEvents, txState.transactions)
                    2 -> AnalyticsTab(monthBars, catSlices, budgetRows, totalMonthlyDebt, debtRatio)
                    3 -> InsightsTab(savingsRate, dailyBurnRate, daysElapsed, expenseMoM, incomeHistory, incomeCoV, runway, breakEvenDay, today, predictedExp, thisMonthIncome, unusualSpending)
                    else -> {}
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 — Overview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(
    today: LocalDate,
    income: Double, expenses: Double, net: Double,
    projectedBalances: List<Float>, projectedDelta: Double,
    scheduledEvents: List<CfEvent>,
    payYourselfFirst: Double,
    largestUpcoming: CfEvent?,
    variableDailyBurn: Double
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Monthly summary card
        LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase() + " SUMMARY",
                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SummaryCol("INCOME",   "+${"$%,.0f".format(income)}", Primary)
                    VerticalDivider(modifier = Modifier.height(36.dp), color = OutlineVariant.copy(alpha = 0.4f))
                    SummaryCol("EXPENSES", "-${"$%,.0f".format(expenses)}", Tertiary)
                    VerticalDivider(modifier = Modifier.height(36.dp), color = OutlineVariant.copy(alpha = 0.4f))
                    SummaryCol("NET", "${if (net >= 0) "+" else ""}${"$%,.0f".format(net)}", if (net >= 0) Primary else Tertiary)
                }
            }
        }

        // 30-day projection
        SectionHeader("30-Day Projection", "Where your balance is headed if all recurring transactions post on schedule")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Projected balance", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.0f".format(projectedBalances.lastOrNull()?.toDouble() ?: 0.0)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (projectedDelta >= 0) Primary else Tertiary,
                            fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = (if (projectedDelta >= 0) Primary else Tertiary).copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (projectedDelta >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown, null,
                                tint = if (projectedDelta >= 0) Primary else Tertiary, modifier = Modifier.size(16.dp))
                            Text("${if (projectedDelta >= 0) "+" else ""}${"$%,.0f".format(projectedDelta)}",
                                style = MaterialTheme.typography.labelMedium, color = if (projectedDelta >= 0) Primary else Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                CfProjectionChart(projectedBalances, projectedDelta >= 0)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "~${"$%.0f".format(variableDailyBurn)}/day variable · ${scheduledEvents.size} scheduled",
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                    )
                    Text("Hold & drag to inspect", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.55f))
                }
                Text(
                    "Slope = 3-month avg of non-recurring expenses. Steps = scheduled transactions.",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // Pay yourself first
        if (payYourselfFirst > 1) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Savings, null, tint = Primary, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Pay Yourself First", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("Move up to ${"$%,.0f".format(payYourselfFirst)} to savings",
                            style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("Without going negative this month", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }
        }

        // Largest upcoming expense
        if (largestUpcoming != null) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Tertiary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.NotificationImportant, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Largest Upcoming Expense", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(largestUpcoming.title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text(largestUpcoming.date.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Text("-${"$%,.2f".format(largestUpcoming.amount)}", style = MaterialTheme.typography.titleSmall, color = Tertiary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Upcoming scheduled list
        if (scheduledEvents.isNotEmpty()) {
            SectionHeader("Upcoming (30 days)", "Your next scheduled income and expenses from recurring transactions")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    scheduledEvents.take(10).forEachIndexed { idx, event ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background((if (event.isIncome) Primary else Tertiary).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center) {
                                Icon(if (event.isIncome) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown, null,
                                    tint = if (event.isIncome) Primary else Tertiary, modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Medium)
                                Text(event.date.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                            Text("${if (event.isIncome) "+" else "-"}${"$%.2f".format(event.amount)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (event.isIncome) Primary else Tertiary, fontWeight = FontWeight.SemiBold)
                        }
                        if (idx < scheduledEvents.take(10).lastIndex)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }
        } else {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No recurring transactions scheduled in the next 30 days.",
                        style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 — Calendar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarTab(today: LocalDate, scheduledEvents: List<CfEvent>, allTxs: List<Transaction>) {
    var calendarMonth by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var selectedDay   by remember { mutableStateOf<LocalDate?>(null) }

    val scheduledByDay = scheduledEvents
        .filter { it.date.year == calendarMonth.year && it.date.monthValue == calendarMonth.monthValue }
        .groupBy { it.date.dayOfMonth }

    val txByDay = allTxs.filter {
        runCatching {
            val d = LocalDate.parse(it.createdAt.take(10))
            d.year == calendarMonth.year && d.monthValue == calendarMonth.monthValue
        }.getOrDefault(false)
    }.groupBy { LocalDate.parse(it.createdAt.take(10)).dayOfMonth }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Month nav
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { calendarMonth = calendarMonth.minusMonths(1); selectedDay = null }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, null, tint = OnSurface)
                    }
                    Text(
                        calendarMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${calendarMonth.year}",
                        style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold
                    )
                    val canGoForward = calendarMonth.isBefore(today.withDayOfMonth(1).plusMonths(2))
                    IconButton(onClick = { if (canGoForward) { calendarMonth = calendarMonth.plusMonths(1); selectedDay = null } }) {
                        Icon(Icons.Filled.KeyboardArrowRight, null, tint = if (canGoForward) OnSurface else OnSurfaceVariant.copy(alpha = 0.4f))
                    }
                }

                // Day headers
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { h ->
                        Text(h, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }

                // Grid
                val offset      = (calendarMonth.dayOfWeek.value - 1).coerceIn(0, 6)
                val daysInMonth = calendarMonth.lengthOfMonth()
                val rows        = ((offset + daysInMonth) + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val day  = row * 7 + col - offset + 1
                                val date = if (day in 1..daysInMonth) calendarMonth.withDayOfMonth(day) else null
                                val isToday    = date == today
                                val isSelected = date == selectedDay
                                val hasSched   = date != null && scheduledByDay.containsKey(day)
                                val hasIncome  = hasSched && scheduledByDay[day]!!.any { it.isIncome }
                                val hasExpense = hasSched && scheduledByDay[day]!!.any { !it.isIncome }
                                val hasTx      = date != null && txByDay.containsKey(day)

                                Box(
                                    modifier = Modifier
                                        .weight(1f).aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(when {
                                            isSelected -> Primary.copy(alpha = 0.20f)
                                            isToday    -> Primary.copy(alpha = 0.08f)
                                            else       -> Color.Transparent
                                        })
                                        .then(if (date != null) Modifier.pointerInput(date) {
                                            detectTapGestures { selectedDay = if (selectedDay == date) null else date }
                                        } else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text("$day",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected || isToday) Primary else OnSurface,
                                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal)
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(5.dp)) {
                                                if (hasIncome)  Box(Modifier.size(4.dp).clip(CircleShape).background(Primary))
                                                if (hasExpense) Box(Modifier.size(4.dp).clip(CircleShape).background(Tertiary))
                                                if (hasTx && !hasSched) Box(Modifier.size(4.dp).clip(CircleShape).background(OnSurfaceVariant.copy(alpha = 0.35f)))
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

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LegendDot(Primary, "Scheduled income")
            LegendDot(Tertiary, "Scheduled expense")
            LegendDot(OnSurfaceVariant.copy(alpha = 0.35f), "Transactions")
        }
        Text("Tap any day to see scheduled and actual transactions for that date",
            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)

        // Selected day detail
        if (selectedDay != null) {
            val day = selectedDay!!
            val dayScheduled = scheduledEvents.filter { it.date == day }
            val dayTxs       = allTxs.filter { runCatching { LocalDate.parse(it.createdAt.take(10)) == day }.getOrDefault(false) }

            Text(day.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    if (dayScheduled.isEmpty() && dayTxs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No events", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                    dayScheduled.forEachIndexed { idx, ev ->
                        DayEventRow(ev.title, "${if (ev.isIncome) "+" else "-"}${"$%.2f".format(ev.amount)}", "Scheduled",
                            if (ev.isIncome) Primary else Tertiary)
                        if (idx < dayScheduled.lastIndex || dayTxs.isNotEmpty())
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                    dayTxs.forEachIndexed { idx, tx ->
                        DayEventRow(tx.title, "${if (tx.isIncome) "+" else "-"}${"$%.2f".format(tx.amount)}", "Actual",
                            if (tx.isIncome) Primary else Tertiary)
                        if (idx < dayTxs.lastIndex)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 3 — Analytics
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsTab(
    monthBars: List<CfMonthBar>,
    catSlices: List<CfSlice>,
    budgetRows: List<CfBudgetRow>,
    totalMonthlyDebt: Double,
    debtRatio: Double
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionHeader("Monthly Comparison", "Income vs expenses for each of the last 6 months · Tap a bar to see exact figures")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(Primary, "Income"); LegendDot(Tertiary, "Expenses")
                }
                CfInteractiveBarChart(monthBars)
            }
        }

        if (catSlices.isNotEmpty()) {
            SectionHeader("Expense Breakdown", "This month's expenses split by category · Tap a slice to highlight and inspect")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CfDonutChart(catSlices)
                }
            }
        }

        if (budgetRows.isNotEmpty()) {
            SectionHeader("Budget Utilization", "How much of each spending budget you've used so far this month")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    budgetRows.forEach { row ->
                        val pct   = if (row.limit > 0) (row.spent / row.limit).coerceIn(0.0, 1.5).toFloat() else 0f
                        val color = if (pct > 1f) Tertiary else row.color
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(row.label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${"$%.0f".format(row.spent)} / ${"$%.0f".format(row.limit)}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
                                        Text("${"%.0f".format(pct * 100)}%", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            LinearProgressIndicator(progress = { pct.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = color, trackColor = SurfaceContainerHighest)
                        }
                    }
                }
            }
        }

        if (totalMonthlyDebt > 0) {
            SectionHeader("Debt Service", "Your total monthly debt repayments as a share of income")
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Monthly debt payments", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("${"$%,.0f".format(totalMonthlyDebt)}", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = (if (debtRatio > 36) Tertiary else Primary).copy(alpha = 0.1f)) {
                            Text("${"%.1f".format(debtRatio)}% of income",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (debtRatio > 36) Tertiary else Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    LinearProgressIndicator(progress = { (debtRatio / 50.0).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (debtRatio > 36) Tertiary else Primary, trackColor = SurfaceContainerHighest)
                    Text(if (debtRatio <= 36) "Healthy — the 36% rule says total debt payments shouldn't exceed 36% of gross income" else "High — the 36% rule recommends keeping debt payments under 36% of gross income",
                        style = MaterialTheme.typography.bodySmall, color = if (debtRatio <= 36) Primary else Tertiary)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 4 — Insights
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsightsTab(
    savingsRate: Double, dailyBurnRate: Double, daysElapsed: Int,
    expenseMoM: Double, incomeHistory: List<Float>, incomeCoV: Float,
    runway: Double, breakEvenDay: Int, today: LocalDate,
    predictedExp: Double, thisMonthIncome: Double,
    unusualSpending: List<CfUnusual>
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Runway
        val runwayColor = when { runway >= 6 -> Primary; runway >= 3 -> Color(0xFFF57C00); else -> Tertiary }
        SectionHeader("Financial Health", "Key indicators of your financial resilience and spending patterns")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("EMERGENCY RUNWAY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("If your income stopped today, how long your current balance would last at average monthly spending",
                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant.copy(alpha = 0.7f))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${"%.1f".format(runway)}", style = MaterialTheme.typography.displaySmall, color = runwayColor, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("months of expenses covered", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Text(when { runway >= 6 -> "You're well-covered"; runway >= 3 -> "Consider building reserves"; else -> "Low — aim for 3–6 months" },
                            style = MaterialTheme.typography.bodySmall, color = runwayColor)
                    }
                }
                LinearProgressIndicator(progress = { (runway / 12.0).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = runwayColor, trackColor = SurfaceContainerHighest)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0 mo", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("3 mo", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("6 mo", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("12 mo", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }

        // Break-even day
        val daysInMonth      = today.lengthOfMonth()
        val beFraction       = (breakEvenDay.toFloat() / daysInMonth).coerceIn(0f, 1f)
        val todayFraction    = today.dayOfMonth.toFloat() / daysInMonth
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BREAK-EVEN DAY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("The day of the month when your cumulative spending reaches your total monthly income at the current daily rate",
                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant.copy(alpha = 0.7f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (breakEvenDay <= daysInMonth) "Day $breakEvenDay" else "> $daysInMonth",
                        style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                    Text(when {
                        breakEvenDay > daysInMonth -> "Income covers full month"
                        today.dayOfMonth < breakEvenDay -> "${breakEvenDay - today.dayOfMonth} days away"
                        else -> "Already passed today"
                    }, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                // Bar with today marker and break-even marker
                val beColor = Primary
                val todayColor = OnSurfaceVariant
                Box(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width; val h = size.height; val r = 7.dp.toPx()
                        // Track
                        drawRoundRect(color = SurfaceContainerHighest.copy(alpha = 0.6f), cornerRadius = CornerRadius(r), size = Size(w, h))
                        // Break-even fill
                        drawRoundRect(color = beColor.copy(alpha = 0.6f), cornerRadius = CornerRadius(r), size = Size(w * beFraction, h))
                        // Today line
                        val todayX = (todayFraction * w).coerceIn(2f, w - 2f)
                        drawRect(color = todayColor, topLeft = Offset(todayX - 1.5f, 0f), size = Size(3f, h))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Primary.copy(alpha = 0.6f)))
                        Text("Break-even", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.width(3.dp).height(8.dp).background(OnSurfaceVariant))
                        Text("Today (day ${today.dayOfMonth})", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }
        }

        // Income stability
        val stabilityColor = when { incomeCoV < 0.1f -> Primary; incomeCoV < 0.25f -> Color(0xFFF57C00); else -> Tertiary }
        SectionHeader("Income Stability", "How consistent your monthly income has been over the last 6 months")
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(when { incomeCoV < 0.1f -> "Stable"; incomeCoV < 0.25f -> "Moderate"; else -> "Variable" },
                            style = MaterialTheme.typography.titleMedium, color = stabilityColor, fontWeight = FontWeight.Bold)
                        Text("Last 6 months income", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = SurfaceContainerHighest) {
                        Text("${(incomeCoV * 100).toInt()}% variation",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    }
                }
                IncomeSparkline(incomeHistory, stabilityColor)
                Text("Stable < 10% variation  ·  Moderate 10–25%  ·  Variable > 25%",
                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.6f))
            }
        }

        // Insight cards
        SectionHeader("This Month", "Metrics calculated from your transactions so far this month")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CfInsightCard(Icons.Filled.AccountBalance, "Savings Rate", "${"%.1f".format(savingsRate)}%",
                when { savingsRate >= 20 -> "Great — aim to stay above 20%"; savingsRate > 0 -> "Target ≥ 20% of income"; else -> "Expenses exceed income" },
                if (savingsRate >= 20) Primary else if (savingsRate >= 0) Color(0xFFF57C00) else Tertiary)

            CfInsightCard(Icons.Filled.Speed, "Daily Burn Rate", "${"$%.2f".format(dailyBurnRate)}/day",
                "Average daily spend from $daysElapsed days of data", Primary)

            CfInsightCard(if (expenseMoM <= 0) Icons.Filled.TrendingDown else Icons.Filled.TrendingUp,
                "Expenses vs Last Month", "${if (expenseMoM >= 0) "+" else ""}${"%.1f".format(expenseMoM)}%",
                if (expenseMoM <= 0) "Spending is down from last month" else "Spending is up from last month",
                if (expenseMoM <= 0) Primary else Tertiary)

            CfInsightCard(Icons.Filled.BarChart, "Month-End Prediction", "${"$%,.0f".format(predictedExp)}",
                if (predictedExp <= thisMonthIncome) "Actual + recurring + variable spend — within income" else "Actual + recurring + variable spend — exceeds income",
                if (predictedExp <= thisMonthIncome) Primary else Tertiary)
        }

        // Unusual spending
        if (unusualSpending.isNotEmpty()) {
            SectionHeader("Unusual Spending", "Categories where you're spending 35%+ above your 3-month average")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                unusualSpending.forEach { spend ->
                    val ratio = spend.thisMonth / spend.avg3
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Tertiary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Warning, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(spend.category, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                                Text("${"%.1f".format(ratio)}× your 3-month average of ${"$%.0f".format(spend.avg3)}",
                                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                LinearProgressIndicator(
                                    progress = { (1f / ratio.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = OnSurfaceVariant.copy(alpha = 0.3f), trackColor = Tertiary.copy(alpha = 0.5f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${"$%.0f".format(spend.thisMonth)}", style = MaterialTheme.typography.titleSmall, color = Tertiary, fontWeight = FontWeight.Bold)
                                Text("avg ${"$%.0f".format(spend.avg3)}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chart composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CfProjectionChart(points: List<Float>, isPositive: Boolean) {
    if (points.size < 2) return
    val lineColor = if (isPositive) Primary else Tertiary
    val minVal    = points.min(); val maxVal = points.max()

    // Interactive scrub
    var inspecting      by remember { mutableStateOf(false) }
    var inspectFraction by remember { mutableStateOf(1f) }
    var canvasW         by remember { mutableStateOf(1f) }
    val inspectIdx   = ((inspectFraction * (points.size - 1)).roundToInt()).coerceIn(0, points.size - 1)
    val inspectValue = points.getOrNull(inspectIdx) ?: 0f

    val tooltipHeight = 28.dp
    Box(modifier = Modifier.fillMaxWidth().height(tooltipHeight)) {
        if (inspecting) {
            Row(modifier = Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = lineColor.copy(alpha = 0.12f)) {
                    Text("Day $inspectIdx", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = lineColor.copy(alpha = 0.15f)) {
                    Text(cfFormatCompact(inspectValue), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = lineColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(90.dp)) {
        androidx.compose.material3.Surface(modifier = Modifier.align(Alignment.TopStart), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), shape = RoundedCornerShape(3.dp)) {
            Text(cfFormatCompact(maxVal), modifier = Modifier.padding(horizontal = 3.dp), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
        androidx.compose.material3.Surface(modifier = Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), shape = RoundedCornerShape(3.dp)) {
            Text(cfFormatCompact(minVal), modifier = Modifier.padding(horizontal = 3.dp), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
        val lc = lineColor
        Canvas(modifier = Modifier.fillMaxSize()
            .onSizeChanged { canvasW = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(points.size) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position ?: continue
                        when (event.type) {
                            PointerEventType.Press -> { inspecting = true; inspectFraction = (pos.x / canvasW).coerceIn(0f, 1f) }
                            PointerEventType.Move -> if (inspecting) { inspectFraction = (pos.x / canvasW).coerceIn(0f, 1f); event.changes.forEach { it.consume() } }
                            PointerEventType.Release -> inspecting = false
                            else -> {}
                        }
                    }
                }
            }
        ) {
            val w = size.width; val h = size.height
            val range = (maxVal - minVal).takeIf { it > 0f } ?: 1f
            fun xOf(i: Int) = w * i / (points.size - 1)
            fun yOf(v: Float) = h - ((v - minVal) / range) * h * 0.8f - h * 0.1f

            drawPath(Path().apply {
                moveTo(xOf(0), h); lineTo(xOf(0), yOf(points[0]))
                for (i in 1 until points.size) { val cx = (xOf(i-1)+xOf(i))/2f; cubicTo(cx, yOf(points[i-1]), cx, yOf(points[i]), xOf(i), yOf(points[i])) }
                lineTo(xOf(points.size-1), h); close()
            }, brush = Brush.verticalGradient(listOf(lc.copy(alpha = 0.25f), Color.Transparent), startY = 0f, endY = h))

            drawPath(Path().apply {
                moveTo(xOf(0), yOf(points[0]))
                for (i in 1 until points.size) { val cx = (xOf(i-1)+xOf(i))/2f; cubicTo(cx, yOf(points[i-1]), cx, yOf(points[i]), xOf(i), yOf(points[i])) }
            }, color = lc, style = Stroke(width = 2.dp.toPx()))

            if (inspecting) {
                val ix = xOf(inspectIdx); val iy = yOf(points[inspectIdx])
                drawLine(color = lc.copy(alpha = 0.4f), start = Offset(ix, 0f), end = Offset(ix, h), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                drawCircle(lc.copy(alpha = 0.2f), 7.dp.toPx(), Offset(ix, iy))
                drawCircle(lc, 4.dp.toPx(), Offset(ix, iy))
                drawCircle(Color.White, 2.dp.toPx(), Offset(ix, iy))
            } else {
                drawCircle(lc, 3.5.dp.toPx(), Offset(xOf(0), yOf(points[0])))
                drawCircle(lc, 3.5.dp.toPx(), Offset(xOf(points.size-1), yOf(points.last())))
            }
        }
    }
}

@Composable
private fun CfInteractiveBarChart(bars: List<CfMonthBar>) {
    if (bars.isEmpty()) return
    val maxVal = bars.maxOf { maxOf(it.income, it.expenses) }.takeIf { it > 0f } ?: 1f
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    var chartSize   by remember { mutableStateOf(IntSize.Zero) }

    // Tooltip row
    val tooltipBar = selectedIdx?.let { bars.getOrNull(it) }
    Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        if (tooltipBar != null) {
            Row(modifier = Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = Primary.copy(alpha = 0.12f)) {
                    Text("+${"$%,.0f".format(tooltipBar.income)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = Tertiary.copy(alpha = 0.12f)) {
                    Text("-${"$%,.0f".format(tooltipBar.expenses)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Tertiary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val incomeC  = Primary; val expenseC = Tertiary
    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()
            .onSizeChanged { chartSize = it }
            .pointerInput(bars.size) {
                detectTapGestures { tap ->
                    val slotW = chartSize.width.toFloat() / bars.size
                    val idx   = (tap.x / slotW).toInt().coerceIn(0, bars.size - 1)
                    selectedIdx = if (selectedIdx == idx) null else idx
                }
            }
        ) {
            val w = size.width; val h = size.height
            val n = bars.size; val slotW = w / n; val barW = slotW * 0.28f; val gap = slotW * 0.04f

            bars.forEachIndexed { i, bar ->
                val cx      = slotW * i + slotW / 2f
                val alpha   = if (selectedIdx == null || selectedIdx == i) 1f else 0.35f
                val incomeH = (bar.income  / maxVal) * h * 0.85f
                val expenseH= (bar.expenses / maxVal) * h * 0.85f
                if (incomeH  > 0f) drawRect(incomeC.copy(alpha = alpha),  Offset(cx - barW - gap/2f, h - incomeH),  Size(barW, incomeH))
                if (expenseH > 0f) drawRect(expenseC.copy(alpha = alpha), Offset(cx + gap/2f,        h - expenseH), Size(barW, expenseH))
                if (selectedIdx == i) {
                    drawRect(OnSurface.copy(alpha = 0.06f), Offset(slotW * i, 0f), Size(slotW, h))
                }
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        bars.forEach { bar ->
            Text(bar.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CfDonutChart(slices: List<CfSlice>) {
    if (slices.isEmpty()) return
    val total       = slices.sumOf { it.amount.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    var chartSize   by remember { mutableStateOf(IntSize.Zero) }

    val selectedSlice = selectedIdx?.let { slices.getOrNull(it) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(160.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()
                .onSizeChanged { chartSize = it }
                .pointerInput(slices.size) {
                    detectTapGestures { tap ->
                        val cx = chartSize.width / 2f; val cy = chartSize.height / 2f
                        val dx = tap.x - cx; val dy = tap.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        val outerR = minOf(chartSize.width, chartSize.height) / 2f * 0.85f
                        val innerR = outerR * 0.52f
                        if (dist in innerR..outerR) {
                            val rawAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            val normAngle = (rawAngle + 90f + 360f) % 360f
                            var cumAngle = 0f
                            val hit = slices.indexOfFirst { s ->
                                val sweep = (s.amount / total) * 360f
                                val found = normAngle >= cumAngle && normAngle < cumAngle + sweep
                                cumAngle += sweep
                                found
                            }.takeIf { it >= 0 }
                            selectedIdx = if (hit == selectedIdx) null else hit
                        } else {
                            selectedIdx = null
                        }
                    }
                }
            ) {
                val w = size.width; val h = size.height
                val outerR  = minOf(w, h) / 2f * 0.85f
                val innerR  = outerR * 0.52f
                val cx = w / 2f; val cy = h / 2f
                var startAngle = -90f

                slices.forEachIndexed { i, s ->
                    val sweep = (s.amount / total) * 360f
                    val isSelected = selectedIdx == i
                    val alpha  = if (selectedIdx == null || isSelected) 1f else 0.4f
                    val offset = if (isSelected) 6.dp.toPx() else 0f
                    val radMid = Math.toRadians((startAngle + sweep / 2f).toDouble())
                    val ox     = (cos(radMid) * offset).toFloat()
                    val oy     = (sin(radMid) * offset).toFloat()

                    drawArc(
                        color     = s.color.copy(alpha = alpha),
                        startAngle= startAngle,
                        sweepAngle= sweep - 1f,
                        useCenter = false,
                        topLeft   = Offset(cx - outerR + ox, cy - outerR + oy),
                        size      = Size(outerR * 2f, outerR * 2f),
                        style     = Stroke(width = outerR - innerR)
                    )
                    startAngle += sweep
                }
            }
            // Center text
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (selectedSlice != null) {
                    Text(cfFormatCompact(selectedSlice.amount), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                    Text(selectedSlice.name, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 80.dp))
                } else {
                    Text(cfFormatCompact(total), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }

        // Legend
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.take(6).forEach { s ->
                val isSelected = selectedSlice == s
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(s.color.copy(alpha = if (selectedIdx == null || isSelected) 1f else 0.35f)))
                    Text(s.name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) OnSurface else OnSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text("${"%.0f".format(s.amount / total * 100)}%", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun IncomeSparkline(points: List<Float>, color: Color) {
    if (points.size < 2) return
    val minV = points.min(); val maxV = points.max()
    val lc = color
    Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        val w = size.width; val h = size.height
        val range = (maxV - minV).takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - ((v - minV) / range) * h * 0.75f - h * 0.1f
        val path = Path().apply {
            moveTo(xOf(0), yOf(points[0]))
            for (i in 1 until points.size) { val cx = (xOf(i-1)+xOf(i))/2f; cubicTo(cx, yOf(points[i-1]), cx, yOf(points[i]), xOf(i), yOf(points[i])) }
        }
        drawPath(path, color = lc, style = Stroke(width = 2.dp.toPx()))
        points.forEachIndexed { i, v -> drawCircle(lc, 3.dp.toPx(), Offset(xOf(i), yOf(v))) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RowScope.SummaryCol(label: String, value: String, color: Color) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeader(title: String, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun DayEventRow(title: String, amount: String, badge: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.1f)) {
            Text(badge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color)
        }
        Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Text(amount, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CfInsightCard(icon: ImageVector, title: String, value: String, description: String, color: Color) {
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Bold)
            }
            Text(description, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant,
                textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 110.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun cfAdvanceDate(date: LocalDate, frequency: String): LocalDate = when (frequency.lowercase()) {
    "daily" -> date.plusDays(1); "weekly" -> date.plusWeeks(1); "biweekly" -> date.plusWeeks(2)
    "monthly" -> date.plusMonths(1); "quarterly" -> date.plusMonths(3); "yearly" -> date.plusYears(1)
    else -> date.plusMonths(1)
}

private fun cfFormatCompact(value: Float): String {
    val abs = abs(value); val prefix = if (value < 0) "-$" else "$"
    return when { abs >= 1_000_000 -> "$prefix${"%.1f".format(abs / 1_000_000)}M"; abs >= 1_000 -> "$prefix${"%.1f".format(abs / 1_000)}K"; else -> "$prefix${"%.0f".format(abs)}" }
}

