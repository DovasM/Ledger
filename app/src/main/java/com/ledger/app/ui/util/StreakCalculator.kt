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

    // The period immediately before the current one — where carry-over takes its residual from.
    fun previousRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val start = start(today)
        return when (this) {
            WEEKLY  -> start.minusWeeks(1) to start.minusDays(1)
            MONTHLY -> start.minusMonths(1) to start.minusDays(1)
            YEARLY  -> start.minusYears(1) to start.minusDays(1)
        }
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

// A budget scoped to neither a category nor a wallet: the cap on everything you spend, optionally
// narrowed to one wallet. This is what drives the daily allowance. Category budgets are no longer
// summed into a total — that produced a number nobody chose (two budgets of 2000 and 30 implied a
// "monthly budget" of 8990), and it made every unbudgeted purchase invisible to the allowance.
data class OverallPace(
    val budgetId: String,
    val walletId: String?,
    val period: BudgetPeriod,
    val baseLimit: Double,
    val carried: Double,
    val periodSpent: Double,
    val staticDaily: Double,
    val todayDaily: Double,
    val spentToday: Double
) {
    // carry-over folds the previous period's residual into this period's ceiling.
    val effectiveLimit: Double get() = baseLimit + carried
    val periodRemaining: Double get() = effectiveLimit - periodSpent
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
    // Everything the overall budget covers — all expenses, narrowed to its wallet if it has one.
    // Empty when there is no overall budget, in which case there is no allowance to judge against.
    val dailyExpenses: Map<String, Double>,
    val dailyExpensesAll: Map<String, Double>,
    val daysWithTx: Set<String>,
    val dailyAllowance: Double,
    val todayAllowance: Double,
    val monthlyBudgetEquivalent: Double,
    val unbudgetedToday: Double,
    val overall: OverallPace?,
    val categoryPaces: List<CategoryPace>,
    val allowance: AllowanceSettings,
    val currentStreak: Int,
    val bestStreak: Int
) {
    // What earlier days in the window handed to today: positive if you underspent, negative if you
    // overspent. Zero when rollover is off.
    val carriedIntoToday: Double get() = overall?.carried ?: 0.0

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

    // "At most X in total" can only be one number, so a second overall budget is a contradiction.
    // create_budget now refuses one, but legacy data can still hold several — take the most recent
    // as the current intent. BudgetsScreen lists the rest so none is silently ignored.
    val overallBudget = budgets.filter { it.categoryId == null }.maxByOrNull { it.createdAt }

    fun byDay(list: List<Transaction>) = list
        .groupBy { it.createdAt.take(10) }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

    val dailyExpensesAll = byDay(expenses)
    val daysWithTx = transactions.map { it.createdAt.take(10) }.toSet()

    // An overall budget covers *everything*, which is the whole point of it: no purchase falls
    // outside the limit the way it did when the total was a sum of category budgets.
    val coveredExpenses = overallBudget?.let { b ->
        expenses.filter { b.walletId == null || it.walletId == b.walletId }
    } ?: emptyList()
    val dailyExpenses = byDay(coveredExpenses)

    val overall = overallBudget?.let { budget ->
        val period = BudgetPeriod.from(budget.period)
        val start = period.start(today)

        fun spentBetween(from: LocalDate, to: LocalDate, excludeToday: Boolean = false) =
            coveredExpenses.filter { tx ->
                val d = dateOf(tx) ?: return@filter false
                !d.isBefore(from) && !d.isAfter(to) &&
                    !(excludeToday && tx.createdAt.take(10) == todayKey)
            }.sumOf { it.amount }

        // Carry-over and the daily rollover compose rather than conflict: carry-over moves the
        // *previous period's* residual into this period's ceiling, while rollover redistributes
        // this period's ceiling across its remaining days. Only one period back, deliberately —
        // chaining further would walk unbounded history for a number nobody could trace.
        //
        // The previous period only counts if the budget already existed for it. Without this, a
        // budget created today inherits a "deficit" measured against a limit that was never in
        // force: a fresh 200/month budget picked up last month's 2644 of spending and opened at
        // minus 2244.
        val createdOn = runCatching { LocalDate.parse(budget.createdAt.take(10)) }.getOrNull()
        val carried = if (budget.carryOver) {
            val (prevStart, prevEnd) = period.previousRange(today)
            if (createdOn != null && createdOn.isAfter(prevStart)) 0.0
            else budget.limitAmount - spentBetween(prevStart, prevEnd)
        } else 0.0

        val effectiveLimit = budget.limitAmount + carried
        val staticDaily = (effectiveLimit / period.lengthInDays(today)).coerceAtLeast(0.0)
        val windowBudget = staticDaily * daysInWindow
        val spentInWindowBeforeToday = spentBetween(windowStart, today, excludeToday = true)
        val dynamicDaily = (windowBudget - spentInWindowBeforeToday).coerceAtLeast(0.0) / daysLeftInWindow

        OverallPace(
            budgetId = budget.id,
            walletId = budget.walletId,
            period = period,
            baseLimit = budget.limitAmount,
            carried = carried,
            periodSpent = spentBetween(start, today),
            staticDaily = staticDaily,
            todayDaily = if (allowance.rollover) dynamicDaily else min(staticDaily, dynamicDaily),
            spentToday = dailyExpenses[todayKey] ?: 0.0
        )
    }

    // Only the overall budget produces an allowance. Category budgets are limits on their own
    // domain, not slices of a whole, so adding them up never described anything the user chose.
    val dailyAllowance = overall?.staticDaily ?: 0.0
    val todayAllowance = overall?.todayDaily ?: 0.0
    // With an overall budget nothing is unbudgeted by definition; the figure only means something
    // for a wallet-scoped one, where spending from other wallets sits outside the cap.
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
        overall = overall,
        categoryPaces = paces,
        allowance = allowance,
        currentStreak = current,
        bestStreak = best
    )
}
