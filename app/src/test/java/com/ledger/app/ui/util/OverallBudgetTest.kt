package com.ledger.app.ui.util

import com.ledger.app.ui.util.F.DAYS_IN_MONTH
import com.ledger.app.ui.util.F.DAYS_LEFT_IN_MONTH
import com.ledger.app.ui.util.F.TODAY
import com.ledger.app.ui.util.F.categoryBudget
import com.ledger.app.ui.util.F.category
import com.ledger.app.ui.util.F.overallBudget
import com.ledger.app.ui.util.F.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "everything" budget — a budget with no category — and the daily allowance it produces.
 *
 * This is the number the user acts on every day and the one the widget shows, and it is where the
 * bugs have been: a weekly limit pro-rated wrong, a carry-over reaching back before the budget
 * existed, a second overall budget silently ignored.
 */
class OverallBudgetTest {

    private fun stats(
        transactions: List<uniffi.ledger.Transaction> = emptyList(),
        budgets: List<uniffi.ledger.Budget>,
        rollover: Boolean = true,
        window: BudgetPeriod = BudgetPeriod.MONTHLY,
        offBudget: Set<String> = emptySet()
    ) = computeStreakStats(
        transactions = transactions,
        budgets = budgets,
        categories = listOf(category("Groceries")),
        today = TODAY,
        allowance = AllowanceSettings(rollover = rollover, window = window),
        offBudgetWalletIds = offBudget
    )

    /** 310.00 a month over 31 days is a flat 10.00 a day before anything is spent. */
    @Test
    fun `daily share is the limit divided by the days in the period`() {
        val s = stats(budgets = listOf(overallBudget(31_000)))
        assertEquals(1_000L, s.overall!!.staticDailyCents)
        assertEquals(1_000L, s.dailyAllowanceCents)
        assertEquals(0L, s.overall!!.carriedCents)
    }

    /**
     * With rollover on, today gets everything still unspent spread over the days that are left:
     * 31000 / 22 = 1409. With it off, the flat share is a ceiling.
     */
    @Test
    fun `rollover spreads what is left over the remaining days`() {
        val withRollover = stats(budgets = listOf(overallBudget(31_000)), rollover = true)
        assertEquals(31_000L / DAYS_LEFT_IN_MONTH, withRollover.todayAllowanceCents)
        assertEquals(1_409L, withRollover.todayAllowanceCents)

        val without = stats(budgets = listOf(overallBudget(31_000)), rollover = false)
        assertEquals(1_000L, without.todayAllowanceCents)
    }

    /** 50.00 spent earlier this month leaves 260.00 across 22 days: 1181 a day. */
    @Test
    fun `earlier spending reduces todays allowance`() {
        val s = stats(
            transactions = listOf(tx(5_000, on = "2026-03-05")),
            budgets = listOf(overallBudget(31_000))
        )
        assertEquals(26_000L / DAYS_LEFT_IN_MONTH, s.todayAllowanceCents)
        assertEquals(1_181L, s.todayAllowanceCents)
        assertEquals(5_000L, s.overall!!.periodSpentCents)
    }

    /** Overspending early squeezes the rest of the month rather than going negative. */
    @Test
    fun `heavy early spending squeezes the remaining days and never goes below zero`() {
        val nearlyAllGone = stats(
            transactions = listOf(tx(30_000, on = "2026-03-05")),
            budgets = listOf(overallBudget(31_000))
        )
        assertEquals(45L, nearlyAllGone.todayAllowanceCents)

        val blownEntirely = stats(
            transactions = listOf(tx(50_000, on = "2026-03-05")),
            budgets = listOf(overallBudget(31_000))
        )
        assertEquals(0L, blownEntirely.todayAllowanceCents)
        assertEquals(31_000L - 50_000L, blownEntirely.overall!!.periodRemainingCents)
    }

    /** Without rollover the flat share still applies, but a squeezed month can only lower it. */
    @Test
    fun `without rollover today is the lower of the flat share and what is left`() {
        val s = stats(
            transactions = listOf(tx(30_000, on = "2026-03-05")),
            budgets = listOf(overallBudget(31_000)),
            rollover = false
        )
        assertEquals(45L, s.todayAllowanceCents)
    }

    /** February's leftover 110.00 is added to March's ceiling: 420.00 over 31 days. */
    @Test
    fun `carry over adds the previous periods leftover`() {
        val s = stats(
            transactions = listOf(tx(20_000, on = "2026-02-10")),
            budgets = listOf(overallBudget(31_000, carryOver = true))
        )
        assertEquals(11_000L, s.overall!!.carriedCents)
        assertEquals(42_000L, s.overall!!.effectiveLimitCents)
        assertEquals(42_000L / DAYS_IN_MONTH, s.overall!!.staticDailyCents)
        assertEquals(1_354L, s.dailyAllowanceCents)
    }

    /** Overspending February carries a deficit forward, not a windfall. */
    @Test
    fun `carry over carries a deficit too`() {
        val s = stats(
            transactions = listOf(tx(40_000, on = "2026-02-10")),
            budgets = listOf(overallBudget(31_000, carryOver = true))
        )
        assertEquals(-9_000L, s.overall!!.carriedCents)
        assertEquals(22_000L, s.overall!!.effectiveLimitCents)
        assertEquals(709L, s.dailyAllowanceCents)
    }

