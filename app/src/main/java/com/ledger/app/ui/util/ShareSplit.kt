package com.ledger.app.ui.util

/**
 * Checking a hand-typed split before it is offered to the database.
 *
 * The Rust side refuses shares that do not sum to the expense, which is the guarantee that matters.
 * But a refusal after pressing Add is a poor way to find out you are three cents short, so the
 * screen works out the same answer while the numbers are being typed. Kept here, away from Compose,
 * because it is the part that can be wrong.
 */
object ShareSplit {

    /**
     * What is still unassigned. Positive means there is money left to give somebody; negative means
     * the shares already come to more than the expense.
     */
    fun remainder(amountCents: Long, shares: List<Long>): Long = amountCents - shares.sum()

    /** A split can be stored only when every cent belongs to somebody. */
    fun isBalanced(amountCents: Long, shares: List<Long>): Boolean =
        amountCents > 0 && shares.isNotEmpty() && remainder(amountCents, shares) == 0L

    /**
     * Gives the whole remainder to one person, so a nearly-finished split can be completed without
     * arithmetic. Used by the "give the rest to…" action beside each field.
     */
    fun assignRemainderTo(amountCents: Long, shares: List<Long>, index: Int): List<Long> {
        if (index !in shares.indices) return shares
        val left = remainder(amountCents, shares)
        return shares.mapIndexed { i, value -> if (i == index) value + left else value }
    }
}
