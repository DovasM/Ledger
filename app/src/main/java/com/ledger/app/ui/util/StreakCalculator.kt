package com.ledger.app.ui.util

import uniffi.ledger.Budget
import uniffi.ledger.Category
import uniffi.ledger.Transaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

// Budget / streak maths shared by SpendingStreaksScreen, BudgetsScreen and the home-screen widgets.
// Compose-free so every surface computes identical numbers.

const val STREAK_LOOKBACK_DAYS = 180

enum class DayState { Future, Good, Over, Empty }

// A budget is paced inside its OWN period. Forcing a weekly limit into a month window turned
// "2030 per week" into "8990 per month" and then spread it over the remaining days of the month.
enum class BudgetPeriod(val label: String) {
    WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly");

    companion object {
        fun from(raw: String) = when (raw.trim().lowercase()) {
            "weekly" -> WEEKLY
            "yearly" -> YEARLY
            else     -> MONTHLY
        }
    }

    fun start(today: LocalDate): LocalDate = when (this) {
        WEEKLY  -> today.with(DayOfWeek.MONDAY)
        MONTHLY -> today.withDayOfMonth(1)
        YEARLY  -> today.withDayOfYear(1)
    }

    fun end(today: LocalDate): LocalDate = when (this) {
        WEEKLY  -> start(today).plusDays(6)
        MONTHLY -> today.withDayOfMonth(today.lengthOfMonth())
        YEARLY  -> today.withDayOfYear(today.lengthOfYear())
    }

    fun lengthInDays(today: LocalDate): Int = when (this) {
        WEEKLY  -> 7
        MONTHLY -> today.lengthOfMonth()
        YEARLY  -> today.lengthOfYear()
    }

    // Only for comparing budgets of different periods side by side.
    fun monthlyEquivalent(limit: Double, today: LocalDate): Double = when (this) {
        WEEKLY  -> limit * today.lengthOfMonth() / 7.0
        MONTHLY -> limit
        YEARLY  -> limit / 12.0
    }
}

// How the daily allowance carries a surplus or a deficit forward, and when that balance resets.
// Separate from Budget.period on purpose: a monthly budget can still be lived week by week.
data class AllowanceSettings(
    val rollover: Boolean = true,
    val window: BudgetPeriod = BudgetPeriod.MONTHLY
) {
    companion object {
        fun of(rollover: Boolean, window: String) = AllowanceSettings(
            rollover = rollover,
            // Yearly would make the carried balance meaningless day to day.
            window = if (BudgetPeriod.from(window) == BudgetPeriod.WEEKLY) BudgetPeriod.WEEKLY
                     else BudgetPeriod.MONTHLY
        )
    }
}

data class CategoryPace(
    val budgetId: String,
    val name: String,
    val period: BudgetPeriod,
    val limit: Double,
    val periodSpent: Double,
    val spentToday: Double,
    val monthSpent: Double,
    val monthlyEquivalent: Double,
    val alertThreshold: Double,
    val staticDaily: Double,
    val todayDaily: Double,
    // Surplus (+) or deficit (−) carried into today from earlier days in the allowance window.
    val carried: Double
) {
    val remaining: Double get() = limit - periodSpent
    val ratio: Double get() = if (limit > 0) periodSpent / limit else 0.0
    val isOver: Boolean get() = remaining < 0
    val isAlerting: Boolean get() = ratio * 100.0 >= alertThreshold
}

data class StreakStats(
    val today: LocalDate,
    // Budgeted categories only. Spending outside them was never part of any limit, so charging it
    // against the allowance made the figure go negative without breaking an actual budget.
    val dailyExpenses: Map<String, Double>,
    val dailyExpensesAll: Map<String, Double>,
    val daysWithTx: Set<String>,
    val dailyAllowance: Double,
    val todayAllowance: Double,
    val monthlyBudgetEquivalent: Double,
    val unbudgetedToday: Double,
    val categoryPaces: List<CategoryPace>,
    val allowance: AllowanceSettings,
    val currentStreak: Int,
    val bestStreak: Int
) {
    // What earlier days in the window handed to today: positive if you underspent, negative if you
    // overspent. Zero when rollover is off.
    val carriedIntoToday: Double get() = categoryPaces.sumOf { it.carried }

    val daysWithData: Int get() = daysWithTx.size
    val hasBudgets: Boolean get() = dailyAllowance > 0

    fun isGoodDay(date: LocalDate): Boolean {
        val key = date.toString()
        return if (dailyAllowance > 0) (dailyExpenses[key] ?: 0.0) <= dailyAllowance
        else key in daysWithTx
    }

    fun dayState(date: LocalDate): DayState = when {
        date.isAfter(today)           -> DayState.Future
        isGoodDay(date)               -> DayState.Good
        date.toString() in daysWithTx -> DayState.Over
        else                          -> DayState.Empty
    }

    fun weekGrid(): List<DayState> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0..6).map { dayState(monday.plusDays(it.toLong())) }
    }

    fun spentOn(date: LocalDate): Double = dailyExpenses[date.toString()] ?: 0.0

    val spentToday: Double get() = spentOn(today)
    val remainingToday: Double get() = todayAllowance - spentToday

    // One blended figure can't answer "can I still spend on groceries?", so surface the budget
    // furthest through its own limit. Always at most one, so it reads the same with 3 budgets or 30.
    // Not gated on isAlerting — a widget that only speaks up once you're already in trouble is the
    // problem, not the fix. Use CategoryPace.isAlerting for colour instead.
    val tightestCategory: CategoryPace? get() = categoryPaces.maxByOrNull { it.ratio }
}

