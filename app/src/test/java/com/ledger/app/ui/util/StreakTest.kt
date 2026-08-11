package com.ledger.app.ui.util

import com.ledger.app.ui.util.F.TODAY
import com.ledger.app.ui.util.F.category
import com.ledger.app.ui.util.F.overallBudget
import com.ledger.app.ui.util.F.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A day counts as good when it stayed inside the allowance. With no allowance to judge against,
 * "good" degrades to "you recorded something", which is the only honest meaning left.
 */
class StreakTest {

    /** 310.00 a month over 31 days: 10.00 a day. */
    private fun stats(
        transactions: List<uniffi.ledger.Transaction>,
        budgets: List<uniffi.ledger.Budget> = listOf(overallBudget(31_000))
    ) = computeStreakStats(
        transactions = transactions,
        budgets = budgets,
        categories = listOf(category("Groceries")),
        today = TODAY,
        allowance = AllowanceSettings(rollover = false, window = BudgetPeriod.MONTHLY)
    )

    @Test
    fun `a day under the allowance is good and a day over it is not`() {
        val s = stats(
            listOf(
                tx(500, on = TODAY.toString()),
                tx(5_000, on = "2026-03-09")
            )
        )
        assertEquals(1_000L, s.dailyAllowanceCents)
        assertTrue(s.isGoodDay(TODAY))
        assertFalse(s.isGoodDay(TODAY.minusDays(1)))
    }

    /** Spending exactly the allowance is staying inside it. */
    @Test
    fun `spending exactly the allowance still counts as a good day`() {
        val s = stats(listOf(tx(1_000, on = TODAY.toString())))
        assertTrue(s.isGoodDay(TODAY))
    }

    /** A day with no spending at all is inside any allowance. */
    @Test
    fun `a day with nothing spent is good`() {
        val s = stats(listOf(tx(500, on = TODAY.toString())))
        assertTrue(s.isGoodDay(TODAY.minusDays(3)))
    }

    /** Several purchases add up before the day is judged. */
    @Test
    fun `a day is judged on its total, not on each purchase`() {
        val s = stats(
            listOf(
                tx(600, on = TODAY.toString()),
                tx(600, on = TODAY.toString())
            )
        )
        assertEquals(1_200L, s.spentOnCents(TODAY))
        assertFalse(s.isGoodDay(TODAY))
    }

    /** The run of good days ending today, and the best run in the window. */
    @Test
    fun `the current streak counts back from today and stops at the first bad day`() {
        val s = stats(
            listOf(
                tx(500, on = TODAY.toString()),          // good
                tx(500, on = "2026-03-09"),              // good
                tx(9_000, on = "2026-03-08"),            // over
                tx(500, on = "2026-03-07"),              // good
                tx(500, on = "2026-03-06")               // good
            )
        )
        assertEquals(2, s.currentStreak)
        assertTrue(s.bestStreak >= s.currentStreak)
    }

    /** A clean record must not report a current streak longer than the best one. */
    @Test
    fun `current never exceeds best`() {
        val s = stats(listOf(tx(100, on = TODAY.toString())))
        assertEquals(STREAK_LOOKBACK_DAYS, s.currentStreak)
        assertEquals(STREAK_LOOKBACK_DAYS, s.bestStreak)
    }

    /** With no overall budget there is nothing to be under, so recording is the achievement. */
    @Test
    fun `without an allowance a good day is one you recorded`() {
        val s = stats(listOf(tx(99_999, on = TODAY.toString())), budgets = emptyList())
        assertEquals(0L, s.dailyAllowanceCents)
        assertTrue(s.isGoodDay(TODAY))
        assertFalse(s.isGoodDay(TODAY.minusDays(1)))
    }

    /** Income counts as activity for the day even though it is not spending. */
    @Test
    fun `a day with only income still counts as recorded`() {
        val s = stats(listOf(tx(50_000, on = TODAY.toString(), income = true)), budgets = emptyList())
        assertTrue(TODAY.toString() in s.daysWithTx)
        assertTrue(s.isGoodDay(TODAY))
    }

    @Test
    fun `future days are marked as future, not as good`() {
        val s = stats(listOf(tx(500, on = TODAY.toString())))
        assertEquals(DayState.Future, s.dayState(TODAY.plusDays(1)))
        assertEquals(DayState.Good, s.dayState(TODAY))
    }

    @Test
    fun `an overspent day with transactions reads as over`() {
        val s = stats(listOf(tx(9_000, on = TODAY.toString())))
        assertEquals(DayState.Over, s.dayState(TODAY))
    }
}
