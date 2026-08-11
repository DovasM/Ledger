package com.ledger.app.data

import uniffi.ledger.RecurringTransaction
import java.time.LocalDate

/**
 * When a recurring item is due, and how many times.
 *
 * This is separated from `applyDueRecurring` because the two halves fail differently: writing the
 * rows is ordinary database work, while *deciding* what to write is calendar arithmetic that has to
 * survive missed months, leap years and month ends. Only the second half is worth testing, and it
 * cannot be tested while it is entangled with the FFI.
 */
data class RecurringPost(
    val recurringId: String,
    val title: String,
    val category: String,
    val walletId: String,
    val amountCents: Long,
    val isIncome: Boolean,
    /** The date the money was due — not today. A missed month posts under the month it belonged to. */
    val occurredOn: LocalDate
)

data class RecurringPlan(
    val posts: List<RecurringPost>,
    /** New `next_date` per recurring id, for the ones that moved. */
    val advancedTo: Map<String, LocalDate>
)

fun advanceRecurringDate(date: LocalDate, frequency: String): LocalDate = when (frequency.lowercase()) {
    "daily"     -> date.plusDays(1)
    "weekly"    -> date.plusWeeks(1)
    "biweekly"  -> date.plusWeeks(2)
    "monthly"   -> date.plusMonths(1)
    "quarterly" -> date.plusMonths(3)
    "yearly"    -> date.plusYears(1)
    // An unrecognised frequency is treated as monthly rather than skipped: a recurring item that
    // silently never posts is worse than one that posts on a defensible schedule.
    else        -> date.plusMonths(1)
}

/**
 * Every occurrence due on or before [today], in order, plus where each item's next date lands.
 *
 * An item missed for three months posts three times, each dated the day it was actually due, so the
 * reports show the money in the month it belonged to.
 */
fun planDueRecurring(items: List<RecurringTransaction>, today: LocalDate): RecurringPlan {
    val posts = mutableListOf<RecurringPost>()
    val advanced = mutableMapOf<String, LocalDate>()

    for (r in items) {
        // A date the app cannot read is left alone rather than guessed at.
        var due = runCatching { LocalDate.parse(r.nextDate.take(10)) }.getOrNull() ?: continue
        val start = due
        while (!due.isAfter(today)) {
            posts += RecurringPost(
                recurringId = r.id,
                title = r.title,
                category = r.category,
                walletId = r.walletId,
                amountCents = r.amountCents,
                isIncome = r.isIncome,
                occurredOn = due
            )
            due = advanceRecurringDate(due, r.frequency)
        }
        if (due != start) advanced[r.id] = due
    }
    return RecurringPlan(posts, advanced)
}
