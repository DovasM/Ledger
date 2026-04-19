package com.ledger.app.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import uniffi.ledger.Transaction
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val AUTHORITY = "com.ledger.app.fileprovider"

/** Escape a CSV field: wrap in quotes if it contains comma, quote, or newline. */
private fun String.csvField(): String =
    if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\"" else this

private fun Double.currency() = "$%,.2f".format(this)

/** Write [content] to cache/exports/[fileName], then return a share Intent. */
fun shareCsv(context: Context, fileName: String, content: String): Intent {
    val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
    val file = File(exportDir, fileName)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName.removeSuffix(".csv"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

// ── Monthly report ────────────────────────────────────────────────────────────

fun buildMonthlyCsv(
    year: Int, month: Int,
    transactions: List<Transaction>
): String {
    val monthName = YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val income   = transactions.filter { it.isIncome }.sumOf { it.amount }
    val expenses = transactions.filter { !it.isIncome }.sumOf { it.amount }
    val net      = income - expenses
    val savingsRate = if (income > 0) net / income * 100 else null

    val grouped = transactions.sortedByDescending { it.createdAt }.groupBy { it.createdAt.take(10) }
    val sortedDates = grouped.keys.sortedDescending()

    val categoryTotals = transactions.filter { !it.isIncome }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }

    return buildString {
        appendLine("Monthly Report — $monthName")
        appendLine()

        appendLine("SUMMARY")
        appendLine("Income,${income.currency()}")
        appendLine("Expenses,${expenses.currency()}")
        appendLine("Net,${net.currency()}")
        savingsRate?.let { appendLine("Savings Rate,${"%.1f".format(it)}%") }
        appendLine("Transactions,${transactions.size}")
        appendLine()

        appendLine("SPENDING BY CATEGORY")
        appendLine("Category,Amount,% of Expenses")
        categoryTotals.forEach { (cat, amt) ->
            val pct = if (expenses > 0) amt / expenses * 100 else 0.0
            appendLine("${cat.csvField()},${amt.currency()},${"%.1f".format(pct)}%")
        }
        appendLine()

        appendLine("TRANSACTIONS")
        appendLine("Date,Title,Category,Amount,Type,Note")
        sortedDates.forEach { dateStr ->
            grouped[dateStr]?.forEach { tx ->
                appendLine("${tx.createdAt.take(10)},${tx.title.csvField()},${tx.category.csvField()},${tx.amount.currency()},${if (tx.isIncome) "Income" else "Expense"},${(tx.note ?: "").csvField()}")
            }
        }
    }
}

// ── Quarterly report ──────────────────────────────────────────────────────────

fun buildQuarterlyCsv(
    year: Int, quarter: Int,
    transactions: List<Transaction>
): String {
    val shortMonths = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val quarterStartMonth = (quarter - 1) * 3 + 1
    val quarterMonths = quarterStartMonth..(quarterStartMonth + 2)

    data class MonthStats(val month: Int, val income: Double, val expenses: Double) {
        val net get() = income - expenses
    }
    val monthStats = quarterMonths.map { m ->
        val txs = transactions.filter { LocalDate.parse(it.createdAt.take(10)).monthValue == m }
        MonthStats(m, txs.filter { it.isIncome }.sumOf { it.amount }, txs.filter { !it.isIncome }.sumOf { it.amount })
    }

    val totalIncome   = monthStats.sumOf { it.income }
    val totalExpenses = monthStats.sumOf { it.expenses }
    val totalNet      = totalIncome - totalExpenses

    val categoryTotals = transactions.filter { !it.isIncome }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }

    return buildString {
        appendLine("Quarterly Report — Q$quarter $year")
        appendLine()

        appendLine("SUMMARY")
        appendLine("Income,${totalIncome.currency()}")
        appendLine("Expenses,${totalExpenses.currency()}")
        appendLine("Net,${totalNet.currency()}")
        if (totalIncome > 0) appendLine("Savings Rate,${"%.1f".format(totalNet / totalIncome * 100)}%")
        appendLine("Transactions,${transactions.size}")
        appendLine()

        appendLine("MONTH-BY-MONTH")
        appendLine("Month,Income,Expenses,Net")
        monthStats.forEach { s ->
            appendLine("${shortMonths[s.month]},${s.income.currency()},${s.expenses.currency()},${s.net.currency()}")
        }
        appendLine("Total,${totalIncome.currency()},${totalExpenses.currency()},${totalNet.currency()}")
        appendLine()

        appendLine("SPENDING BY CATEGORY")
        appendLine("Category,Amount,% of Expenses")
        categoryTotals.forEach { (cat, amt) ->
            val pct = if (totalExpenses > 0) amt / totalExpenses * 100 else 0.0
            appendLine("${cat.csvField()},${amt.currency()},${"%.1f".format(pct)}%")
        }
        appendLine()

        appendLine("TRANSACTIONS")
        appendLine("Date,Title,Category,Amount,Type,Note")
        transactions.sortedByDescending { it.createdAt }.forEach { tx ->
            appendLine("${tx.createdAt.take(10)},${tx.title.csvField()},${tx.category.csvField()},${tx.amount.currency()},${if (tx.isIncome) "Income" else "Expense"},${(tx.note ?: "").csvField()}")
        }
    }
}

