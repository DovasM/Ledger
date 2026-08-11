package com.ledger.app.ui.util

import kotlin.math.abs
import kotlin.math.roundToLong

// Amount formatting that honours the user's currency + number-format preferences.
// Pure Kotlin (no Android, no Compose) so Glance widgets and screens share one implementation.
//
// numberFormatIndex mirrors AppearanceSettingsScreen's list:
//   0 → 1,000.00   1 → 1.000,00   2 → 1 000,00

private val currencySymbols = mapOf(
    "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CHF" to "CHF",
    "PLN" to "zł", "SEK" to "kr", "NOK" to "kr", "DKK" to "kr", "CZK" to "Kč",
    "CAD" to "$", "AUD" to "$", "NZD" to "$", "UAH" to "₴", "RON" to "lei"
)

fun currencySymbol(code: String): String =
    currencySymbols[code.uppercase()] ?: code.uppercase()

// Currencies whose symbol conventionally trails the amount ("12,00 €").
private val suffixCurrencies = setOf("EUR", "PLN", "SEK", "NOK", "DKK", "CZK", "RON")

private fun groupingAndDecimal(numberFormatIndex: Int): Pair<String, String> = when (numberFormatIndex) {
    1    -> "." to ","
    2    -> " " to ","
    else -> "," to "."
}

/**
 * Money is stored and passed around as an integer number of cents. Adding cents is exact; adding
 * fractions of a currency unit is not, and it was drifting.
 *
 * This is the one place cents turn into a decimal, and it happens on the way to the screen.
 */
fun formatCents(
    cents: Long,
    currencyCode: String = "USD",
    numberFormatIndex: Int = 0,
    decimals: Int = 2,
    withSymbol: Boolean = true
): String = formatAmount(cents / 100.0, currencyCode, numberFormatIndex, decimals, withSymbol)

fun formatCentsCompact(
    cents: Long,
    currencyCode: String = "USD",
    numberFormatIndex: Int = 0
): String = formatAmountCompact(cents / 100.0, currencyCode, numberFormatIndex)

/**
 * Cents as a fractional number of currency units, for ratios, averages, trends and chart scales —
 * the places where a real number is the right answer and rounding to the cent would be wrong.
 *
 * Never use it to add money together, and never store the result: `Long` cents is the source of
 * truth, this is a view of it. It exists mainly so that `spent / limit` cannot silently become
 * integer division, which is the trap in converting a codebase from Double to Long.
 */
val Long.asUnits: Double get() = this / 100.0

/**
 * The other end of [asUnits]: what the user typed, turned into cents. This is the single rounding
 * step in the whole system, and it happens once, at the edge, on a number a person entered — not
 * repeatedly in the middle of arithmetic, which is what the old Double storage was doing.
 */
fun Double.toCents(): Long = kotlin.math.round(this * 100).toLong()

/** Parses "12.34" (or "12,34") into cents, returning null when it is not a usable amount. */
fun String.toCentsOrNull(): Long? = replace(',', '.').toDoubleOrNull()?.toCents()

fun formatAmount(
    amount: Double,
    currencyCode: String = "USD",
    numberFormatIndex: Int = 0,
    decimals: Int = 2,
    withSymbol: Boolean = true
): String {
    val (grouping, decimal) = groupingAndDecimal(numberFormatIndex)
    val negative = amount < 0
    val abs = abs(amount)

    val scale = when (decimals) { 0 -> 1L; 1 -> 10L; else -> 100L }
    val scaled = (abs * scale).roundToLong()
    val whole = scaled / scale
    val frac = scaled % scale

    val digits = whole.toString()
    val grouped = buildString {
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) append(grouping)
            append(c)
        }
    }

    val number = if (decimals > 0) "$grouped$decimal${frac.toString().padStart(decimals, '0')}" else grouped
    if (!withSymbol) return if (negative) "-$number" else number

    val symbol = currencySymbol(currencyCode)
    val body = if (currencyCode.uppercase() in suffixCurrencies) "$number $symbol" else "$symbol$number"
    return if (negative) "-$body" else body
}

// Compact form for widgets, where horizontal space is scarce: 1.2k / 24.5k / 1.3M.
fun formatAmountCompact(
    amount: Double,
    currencyCode: String = "USD",
    numberFormatIndex: Int = 0
): String {
    val abs = abs(amount)
    return when {
        abs >= 1_000_000 -> {
            val v = amount / 1_000_000
            formatAmount(v, currencyCode, numberFormatIndex, decimals = 1) + "M"
        }
        abs >= 10_000 -> {
            val v = amount / 1_000
            formatAmount(v, currencyCode, numberFormatIndex, decimals = 1) + "k"
        }
        abs >= 1_000 -> formatAmount(amount, currencyCode, numberFormatIndex, decimals = 0)
        else          -> formatAmount(amount, currencyCode, numberFormatIndex, decimals = 2)
    }
}
