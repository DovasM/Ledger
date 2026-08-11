package com.ledger.app.ui.util

import uniffi.ledger.Budget
import uniffi.ledger.Category
import uniffi.ledger.Transaction
import java.time.LocalDate

/**
 * Shared fixtures for the budget maths tests.
 *
 * Everything is pinned to a fixed clock so the expected numbers can be worked out by hand and stay
 * worked out:
 *
 *   TODAY = 2026-03-10, a Tuesday
 *   March 2026 has 31 days; the week runs Mon 2026-03-09 … Sun 2026-03-15
 *   February 2026 has 28 days, so the previous monthly period is 2026-02-01 … 2026-02-28
 *
 * From that: a MONTHLY window has 31 days with 22 left (today included) and 9 elapsed; a WEEKLY
 * window has 7 days with 6 left and 1 elapsed.
 */
object F {
    val TODAY: LocalDate = LocalDate.parse("2026-03-10")

    const val WALLET = "w-personal"
    const val WALLET_OTHER = "w-other"
    const val WALLET_WORK = "w-work"

    const val DAYS_IN_MONTH = 31
    const val DAYS_LEFT_IN_MONTH = 22
    const val DAYS_ELAPSED_IN_MONTH = 9

    fun category(name: String, id: String = "cat-$name", isExpense: Boolean = true) =
        Category(id, name, "label", "#00513F", isExpense, "2026-01-01T00:00:00Z")

    /** An expense unless [income] is set. [on] is the date the money moved. */
    fun tx(
        amountCents: Long,
        category: String = "Groceries",
        on: String = TODAY.toString(),
        walletId: String = WALLET,
        income: Boolean = false,
        id: String = "tx-${counter++}"
    ) = Transaction(id, walletId, category, category, amountCents, income, null, "${on}T00:00:00Z")

    /** A budget on one category. */
    fun categoryBudget(
        categoryId: String,
        limitCents: Long,
        period: String = "monthly",
        alertThreshold: Double = 80.0,
        carryOver: Boolean = false,
        createdAt: String = "2026-01-01T00:00:00Z",
        id: String = "b-${counter++}"
    ) = Budget(id, categoryId, null, limitCents, period, alertThreshold, carryOver, createdAt)

    /** The "everything" budget: no category, optionally scoped to one wallet. */
    fun overallBudget(
        limitCents: Long,
        period: String = "monthly",
        carryOver: Boolean = false,
        walletId: String? = null,
        createdAt: String = "2026-01-01T00:00:00Z",
        id: String = "b-overall-${counter++}"
    ) = Budget(id, null, walletId, limitCents, period, 80.0, carryOver, createdAt)

    private var counter = 0
}
