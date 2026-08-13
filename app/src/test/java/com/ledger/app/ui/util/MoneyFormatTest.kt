package com.ledger.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The boundary between what the user types and what the database stores. Money is exact inside the
 * app precisely because the rounding happens once, here, on a number a person entered.
 */
class MoneyFormatTest {

    @Test
    fun `typed amounts become whole cents`() {
        assertEquals(1_234L, "12.34".toCentsOrNull())
        assertEquals(1_200L, "12".toCentsOrNull())
        assertEquals(7L, "0.07".toCentsOrNull())
        assertEquals(0L, "0".toCentsOrNull())
    }

    /** A comma is how most of Europe types a decimal point, including in Lithuanian. */
    @Test
    fun `a comma is accepted as the decimal separator`() {
        assertEquals(1_234L, "12,34".toCentsOrNull())
    }

    /** These are the values binary floating point cannot hold; rounding once keeps them exact. */
    @Test
    fun `the awkward values round to the cent the user meant`() {
        assertEquals(29L, "0.29".toCentsOrNull())
        assertEquals(720L, "7.20".toCentsOrNull())
        assertEquals(835L, "8.35".toCentsOrNull())
        assertEquals(99_999_999L, "999999.99".toCentsOrNull())
    }

    /** More precision than a cent is rounded, not truncated. */
    @Test
    fun `sub-cent input rounds to the nearest cent`() {
        assertEquals(1_235L, "12.345".toCentsOrNull())
        assertEquals(1_234L, "12.344".toCentsOrNull())
        assertEquals(1L, "0.005".toCentsOrNull())
    }

    @Test
    fun `nonsense is rejected rather than silently becoming zero`() {
        assertNull("".toCentsOrNull())
        assertNull("abc".toCentsOrNull())
        assertNull("12.34.56".toCentsOrNull())
    }

    @Test
    fun `negative amounts survive the round trip`() {
        assertEquals(-1_234L, (-12.34).toCents())
        assertEquals(-12.34, (-1_234L).asUnits, 0.00001)
    }

    /** Cents in, the same cents out. */
    @Test
    fun `a value survives a trip through units and back`() {
        for (cents in listOf(0L, 1L, 29L, 720L, 1_234L, 99_999_999L, -8_535L)) {
            assertEquals(cents, cents.asUnits.toCents())
        }
    }

    /**
     * The space before a trailing symbol is a non-breaking one on purpose, so "12,34" and "€" can
     * never be split across two lines. Asserted explicitly because a plain space looks identical and
     * would slip through review.
     */
    @Test
    fun `formatting places the symbol where the currency wants it`() {
        assertEquals("$12.34", formatCents(1_234, "USD"))
        assertEquals("12.34 €", formatCents(1_234, "EUR"))
        assertEquals("£12.34", formatCents(1_234, "GBP"))
    }

    @Test
    fun `the number format preference changes the separators`() {
        assertEquals("$1,234.56", formatCents(123_456, "USD", numberFormatIndex = 0))
        assertEquals("1.234,56 €", formatCents(123_456, "EUR", numberFormatIndex = 1))
        // Format 2 groups with a non-breaking space too: "1 234,56".
        assertEquals("1 234,56 €", formatCents(123_456, "EUR", numberFormatIndex = 2))
    }

    /** A negative figure puts the minus before the symbol, not between it and the digits. */
    @Test
    fun `a negative amount reads as minus dollars`() {
        assertEquals("-$85.35", formatCents(-8_535, "USD"))
        assertEquals("-85.35 €", formatCents(-8_535, "EUR"))
    }

    @Test
    fun `zero formats as zero rather than as an empty string`() {
        assertEquals("$0.00", formatCents(0, "USD"))
    }

    @Test
    fun `the compact form is for widgets where space is scarce`() {
        assertEquals("$1,234", formatCentsCompact(123_400, "USD"))
        assertEquals("$24.5k", formatCentsCompact(2_450_000, "USD"))
        assertEquals("$1.3M", formatCentsCompact(130_000_000, "USD"))
        assertEquals("$9.99", formatCentsCompact(999, "USD"))
    }

    /**
     * The bug this pins: editing a budget of 1000 pre-filled its field with "100000", and saving
     * that multiplied it by a hundred again. What goes into a text field has to come back out as
     * the same money.
     */
    @Test
    fun `a field pre-filled from cents parses back to the same cents`() {
        for (cents in listOf(0L, 1L, 29L, 720L, 100_000L, 1_234L, 99_999_999L, -8_535L)) {
            assertEquals(cents, cents.asAmountInput().toCentsOrNull())
        }
    }

    @Test
    fun `the pre-filled value is a plain number, with no symbol or grouping`() {
        assertEquals("1000.00", 100_000L.asAmountInput())
        assertEquals("12.34", 1_234L.asAmountInput())
        assertEquals("0.00", 0L.asAmountInput())
        assertEquals("-85.35", (-8_535L).asAmountInput())
        // Grouping separators would not survive the round trip through toCentsOrNull.
        assertFalse(1_234_567_89L.asAmountInput().contains(","))
        assertFalse(1_234_567_89L.asAmountInput().contains(" "))
    }

    @Test
    fun `an unknown currency code is shown as itself`() {
        assertEquals("XYZ12.34", formatCents(1_234, "XYZ"))
    }
}
