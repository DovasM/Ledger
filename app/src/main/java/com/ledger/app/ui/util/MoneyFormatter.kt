package com.ledger.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.ui.viewmodel.SettingsViewModel

/**
 * One formatter, carrying the user's currency and number format, so a screen can write `money.of(x)`
 * instead of a hand-rolled `"$%,.2f"`.
 *
 * Those hand-rolled formats were wrong twice over. They print a dollar sign to someone whose wallets
 * are in euros, and they ignore the number-format preference entirely. They are also where every
 * money bug in this app has come from: `format` takes `Any?`, so passing cents where a decimal was
 * expected compiles and then either throws or prints a number a hundred times too large.
 */
class MoneyFormatter(
    private val currency: String,
    private val numberFormat: Int
) {
    /** Integer cents — what the database and the FFI hand over. */
    fun of(cents: Long, decimals: Int = 2): String =
        formatCents(cents, currency, numberFormat, decimals)

    /**
     * A fractional number of currency units, for the analysis screens whose arithmetic is genuinely
     * in real numbers. Never for a figure that came straight from the database.
     */
    fun ofUnits(amount: Double, decimals: Int = 2): String =
        formatAmount(amount, currency, numberFormat, decimals)

    /** Chart values arrive as Float; formatting them should not need a cast at every call site. */
    fun ofUnits(amount: Float, decimals: Int = 2): String = ofUnits(amount.toDouble(), decimals)

    /** Shortened for places where the space is fixed: 1.2k, 24.5k, 1.3M. */
    fun compact(cents: Long): String = formatCentsCompact(cents, currency, numberFormat)

    fun compactUnits(amount: Double): String = formatAmountCompact(amount, currency, numberFormat)
}

/**
 * Reads the preferences itself, the same way [rememberReportTransactions] does, so adopting it in a
 * screen costs one line and no changes to that screen's ViewModel parameters.
 */
@Composable
fun rememberMoneyFormatter(
    settings: SettingsViewModel = hiltViewModel()
): MoneyFormatter {
    val currency by settings.currencyCode.collectAsStateWithLifecycle()
    val numberFormat by settings.numberFormatIndex.collectAsStateWithLifecycle()
    return remember(currency, numberFormat) { MoneyFormatter(currency, numberFormat) }
}
