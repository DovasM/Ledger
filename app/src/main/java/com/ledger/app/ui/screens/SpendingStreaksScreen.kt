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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.BudgetViewModel
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.GoalViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingStreaksScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel(),
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    goalViewModel: GoalViewModel = hiltViewModel()
) {
    val txState     by txViewModel.state.collectAsStateWithLifecycle()
    val budgetState by budgetViewModel.state.collectAsStateWithLifecycle()
    val catState    by categoryViewModel.state.collectAsStateWithLifecycle()
    val goalState   by goalViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        txViewModel.loadAll()
        budgetViewModel.load()
        categoryViewModel.load()
        goalViewModel.load()
    }

    val today = remember { LocalDate.now() }

    // ── Core computations ─────────────────────────────────────────────────────

    // Daily expense totals: date string → amount
    val dailyExpenses = remember(txState.transactions) {
        txState.transactions
            .filter { !it.isIncome }
            .groupBy { it.createdAt.take(10) }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
    }

    // Daily allowance = total monthly budget / days in month (0 if no budgets)
    val totalMonthlyBudget = remember(budgetState.budgets) {
        budgetState.budgets.sumOf { it.limitAmount }
    }
    val dailyAllowance = remember(totalMonthlyBudget, today) {
        if (totalMonthlyBudget > 0) totalMonthlyBudget / today.lengthOfMonth() else 0.0
    }

    // "Good day": under daily allowance (if budgets set) or any transaction logged
    fun isGoodDay(date: LocalDate): Boolean {
        val key = date.toString()
        return if (dailyAllowance > 0) {
            (dailyExpenses[key] ?: 0.0) <= dailyAllowance
        } else {
            txState.transactions.any { it.createdAt.take(10) == key }
        }
    }

    // Current streak (backwards from today)
    val currentStreak = remember(dailyExpenses, dailyAllowance, today) {
        var streak = 0
        var d = today
        val limit = today.minusDays(180)
        while (!d.isBefore(limit) && isGoodDay(d)) {
            streak++
            d = d.minusDays(1)
        }
        streak
    }

    // Best streak in last 180 days
    val bestStreak = remember(dailyExpenses, dailyAllowance, today) {
        var best = 0
        var run = 0
        for (i in 0..179) {
            val d = today.minusDays(i.toLong())
            if (isGoodDay(d)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        best
    }

    // Total days with any transaction
    val daysWithData = remember(txState.transactions) {
        txState.transactions.map { it.createdAt.take(10) }.distinct().size
    }

    // Current week Mon–Sun grid
    val weekStart = remember(today) {
        today.with(DayOfWeek.MONDAY)
    }
    val weekDays  = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val weekLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    // ── Weekly stats ──────────────────────────────────────────────────────────

    val weekTxs = remember(txState.transactions, weekStart, today) {
        txState.transactions.filter {
            runCatching {
                val d = LocalDate.parse(it.createdAt.take(10))
                !d.isBefore(weekStart) && !d.isAfter(today)
            }.getOrDefault(false)
        }
    }
    val weekIncome   = remember(weekTxs) { weekTxs.filter {  it.isIncome }.sumOf { it.amount } }
    val weekExpenses = remember(weekTxs) { weekTxs.filter { !it.isIncome }.sumOf { it.amount } }
    val weeklyBudgetShare = dailyAllowance * 7.0
    val weekBudgetPct = if (weeklyBudgetShare > 0) (weekExpenses / weeklyBudgetShare).coerceIn(0.0, 1.5).toFloat() else 0f

    val todayExpenses = dailyExpenses[today.toString()] ?: 0.0
    val todayPct = if (dailyAllowance > 0) (todayExpenses / dailyAllowance).coerceIn(0.0, 1.5).toFloat() else 0f

    // ── Achievements ──────────────────────────────────────────────────────────

    data class Achievement(
        val emoji: String,
        val title: String,
        val desc: String,
        val unlocked: Boolean,
        val color: Color
    )

    val achievements = remember(
        txState.transactions, budgetState.budgets, catState.categories,
        goalState.goals, bestStreak, today
    ) {
        fun inMonth(s: String, y: Int, m: Int) = runCatching {
            val d = LocalDate.parse(s.take(10)); d.year == y && d.monthValue == m
        }.getOrDefault(false)

        // Saver: any month in last 6 with savings rate ≥20%
        val isSaver = (0..5).any { off ->
            val m = today.minusMonths(off.toLong())
            val inc = txState.transactions.filter { it.isIncome && inMonth(it.createdAt, m.year, m.monthValue) }.sumOf { it.amount }
            val exp = txState.transactions.filter { !it.isIncome && inMonth(it.createdAt, m.year, m.monthValue) }.sumOf { it.amount }
            inc > 0 && (inc - exp) / inc >= 0.20
        }

        // Budget Master: any month in last 6 where all budgets were met
        val isBudgetMaster = budgetState.budgets.isNotEmpty() && (0..5).any { off ->
            val m = today.minusMonths(off.toLong())
            val spentByCat = txState.transactions
                .filter { !it.isIncome && inMonth(it.createdAt, m.year, m.monthValue) }
                .groupBy { it.category }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            budgetState.budgets.all { b ->
                val cn = catState.categories.find { it.id == b.categoryId }?.name ?: return@all true
                (spentByCat[cn] ?: 0.0) <= b.limitAmount
            }
        }

        // Consistent: any 14-day run with transactions
        val isConsistent = run {
            var run = 0
            for (i in 0..179) {
                val d = today.minusDays(i.toLong())
                if (txState.transactions.any { it.createdAt.take(10) == d.toString() }) {
                    run++
                    if (run >= 14) return@run true
                } else run = 0
            }
            false
        }

        listOf(
            Achievement("🌱", "First Transaction", "Record your first transaction", txState.transactions.isNotEmpty(), Primary),
            Achievement("💰", "Saver", "Save 20%+ of income in any month", isSaver, Color(0xFF1565C0)),
            Achievement("🎯", "Budget Master", "Stay under all budgets for a full month", isBudgetMaster, Color(0xFF1565C0)),
            Achievement("🔥", "7-Day Streak", "7 consecutive days under daily budget", bestStreak >= 7, Color(0xFFE65100)),
            Achievement("🏆", "30-Day Streak", "30 consecutive days under daily budget", bestStreak >= 30, Color(0xFFFF9800)),
            Achievement("⭐", "First Goal", "Create a savings goal", goalState.goals.isNotEmpty(), Color(0xFFF9A825)),
            Achievement("📅", "Consistent", "Log transactions 14 days in a row", isConsistent, Color(0xFF00838F)),
        )
    }

    val unlockedCount = achievements.count { it.unlocked }

    // ── UI ────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Streaks", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Streak hero ───────────────────────────────────────────────────
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(if (currentStreak >= 7) "🔥" else if (currentStreak > 0) "✅" else "📊", fontSize = 48.sp)
                    Text(
                        "$currentStreak",
                        style = MaterialTheme.typography.displayMedium,
                        color = if (currentStreak >= 7) Color(0xFFE65100) else Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (dailyAllowance > 0) "day streak under daily budget"
                        else "day activity streak",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (dailyAllowance > 0) {
                        Text(
                            "Daily allowance: ${"$%,.2f".format(dailyAllowance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // Week grid
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weekDays.forEachIndexed { i, date ->
                            val isFuture = date.isAfter(today)
                            val good = !isFuture && isGoodDay(date)
                            val hasData = !isFuture && (dailyExpenses[date.toString()] != null ||
                                txState.transactions.any { it.createdAt.take(10) == date.toString() })
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isFuture  -> SurfaceContainerHigh.copy(alpha = 0.4f)
                                                good      -> if (currentStreak > 0 && !date.isAfter(today)) Color(0xFFE65100) else Primary
                                                hasData   -> Tertiary.copy(alpha = 0.7f)
                                                else      -> SurfaceContainerHigh
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isFuture -> Text(weekLabels[i], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.4f))
                                        good     -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        hasData  -> Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        else     -> Text(weekLabels[i], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                    }
                                }
                                Text(weekLabels[i], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Stats row ─────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🏆", fontSize = 20.sp)
                        Text("$bestStreak", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("Best streak", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📅", fontSize = 20.sp)
                        Text("$daysWithData", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("Days logged", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("💎", fontSize = 20.sp)
                        Text("$unlockedCount", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("Achievements", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            // ── This week ─────────────────────────────────────────────────────
            Text("This Week", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

            if (dailyAllowance > 0) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Today
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Today's spending", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                Text(
                                    "${"$%,.2f".format(todayExpenses)} / ${"$%,.2f".format(dailyAllowance)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (todayPct >= 1f) Tertiary else OnSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { todayPct.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (todayPct >= 1f) Tertiary else Primary,
                                trackColor = SurfaceContainerHighest
                            )
                        }
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                        // Week to date
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Week-to-date budget", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                Text(
                                    "${"%.0f".format(weekBudgetPct * 100)}% used",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (weekBudgetPct >= 1f) Tertiary else OnSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { weekBudgetPct.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (weekBudgetPct >= 1f) Tertiary else Primary,
                                trackColor = SurfaceContainerHighest
                            )
                            Text(
                                "${"$%,.2f".format(weekExpenses)} spent · ${"$%,.2f".format((weeklyBudgetShare - weekExpenses).coerceAtLeast(0.0))} remaining",
                                style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Week income vs expenses
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("+${"$%,.0f".format(weekIncome)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                        Text("this week", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    VerticalDivider(modifier = Modifier.height(48.dp).align(Alignment.CenterVertically), color = OutlineVariant.copy(alpha = 0.4f))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("-${"$%,.0f".format(weekExpenses)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.Bold)
                        Text("this week", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }

            // ── Achievements ──────────────────────────────────────────────────
            Text("Achievements", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                achievements.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { a ->
                            LedgerCard(modifier = Modifier.weight(1f)) {
                                Column(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                            .background(if (a.unlocked) a.color.copy(alpha = 0.15f) else SurfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (a.unlocked) Text(a.emoji, fontSize = 22.sp)
                                        else Icon(Icons.Filled.Lock, null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        a.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (a.unlocked) OnSurface else OnSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        a.desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