    /**
     * The bug this exists for: a budget created today used to inherit last month's spending and
     * open at a deficit measured against a limit that was never in force.
     */
    @Test
    fun `carry over ignores a period the budget did not exist for`() {
        val s = stats(
            transactions = listOf(tx(40_000, on = "2026-02-10")),
            budgets = listOf(overallBudget(31_000, carryOver = true, createdAt = "2026-03-05T00:00:00Z"))
        )
        assertEquals(0L, s.overall!!.carriedCents)
        assertEquals(31_000L, s.overall!!.effectiveLimitCents)
    }

    /** Carry-over off means the previous period is simply not consulted. */
    @Test
    fun `without carry over the previous period is ignored`() {
        val s = stats(
            transactions = listOf(tx(20_000, on = "2026-02-10")),
            budgets = listOf(overallBudget(31_000, carryOver = false))
        )
        assertEquals(0L, s.overall!!.carriedCents)
        assertEquals(0L, s.overall!!.periodSpentCents)
    }

    /** Legacy data can hold several; the most recently created one is the current intent. */
    @Test
    fun `the newest overall budget wins when several exist`() {
        val old = overallBudget(10_000, createdAt = "2026-01-01T00:00:00Z")
        val new = overallBudget(31_000, createdAt = "2026-03-01T00:00:00Z")
        val s = stats(budgets = listOf(old, new))
        assertEquals(31_000L, s.overall!!.baseLimitCents)
        assertEquals(new.id, s.overall!!.budgetId)
    }

    /**
     * A wallet-scoped overall budget only covers that wallet. Spending elsewhere is real but sits
     * outside the cap, which is what `unbudgetedToday` reports.
     */
    @Test
    fun `a wallet scoped budget only counts its own wallet`() {
        val s = stats(
            transactions = listOf(
                tx(1_000, on = TODAY.toString(), walletId = F.WALLET),
                tx(2_500, on = TODAY.toString(), walletId = F.WALLET_OTHER)
            ),
            budgets = listOf(overallBudget(31_000, walletId = F.WALLET))
        )
        assertEquals(1_000L, s.overall!!.spentTodayCents)
        assertEquals(1_000L, s.overall!!.periodSpentCents)
        assertEquals(2_500L, s.unbudgetedTodayCents)
    }

    /** An off-budget wallet is excluded everywhere — that is the entire point of the flag. */
    @Test
    fun `off budget wallets are invisible to the allowance`() {
        val s = stats(
            transactions = listOf(
                tx(1_000, on = TODAY.toString(), walletId = F.WALLET),
                tx(9_900, on = TODAY.toString(), walletId = F.WALLET_WORK)
            ),
            budgets = listOf(overallBudget(31_000)),
            offBudget = setOf(F.WALLET_WORK)
        )
        assertEquals(1_000L, s.overall!!.spentTodayCents)
        assertEquals(0L, s.unbudgetedTodayCents)
        assertTrue(F.TODAY.toString() in s.daysWithTx)
    }

    /** Income never counts against a spending budget. */
    @Test
    fun `income is not spending`() {
        val s = stats(
            transactions = listOf(
                tx(1_000, on = TODAY.toString()),
                tx(500_000, on = TODAY.toString(), income = true)
            ),
            budgets = listOf(overallBudget(31_000))
        )
        assertEquals(1_000L, s.overall!!.spentTodayCents)
    }

    /** Several purchases in one day are one day's spending. */
    @Test
    fun `a days spending is the sum of that days transactions`() {
        val s = stats(
            transactions = listOf(
                tx(1_200, on = TODAY.toString()),
                tx(800, on = TODAY.toString()),
                tx(500, on = TODAY.toString()),
                tx(9_999, on = "2026-03-04")
            ),
            budgets = listOf(overallBudget(31_000))
        )
        assertEquals(2_500L, s.overall!!.spentTodayCents)
        assertEquals(2_500L, s.spentOnCents(TODAY))
        assertEquals(12_499L, s.overall!!.periodSpentCents)
    }

    /** A weekly overall budget is paced inside its own week, not stretched across the month. */
    @Test
    fun `a weekly budget is paced over seven days`() {
        val s = stats(
            budgets = listOf(overallBudget(7_000, period = "weekly")),
            window = BudgetPeriod.WEEKLY
        )
        assertEquals(1_000L, s.overall!!.staticDailyCents)
        assertEquals(BudgetPeriod.WEEKLY, s.overall!!.period)
        // 7000 spread over the 6 days left in the week, today included.
        assertEquals(7_000L / 6, s.todayAllowanceCents)
    }

    /** Category budgets are limits on their own domain; they never add up to an allowance. */
    @Test
    fun `category budgets alone produce no allowance`() {
        val s = stats(budgets = listOf(categoryBudget("cat-Groceries", 15_000)))
        assertNull(s.overall)
        assertEquals(0L, s.dailyAllowanceCents)
        assertEquals(0L, s.todayAllowanceCents)
        assertTrue(s.categoryPaces.isNotEmpty())
    }
}
