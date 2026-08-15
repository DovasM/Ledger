package com.ledger.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A hand-typed split has to add up to the expense exactly, and the screen has to be able to say how
 * far off it is while the numbers are still being typed — finding out after pressing Add that you
 * are three cents short is not much use.
 */
class ShareSplitTest {

    @Test
    fun `what is left is the expense minus the shares so far`() {
        assertEquals(4_000L, ShareSplit.remainder(10_000, listOf(3_000, 3_000)))
        assertEquals(0L, ShareSplit.remainder(10_000, listOf(3_000, 3_000, 4_000)))
        assertEquals(10_000L, ShareSplit.remainder(10_000, emptyList()))
    }

    /** Overshooting reads as a negative remainder rather than as zero. */
    @Test
    fun `too much assigned is reported as a negative remainder`() {
        assertEquals(-500L, ShareSplit.remainder(10_000, listOf(5_000, 5_500)))
        assertFalse(ShareSplit.isBalanced(10_000, listOf(5_000, 5_500)))
    }

    @Test
    fun `a split is only balanced when every cent belongs to somebody`() {
        assertTrue(ShareSplit.isBalanced(10_000, listOf(3_334, 3_333, 3_333)))
        assertFalse("one cent short is not balanced", ShareSplit.isBalanced(10_000, listOf(3_333, 3_333, 3_333)))
        assertFalse(ShareSplit.isBalanced(10_000, emptyList()))
        assertFalse("an expense of nothing is not a split", ShareSplit.isBalanced(0, listOf(0)))
    }

    /** Uneven on purpose is the whole point: one person pays more, another less. */
    @Test
    fun `an uneven split is fine as long as it adds up`() {
        assertTrue(ShareSplit.isBalanced(10_000, listOf(5_000, 3_000, 2_000)))
        assertTrue("one person covering it all is a split too", ShareSplit.isBalanced(10_000, listOf(10_000, 0, 0)))
    }

    @Test
    fun `the rest can be handed to one person`() {
        assertEquals(
            listOf(3_000L, 3_000L, 4_000L),
            ShareSplit.assignRemainderTo(10_000, listOf(3_000, 3_000, 0), 2)
        )
        assertTrue(ShareSplit.isBalanced(10_000, ShareSplit.assignRemainderTo(10_000, listOf(3_000, 3_000, 0), 2)))
    }

    /** Handing over the rest when too much is already assigned takes it back off that person. */
    @Test
    fun `handing over the rest also works downwards`() {
        val fixed = ShareSplit.assignRemainderTo(10_000, listOf(6_000, 6_000), 1)
        assertEquals(listOf(6_000L, 4_000L), fixed)
        assertTrue(ShareSplit.isBalanced(10_000, fixed))
    }

    @Test
    fun `an index that is not there changes nothing`() {
        val shares = listOf(3_000L, 3_000L)
        assertEquals(shares, ShareSplit.assignRemainderTo(10_000, shares, 5))
        assertEquals(shares, ShareSplit.assignRemainderTo(10_000, shares, -1))
    }

    /**
     * The even split this screen starts from is the Rust one, and it is already balanced — the
     * custom mode begins from a state that could be saved as it stands.
     */
    @Test
    fun `the even split it starts from is already balanced`() {
        for (amount in listOf(10_000L, 1L, 99L, 12_345L)) {
            for (people in 1..5) {
                val even = evenShares(amount, people)
                assertTrue("$amount between $people", ShareSplit.isBalanced(amount, even))
            }
        }
    }

    /** Mirrors `split_equally` on the Rust side: the remainder goes to the first shares. */
    private fun evenShares(amountCents: Long, people: Int): List<Long> {
        val base = amountCents / people
        val remainder = amountCents - base * people
        return (0 until people).map { if (it < remainder) base + 1 else base }
    }
}
