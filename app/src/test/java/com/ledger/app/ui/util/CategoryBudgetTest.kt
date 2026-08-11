package com.ledger.app.ui.util

import com.ledger.app.ui.util.F.TODAY
import com.ledger.app.ui.util.F.category
import com.ledger.app.ui.util.F.categoryBudget
import com.ledger.app.ui.util.F.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-category budgets: their own period, their own pace, and the "can I still spend on groceries"
 * question that one blended daily number cannot answer.
 */
class CategoryBudgetTest {

    private val groceries = category("Groceries")
    private val cigarettes = category("Smoking")

    private fun stats(
        transactions: List<uniffi.ledger.Transaction> = emptyList(),
        budgets: List<uniffi.ledger.Budget>,
        categories: List<uniffi.ledger.Category> = listOf(groceries, cigarettes),
        rollover: Boolean = true
    ) = computeStreakStats(
        transactions = transactions,
        budgets = budgets,
        categories = categories,
        today = TODAY,
        allowance = AllowanceSettings(rollover = rollover, window = BudgetPeriod.MONTHLY)
    )

    private fun pace(s: StreakStats, name: String) = s.categoryPaces.first { it.name == name }

    @Test
    fun `a monthly category budget paces over the month`() {
        val s = stats(budgets = listOf(categoryBudget(groceries.id, 15_000)))
        val p = pace(s, "Groceries")
        assertEquals(15_000L, p.limitCents)
        assertEquals(BudgetPeriod.MONTHLY, p.period)
        assertEquals(15_000L / F.DAYS_IN_MONTH, p.staticDailyCents)
        assertEquals(483L, p.staticDailyCents)
    }

    /** A weekly limit stays weekly; the monthly equivalent exists only to compare budgets. */
    @Test
    fun `a weekly category budget is paced over seven days`() {
        val s = stats(budgets = listOf(categoryBudget(groceries.id, 7_000, period = "weekly")))
        val p = pace(s, "Groceries")
        assertEquals(1_000L, p.staticDailyCents)
        assertEquals(31_000L, p.monthlyEquivalentCents)
    }

    @Test
    fun `a yearly category budget is paced over the year`() {
        val s = stats(budgets = listOf(categoryBudget(groceries.id, 365_000, period = "yearly")))
        val p = pace(s, "Groceries")
        assertEquals(1_000L, p.staticDailyCents)
        assertEquals(30_417L, p.monthlyEquivalentCents)
    }

    /** Several purchases in the category on several days all count toward the period. */
    @Test
    fun `period spending sums every transaction in the category this period`() {
        val s = stats(
            transactions = listOf(
                tx(1_200, category = "Groceries", on = "2026-03-02"),
                tx(800, category = "Groceries", on = "2026-03-05"),
                tx(500, category = "Groceries", on = TODAY.toString()),
                tx(300, category = "Groceries", on = TODAY.toString()),
                tx(9_999, category = "Groceries", on = "2026-02-20"),  // previous month
                tx(7_777, category = "Smoking", on = TODAY.toString()) // different category
            ),
            budgets = listOf(categoryBudget(groceries.id, 15_000))
        )
        val p = pace(s, "Groceries")
        assertEquals(2_800L, p.periodSpentCents)
        assertEquals(800L, p.spentTodayCents)
        assertEquals(2_800L, p.monthSpentCents)
        assertEquals(12_200L, p.remainingCents)
    }

    @Test
    fun `ratio and the alert threshold drive the warning`() {
        val s = stats(
            transactions = listOf(tx(8_500, category = "Groceries", on = "2026-03-02")),
            budgets = listOf(categoryBudget(groceries.id, 10_000, alertThreshold = 80.0))
        )
        val p = pace(s, "Groceries")
        assertEquals(0.85, p.ratio, 0.0001)
        assertTrue(p.isAlerting)
        assertFalse(p.isOver)
    }

    @Test
    fun `spending past the limit reports over, not a negative allowance`() {
        val s = stats(
            transactions = listOf(tx(12_000, category = "Groceries", on = "2026-03-02")),
            budgets = listOf(categoryBudget(groceries.id, 10_000))
        )
        val p = pace(s, "Groceries")
        assertTrue(p.isOver)
        assertEquals(-2_000L, p.remainingCents)
        assertEquals(1.2, p.ratio, 0.0001)
    }

