package com.ledger.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ledger.Transaction
import java.time.YearMonth

/**
 * The CSV export is where a whole month of transactions is added up and written out, so it is the
 * one place a mistake in the totals leaves the app entirely and lands in a spreadsheet.
 */
class CsvExportTest {

    private fun tx(
        amountCents: Long,
        category: String = "Groceries",
        on: String = "2026-03-05",
        income: Boolean = false,
        title: String = category,
        note: String? = null,
        id: String = "t-$on-$amountCents-$title"
    ) = Transaction(id, "w1", title, category, amountCents, income, note, "${on}T00:00:00Z")

    /** 400.00 in, 100.00 out: net 300.00 and a savings rate of 75%. */
    private val month = listOf(
        tx(40_000, "Salary", "2026-03-01", income = true, title = "Salary"),
        tx(6_000, "Groceries", "2026-03-05"),
        tx(3_000, "Groceries", "2026-03-12"),
        tx(1_000, "Cafe", "2026-03-12")
    )

    private fun lineAfter(csv: String, label: String): String =
        csv.lineSequence().first { it.startsWith("$label,") }.substringAfter(",")

    @Test
    fun `the monthly summary totals every transaction`() {
        val csv = buildMonthlyCsv(2026, 3, month)
        assertEquals("$400.00", lineAfter(csv, "Income"))
        assertEquals("$100.00", lineAfter(csv, "Expenses"))
        assertEquals("$300.00", lineAfter(csv, "Net"))
        assertEquals("4", lineAfter(csv, "Transactions"))
    }

    /**
     * The savings rate is a ratio of two cent totals. Dividing them as integers gives 0 for anything
     * short of a perfect month, and handing a Long to "%.1f" throws outright.
     */
    @Test
    fun `the monthly savings rate is a real percentage`() {
        val csv = buildMonthlyCsv(2026, 3, month)
        assertEquals("75.0%", lineAfter(csv, "Savings Rate"))
    }

    @Test
    fun `a month with no income has no savings rate rather than a division by zero`() {
        val csv = buildMonthlyCsv(2026, 3, listOf(tx(6_000)))
        assertFalse(csv.contains("Savings Rate"))
    }

    /** Spending is grouped per category and each share is a real percentage of the expenses. */
    @Test
    fun `spending by category sums each category and its share`() {
        val csv = buildMonthlyCsv(2026, 3, month)
        assertEquals("$90.00,90.0%", lineAfter(csv, "Groceries"))
        assertEquals("$10.00,10.0%", lineAfter(csv, "Cafe"))
    }

    /** Every transaction is listed, newest day first, with the date the money moved. */
    @Test
    fun `every transaction is listed under the date it happened`() {
        val csv = buildMonthlyCsv(2026, 3, month)
        val rows = csv.lineSequence().dropWhile { it != "Date,Title,Category,Amount,Type,Note" }.drop(1)
            .filter { it.isNotBlank() }.toList()
        assertEquals(4, rows.size)
        assertTrue(rows.first().startsWith("2026-03-12"))
        assertTrue(rows.last().startsWith("2026-03-01"))
        assertTrue(rows.any { it.contains("$60.00,Expense") })
        assertTrue(rows.any { it.contains("$400.00,Income") })
    }

    /** A title with a comma must not silently become two columns. */
    @Test
    fun `fields containing commas and quotes are escaped`() {
        val csv = buildMonthlyCsv(
            2026, 3,
            listOf(tx(1_000, title = "Lunch, with colleagues", note = "said \"thanks\""))
        )
        assertTrue(csv.contains("\"Lunch, with colleagues\""))
        assertTrue(csv.contains("\"said \"\"thanks\"\"\""))
    }

    @Test
    fun `the quarterly report totals each month and the quarter`() {
        val csv = buildQuarterlyCsv(
            2026, 1,
            listOf(
                tx(40_000, "Salary", "2026-01-10", income = true),
                tx(10_000, "Groceries", "2026-01-11"),
                tx(20_000, "Groceries", "2026-02-11"),
                tx(30_000, "Groceries", "2026-03-11")
            )
        )
        assertEquals("$400.00", lineAfter(csv, "Income"))
        assertEquals("$600.00", lineAfter(csv, "Expenses"))
        assertEquals("-$200.00", lineAfter(csv, "Net"))
        // Spending 600 against 400 of income is a savings rate of minus fifty percent.
        assertEquals("-50.0%", lineAfter(csv, "Savings Rate"))

        // Each month of the quarter is broken out, then totalled.
        assertEquals("$400.00,$100.00,$300.00", lineAfter(csv, "Jan"))
        assertEquals("$0.00,$200.00,-$200.00", lineAfter(csv, "Feb"))
        assertEquals("$0.00,$300.00,-$300.00", lineAfter(csv, "Mar"))
        assertEquals("$400.00,$600.00,-$200.00", lineAfter(csv, "Total"))
    }

    @Test
    fun `the custom report totals the range it was given`() {
        val csv = buildCustomCsv(
            YearMonth.of(2026, 1), YearMonth.of(2026, 3), month, "monthly"
        )
        assertEquals("$400.00", lineAfter(csv, "Income"))
        assertEquals("$100.00", lineAfter(csv, "Expenses"))
        assertEquals("$300.00", lineAfter(csv, "Net"))
        assertEquals("75.0%", lineAfter(csv, "Savings Rate"))
    }

    @Test
    fun `an empty month produces a report rather than an error`() {
        val csv = buildMonthlyCsv(2026, 3, emptyList())
        assertEquals("$0.00", lineAfter(csv, "Income"))
        assertEquals("0", lineAfter(csv, "Transactions"))
    }
}