fun computeStreakStats(
    transactions: List<Transaction>,
    budgets: List<Budget>,
    categories: List<Category>,
    today: LocalDate = LocalDate.now(),
    allowance: AllowanceSettings = AllowanceSettings()
): StreakStats {
    val nameById = categories.associateBy({ it.id }, { it.name })
    val expenses = transactions.filter { !it.isIncome }
    val monthPrefix = "%04d-%02d".format(today.year, today.monthValue)
    val todayKey = today.toString()

    fun dateOf(tx: Transaction): LocalDate? = runCatching { LocalDate.parse(tx.createdAt.take(10)) }.getOrNull()

    // The allowance window is where a carried surplus/deficit lives and resets. Budget limits are
    // pro-rated into it via staticDaily, so a weekly budget works inside a monthly window and back.
    val windowStart = allowance.window.start(today)
    val windowEnd = allowance.window.end(today)
    val daysInWindow = allowance.window.lengthInDays(today)
    val daysLeftInWindow = (ChronoUnit.DAYS.between(today, windowEnd).toInt() + 1).coerceAtLeast(1)
    val daysElapsed = daysInWindow - daysLeftInWindow

    val paces = budgets.mapNotNull { budget ->
        val name = nameById[budget.categoryId] ?: return@mapNotNull null
        val period = BudgetPeriod.from(budget.period)
        val start = period.start(today)

        val ofCategory = expenses.filter { it.category.equals(name, ignoreCase = true) }
        fun spentBetween(from: LocalDate, toExclusiveOfToday: Boolean) = ofCategory.filter { tx ->
            val d = dateOf(tx) ?: return@filter false
            !d.isBefore(from) && !d.isAfter(today) &&
                !(toExclusiveOfToday && tx.createdAt.take(10) == todayKey)
        }.sumOf { it.amount }

        val staticDaily = budget.limitAmount / period.lengthInDays(today)
        val windowBudget = staticDaily * daysInWindow
        val spentInWindowBeforeToday = spentBetween(windowStart, toExclusiveOfToday = true)

        // What earlier days in this window left behind: what they were allotted minus what they used.
        val carried = if (allowance.rollover) staticDaily * daysElapsed - spentInWindowBeforeToday else 0.0
        val dynamicDaily = (windowBudget - spentInWindowBeforeToday).coerceAtLeast(0.0) / daysLeftInWindow

        CategoryPace(
            budgetId = budget.id,
            name = name,
            period = period,
            limit = budget.limitAmount,
            periodSpent = spentBetween(start, toExclusiveOfToday = false),
            spentToday = ofCategory.filter { it.createdAt.take(10) == todayKey }.sumOf { it.amount },
            monthSpent = ofCategory.filter { it.createdAt.startsWith(monthPrefix) }.sumOf { it.amount },
            monthlyEquivalent = period.monthlyEquivalent(budget.limitAmount, today),
            alertThreshold = if (budget.alertThreshold > 0) budget.alertThreshold else 80.0,
            staticDaily = staticDaily,
            // With rollover the figure moves both ways: a frugal day funds tomorrow, an expensive
            // one bites into it. Without it, the plain daily share is a ceiling.
            todayDaily = if (allowance.rollover) dynamicDaily else min(staticDaily, dynamicDaily),
            carried = carried
        )
    }

    val budgetedNames = paces.map { it.name.lowercase() }.toSet()
    val budgetedExpenses = expenses.filter { it.category.lowercase() in budgetedNames }

    fun byDay(list: List<Transaction>) = list
        .groupBy { it.createdAt.take(10) }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

    val dailyExpenses = byDay(budgetedExpenses)
    val dailyExpensesAll = byDay(expenses)
    val daysWithTx = transactions.map { it.createdAt.take(10) }.toSet()

    val dailyAllowance = paces.sumOf { it.staticDaily }
    val todayAllowance = paces.sumOf { it.todayDaily }
    val unbudgetedToday = (dailyExpensesAll[todayKey] ?: 0.0) - (dailyExpenses[todayKey] ?: 0.0)

    fun good(date: LocalDate): Boolean {
        val key = date.toString()
        return if (dailyAllowance > 0) (dailyExpenses[key] ?: 0.0) <= dailyAllowance
        else key in daysWithTx
    }

    // Both loops must cover the same window. The bound used to be today−180 *inclusive*, i.e. 181
    // days, while the best-streak loop ran exactly 180 — so a perfect record reported a current
    // streak of 181 against a best of 180.
    var current = 0
    var d = today
    val oldest = today.minusDays((STREAK_LOOKBACK_DAYS - 1).toLong())
    while (!d.isBefore(oldest) && good(d)) {
        current++
        d = d.minusDays(1)
    }

    var best = 0
    var run = 0
    for (i in 0 until STREAK_LOOKBACK_DAYS) {
        if (good(today.minusDays(i.toLong()))) {
            run++
            if (run > best) best = run
        } else run = 0
    }

    return StreakStats(
        today = today,
        dailyExpenses = dailyExpenses,
        dailyExpensesAll = dailyExpensesAll,
        daysWithTx = daysWithTx,
        dailyAllowance = dailyAllowance,
        todayAllowance = todayAllowance,
        monthlyBudgetEquivalent = paces.sumOf { it.monthlyEquivalent },
        unbudgetedToday = unbudgetedToday,
        categoryPaces = paces,
        allowance = allowance,
        currentStreak = current,
        bestStreak = best
    )
}