    /** A threshold of zero is not "warn me always" — it means nothing was set. */
    @Test
    fun `a missing alert threshold falls back to eighty percent`() {
        val s = stats(
            transactions = listOf(tx(8_500, category = "Groceries", on = "2026-03-02")),
            budgets = listOf(categoryBudget(groceries.id, 10_000, alertThreshold = 0.0))
        )
        val p = pace(s, "Groceries")
        assertEquals(80.0, p.alertThreshold, 0.0001)
        assertTrue(p.isAlerting)
    }

    /**
     * The widget shows one category and it has to be the right one: furthest through its own limit,
     * not the largest in absolute terms.
     */
    @Test
    fun `the tightest category is the one furthest through its own limit`() {
        val s = stats(
            transactions = listOf(
                tx(9_000, category = "Groceries", on = "2026-03-02"),  // 9000/50000 = 18%
                tx(900, category = "Smoking", on = "2026-03-02")       // 900/1000  = 90%
            ),
            budgets = listOf(
                categoryBudget(groceries.id, 50_000),
                categoryBudget(cigarettes.id, 1_000)
            )
        )
        assertEquals("Smoking", s.tightestCategory!!.name)
        assertTrue(s.tightestCategory!!.isAlerting)
    }

    /** Transactions store the category name; matching it must not depend on capitalisation. */
    @Test
    fun `category matching ignores case`() {
        val s = stats(
            transactions = listOf(tx(2_500, category = "groceries", on = TODAY.toString())),
            budgets = listOf(categoryBudget(groceries.id, 10_000))
        )
        assertEquals(2_500L, pace(s, "Groceries").spentTodayCents)
    }

    /** A budget whose category is gone produces no pace rather than a crash or a phantom row. */
    @Test
    fun `a budget on a deleted category is skipped`() {
        val s = stats(
            budgets = listOf(categoryBudget("cat-does-not-exist", 10_000)),
            categories = listOf(groceries)
        )
        assertTrue(s.categoryPaces.isEmpty())
    }

    /** Income filed under a spending category is not spending. */
    @Test
    fun `income in the category does not count against it`() {
        val s = stats(
            transactions = listOf(
                tx(2_000, category = "Groceries", on = TODAY.toString()),
                tx(50_000, category = "Groceries", on = TODAY.toString(), income = true)
            ),
            budgets = listOf(categoryBudget(groceries.id, 10_000))
        )
        assertEquals(2_000L, pace(s, "Groceries").spentTodayCents)
    }

    /** Two budgets, two independent paces — one does not eat into the other. */
    @Test
    fun `each category is paced independently`() {
        val s = stats(
            transactions = listOf(
                tx(3_000, category = "Groceries", on = "2026-03-02"),
                tx(400, category = "Smoking", on = "2026-03-02")
            ),
            budgets = listOf(
                categoryBudget(groceries.id, 10_000),
                categoryBudget(cigarettes.id, 1_000)
            )
        )
        assertEquals(2, s.categoryPaces.size)
        assertEquals(7_000L, pace(s, "Groceries").remainingCents)
        assertEquals(600L, pace(s, "Smoking").remainingCents)
    }

    /**
     * With rollover, a frugal start to the window funds today: nine days at 483 with nothing spent
     * leaves a surplus of 4347. Without rollover the carried figure is not in play at all.
     */
    @Test
    fun `rollover carries the windows surplus into today`() {
        val withRollover = stats(budgets = listOf(categoryBudget(groceries.id, 15_000)), rollover = true)
        assertEquals(483L * F.DAYS_ELAPSED_IN_MONTH, pace(withRollover, "Groceries").carriedCents)

        val spentAlready = stats(
            transactions = listOf(tx(5_000, category = "Groceries", on = "2026-03-02")),
            budgets = listOf(categoryBudget(groceries.id, 15_000)),
            rollover = true
        )
        assertEquals(483L * F.DAYS_ELAPSED_IN_MONTH - 5_000L, pace(spentAlready, "Groceries").carriedCents)

        val without = stats(budgets = listOf(categoryBudget(groceries.id, 15_000)), rollover = false)
        assertEquals(0L, pace(without, "Groceries").carriedCents)
    }
}
