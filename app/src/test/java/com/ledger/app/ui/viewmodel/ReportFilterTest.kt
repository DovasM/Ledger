package com.ledger.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ledger.Transaction

/**
 * The off-budget rule: a work or investment account still exists, it just does not belong in the
 * reports. Getting this backwards either hides a transaction the user is looking for, or lets a work
 * account eat the personal budget — both have happened.
 */
class ReportFilterTest {

    private fun tx(id: String, walletId: String, amountCents: Long = 1_000) =
        Transaction(id, walletId, "x", "Groceries", amountCents, false, null, "2026-03-10T00:00:00Z")

    private val personal = tx("t1", "w-personal")
    private val alsoPersonal = tx("t2", "w-personal")
    private val work = tx("t3", "w-work", 99_000)

    private val state = TransactionUiState(
        transactions = listOf(personal, alsoPersonal, work),
        offBudgetWalletIds = setOf("w-work")
    )

    @Test
    fun `reports exclude off budget wallets by default`() {
        val reported = state.forReports(includeOffBudget = false)
        assertEquals(listOf("t1", "t2"), reported.map { it.id })
        assertEquals(2_000L, reported.sumOf { it.amountCents })
    }

    @Test
    fun `the setting brings them back`() {
        val reported = state.forReports(includeOffBudget = true)
        assertEquals(3, reported.size)
        assertEquals(101_000L, reported.sumOf { it.amountCents })
    }

    @Test
    fun `with no off budget wallets the two views are the same list`() {
        val plain = TransactionUiState(transactions = listOf(personal, work))
        assertEquals(2, plain.forReports(includeOffBudget = false).size)
        assertEquals(2, plain.forReports(includeOffBudget = true).size)
    }

    /**
     * The unfiltered list stays complete on purpose: editing, search and the transaction list must
     * still be able to reach an off-budget row by id.
     */
    @Test
    fun `filtering for reports never removes the row from the list itself`() {
        state.forReports(includeOffBudget = false)
        assertEquals(3, state.transactions.size)
        assertNotNull(state.transactions.find { it.id == "t3" })
    }

    @Test
    fun `every wallet being off budget leaves the reports empty rather than unfiltered`() {
        val all = TransactionUiState(
            transactions = listOf(personal, work),
            offBudgetWalletIds = setOf("w-personal", "w-work")
        )
        assertTrue(all.forReports(includeOffBudget = false).isEmpty())
    }
}
