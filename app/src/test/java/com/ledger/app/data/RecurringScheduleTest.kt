package com.ledger.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ledger.RecurringTransaction
import java.time.LocalDate

/**
 * Recurring transactions are the one place the app writes rows on its own, and it writes as many as
 * the calendar says. Getting the count or the dates wrong silently invents or loses money.
 *
 * Fixed clock: today is 2026-03-10.
 */
class RecurringScheduleTest {

    private val today: LocalDate = LocalDate.parse("2026-03-10")

    private fun rec(
        nextDate: String,
        frequency: String = "monthly",
        id: String = "r1",
        title: String = "Rent",
        amountCents: Long = 145_000,
        isIncome: Boolean = false
    ) = RecurringTransaction(
        id, title, amountCents, "Housing", "w1", isIncome, frequency, nextDate, "2026-01-01T00:00:00Z"
    )

    @Test
    fun `an item due today posts once and moves to the next period`() {
        val plan = planDueRecurring(listOf(rec("2026-03-10")), today)
        assertEquals(1, plan.posts.size)
        assertEquals(LocalDate.parse("2026-03-10"), plan.posts[0].occurredOn)
        assertEquals(LocalDate.parse("2026-04-10"), plan.advancedTo["r1"])
    }

    @Test
    fun `an item due tomorrow posts nothing and does not move`() {
        val plan = planDueRecurring(listOf(rec("2026-03-11")), today)
        assertTrue(plan.posts.isEmpty())
        assertTrue(plan.advancedTo.isEmpty())
    }

    /**
     * Due since December and today is 10 March: December, January, February and today's own posting
     * are all owed — four rows, each dated the day it was actually due.
     */
    @Test
    fun `a monthly item missed since december posts every occurrence it owes`() {
        val plan = planDueRecurring(listOf(rec("2025-12-10")), today)
        assertEquals(
            listOf("2025-12-10", "2026-01-10", "2026-02-10", "2026-03-10").map(LocalDate::parse),
            plan.posts.map { it.occurredOn }
        )
        assertEquals(LocalDate.parse("2026-04-10"), plan.advancedTo["r1"])
    }

    /**
     * The dates are the point: a January posting must land in January, or the reports show three
     * months of rent in March. This is what the occurred_at split was for.
     */
    @Test
    fun `missed postings are dated when they were due, not all on today`() {
        val plan = planDueRecurring(listOf(rec("2026-01-10")), today)
        assertEquals(3, plan.posts.size)
        assertEquals(LocalDate.parse("2026-01-10"), plan.posts.first().occurredOn)
        // Exactly one lands on today — the occurrence that is genuinely due now.
        assertEquals(1, plan.posts.count { it.occurredOn == today })
        assertEquals(2, plan.posts.count { it.occurredOn.isBefore(today) })
    }

    @Test
    fun `every frequency advances by its own step`() {
        val cases = mapOf(
            "daily" to "2026-03-11",
            "weekly" to "2026-03-17",
            "biweekly" to "2026-03-24",
            "monthly" to "2026-04-10",
            "quarterly" to "2026-06-10",
            "yearly" to "2027-03-10"
        )
        for ((freq, expected) in cases) {
            assertEquals(freq, LocalDate.parse(expected), advanceRecurringDate(LocalDate.parse("2026-03-10"), freq))
        }
    }

    /** Capitalisation in the stored value must not change the schedule. */
    @Test
    fun `frequency matching ignores case`() {
        assertEquals(LocalDate.parse("2026-03-17"), advanceRecurringDate(LocalDate.parse("2026-03-10"), "Weekly"))
    }

    /** An unknown frequency posts monthly rather than never posting at all. */
    @Test
    fun `an unrecognised frequency falls back to monthly`() {
        assertEquals(LocalDate.parse("2026-04-10"), advanceRecurringDate(LocalDate.parse("2026-03-10"), "fortnightly-ish"))
        val plan = planDueRecurring(listOf(rec("2026-03-10", frequency = "nonsense")), today)
        assertEquals(1, plan.posts.size)
        assertEquals(LocalDate.parse("2026-04-10"), plan.advancedTo["r1"])
    }

    /** A daily item left alone for a week owes seven postings, not one. */
    @Test
    fun `a daily item posts once for every day it was missed`() {
        val plan = planDueRecurring(listOf(rec("2026-03-04", frequency = "daily")), today)
        assertEquals(7, plan.posts.size)
        assertEquals(LocalDate.parse("2026-03-11"), plan.advancedTo["r1"])
    }

    /** The 31st has no February, so java.time clamps — and must not lose the item. */
    @Test
    fun `a month end date clamps instead of overflowing`() {
        assertEquals(LocalDate.parse("2026-02-28"), advanceRecurringDate(LocalDate.parse("2026-01-31"), "monthly"))
        val plan = planDueRecurring(listOf(rec("2026-01-31")), today)
        assertEquals(
            listOf("2026-01-31", "2026-02-28").map(LocalDate::parse),
            plan.posts.map { it.occurredOn }
        )
        assertEquals(LocalDate.parse("2026-03-28"), plan.advancedTo["r1"])
    }

    /** A leap day advanced yearly lands on the 28th rather than throwing. */
    @Test
    fun `a leap day advances to the 28th in a common year`() {
        assertEquals(LocalDate.parse("2025-02-28"), advanceRecurringDate(LocalDate.parse("2024-02-29"), "yearly"))
    }

    @Test
    fun `the posted row carries the recurring items own details`() {
        val income = rec("2026-03-10", id = "r2", title = "Salary", amountCents = 420_000, isIncome = true)
        val plan = planDueRecurring(listOf(income), today)
        val p = plan.posts.single()
        assertEquals("Salary", p.title)
        assertEquals(420_000L, p.amountCents)
        assertTrue(p.isIncome)
        assertEquals("w1", p.walletId)
        assertEquals("Housing", p.category)
    }

    /** Several items are scheduled independently of each other. */
    @Test
    fun `each recurring item is scheduled on its own`() {
        val plan = planDueRecurring(
            listOf(
                rec("2026-03-10", id = "rent", title = "Rent"),
                rec("2026-01-10", id = "gym", title = "Gym", frequency = "monthly"),
                rec("2026-03-11", id = "later", title = "Not yet")
            ),
            today
        )
        assertEquals(1, plan.posts.count { it.title == "Rent" })
        assertEquals(3, plan.posts.count { it.title == "Gym" })
        assertEquals(0, plan.posts.count { it.title == "Not yet" })
        assertEquals(setOf("rent", "gym"), plan.advancedTo.keys)
    }

    /** A date the app cannot read is left alone rather than guessed at or crashed on. */
    @Test
    fun `an unreadable next date is skipped`() {
        val plan = planDueRecurring(
            listOf(rec("not-a-date", id = "broken"), rec("2026-03-10", id = "ok")),
            today
        )
        assertEquals(1, plan.posts.size)
        assertEquals("ok", plan.posts.single().recurringId)
        assertTrue("broken" !in plan.advancedTo)
    }

    @Test
    fun `nothing due means nothing to do`() {
        assertTrue(planDueRecurring(emptyList(), today).posts.isEmpty())
    }
}