// ── Net worth snapshot ────────────────────────────────────────────────────────

fun buildNetWorthCsv(
    totalAssets: Double, totalLiabilities: Double,
    wallets: List<uniffi.ledger.Wallet>,
    debts: List<uniffi.ledger.Debt>
): String {
    val today = LocalDate.now().toString()
    return buildString {
        appendLine("Net Worth Snapshot — $today")
        appendLine()

        appendLine("SUMMARY")
        appendLine("Total Assets,${totalAssets.currency()}")
        appendLine("Total Liabilities,${totalLiabilities.currency()}")
        appendLine("Net Worth,${(totalAssets - totalLiabilities).currency()}")
        appendLine()

        appendLine("ASSETS")
        appendLine("Wallet,Balance,Description")
        wallets.forEach { w ->
            appendLine("${w.name.csvField()},${w.balance.currency()},${w.description.csvField()}")
        }
        appendLine()

        appendLine("LIABILITIES")
        appendLine("Name,Type,Remaining,Total,APR,Monthly Payment")
        debts.forEach { d ->
            appendLine("${d.name.csvField()},${d.debtType.csvField()},${d.remainingAmount.currency()},${d.totalAmount.currency()},${d.apr}%,${d.monthlyPayment.currency()}")
        }
    }
}

// ── Custom report ─────────────────────────────────────────────────────────────

fun buildCustomCsv(
    startYm: YearMonth, endYm: YearMonth,
    transactions: List<Transaction>,
    grouping: String
): String {
    val ymFmt = DateTimeFormatter.ofPattern("MMM yyyy")
    val income   = transactions.filter { it.isIncome }.sumOf { it.amount }
    val expenses = transactions.filter { !it.isIncome }.sumOf { it.amount }
    val net      = income - expenses

    return buildString {
        appendLine("Custom Report — ${startYm.format(ymFmt)} to ${endYm.format(ymFmt)}")
        appendLine()

        appendLine("SUMMARY")
        appendLine("Income,${income.currency()}")
        appendLine("Expenses,${expenses.currency()}")
        appendLine("Net,${net.currency()}")
        if (income > 0) appendLine("Savings Rate,${"%.1f".format(net / income * 100)}%")
        appendLine("Transactions,${transactions.size}")
        appendLine()

        when (grouping) {
            "By Month" -> {
                appendLine("BY MONTH")
                appendLine("Month,Income,Expenses,Net")
                transactions.groupBy { it.createdAt.take(7) }
                    .entries.sortedBy { it.key }
                    .forEach { (key, txs) ->
                        val (yr, mo) = key.split("-").map { it.toInt() }
                        val label = YearMonth.of(yr, mo).format(ymFmt)
                        val mInc = txs.filter { it.isIncome }.sumOf { it.amount }
                        val mExp = txs.filter { !it.isIncome }.sumOf { it.amount }
                        appendLine("$label,${mInc.currency()},${mExp.currency()},${(mInc - mExp).currency()}")
                    }
            }
            "By Category" -> {
                appendLine("BY CATEGORY (Expenses)")
                appendLine("Category,Amount,% of Expenses")
                transactions.filter { !it.isIncome }
                    .groupBy { it.category }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                    .entries.sortedByDescending { it.value }
                    .forEach { (cat, amt) ->
                        val pct = if (expenses > 0) amt / expenses * 100 else 0.0
                        appendLine("${cat.csvField()},${amt.currency()},${"%.1f".format(pct)}%")
                    }
            }
            "By Week" -> {
                appendLine("BY WEEK")
                appendLine("Week of,Income,Expenses,Net")
                transactions.groupBy {
                    runCatching {
                        LocalDate.parse(it.createdAt.take(10)).with(java.time.DayOfWeek.MONDAY).toString()
                    }.getOrElse { "?" }
                }.entries.sortedBy { it.key }
                    .forEach { (weekStart, txs) ->
                        val label = runCatching {
                            LocalDate.parse(weekStart).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                        }.getOrElse { weekStart }
                        val wInc = txs.filter { it.isIncome }.sumOf { it.amount }
                        val wExp = txs.filter { !it.isIncome }.sumOf { it.amount }
                        appendLine("$label,${wInc.currency()},${wExp.currency()},${(wInc - wExp).currency()}")
                    }
            }
            else -> { // By Day
                appendLine("BY DAY")
                appendLine("Date,Income,Expenses,Net")
                transactions.groupBy { it.createdAt.take(10) }
                    .entries.sortedByDescending { it.key }
                    .forEach { (date, txs) ->
                        val dInc = txs.filter { it.isIncome }.sumOf { it.amount }
                        val dExp = txs.filter { !it.isIncome }.sumOf { it.amount }
                        appendLine("$date,${dInc.currency()},${dExp.currency()},${(dInc - dExp).currency()}")
                    }
            }
        }
        appendLine()

        appendLine("TRANSACTIONS")
        appendLine("Date,Title,Category,Amount,Type,Note")
        transactions.sortedByDescending { it.createdAt }.forEach { tx ->
            appendLine("${tx.createdAt.take(10)},${tx.title.csvField()},${tx.category.csvField()},${tx.amount.currency()},${if (tx.isIncome) "Income" else "Expense"},${(tx.note ?: "").csvField()}")
        }
    }
}
