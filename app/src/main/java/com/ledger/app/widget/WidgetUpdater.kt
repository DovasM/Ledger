package com.ledger.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ledger.app.data.ILedgerBridge
import com.ledger.app.data.PreferencesRepository
import com.ledger.app.ui.util.AllowanceSettings
import com.ledger.app.ui.util.computeStreakStats
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// Entry point for widget receivers, which Hilt cannot inject into directly.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetSnapshotRepository(): WidgetSnapshotRepository
    fun widgetUpdater(): WidgetUpdater
}

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: ILedgerBridge,
    private val prefs: PreferencesRepository,
    private val snapshotRepo: WidgetSnapshotRepository
) {
    // Recomputes the snapshot from the DB and pushes it to every placed widget. Best-effort:
    // a widget showing slightly stale numbers is better than a crash in a background path.
    suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) { runCatching { build() }.getOrNull() } ?: return
        snapshotRepo.write(snapshot)
        runCatching {
            QuickAddWidget().updateAll(context)
            DailyAllowanceWidget().updateAll(context)
            StreakWidget().updateAll(context)
        }
    }

    private suspend fun build(): WidgetSnapshot {
        val today = LocalDate.now()
        val transactions = bridge.listAllTransactions(limit = 10000u)
        val budgets = runCatching { bridge.listBudgets() }.getOrDefault(emptyList())
        val wallets = runCatching { bridge.listWallets() }.getOrDefault(emptyList())
        val categories = runCatching { bridge.listCategories() }.getOrDefault(emptyList())

        val stats = computeStreakStats(
            transactions, budgets, categories, today,
            AllowanceSettings.of(prefs.allowanceRollover.first(), prefs.allowanceWindow.first()),
            offBudgetWalletIds = wallets.filter { it.offBudget }.map { it.id }.toSet()
        )

        val monthPrefix = "%04d-%02d".format(today.year, today.monthValue)
        val monthSpent = transactions
            .filter { !it.isIncome && it.occurredAt.startsWith(monthPrefix) }
            .sumOf { it.amount }
        val tightest = stats.tightestCategory

        val iconByName = categories.associateBy({ it.name.lowercase() }, { it.iconName })
        fun shortcut(name: String) = CategoryShortcut(name, iconByName[name.lowercase()] ?: "shopping_bag")

        // The user's own picks win. A pinned category that has since been renamed or deleted is
        // dropped rather than shown as a dead shortcut.
        val pinned = snapshotRepo.pinnedCategories.first()
            .filter { name -> categories.any { it.name.equals(name, ignoreCase = true) } }

        // Falling back to recent behaviour, not all-time totals — the categories someone reached for
        // last month are the ones they are likely to reach for now.
        val since = today.minusDays(60).toString()
        val topCategories = if (pinned.isNotEmpty()) {
            pinned.map { shortcut(it) }
        } else {
            transactions
                .filter { !it.isIncome && it.occurredAt.take(10) >= since }
                .groupingBy { it.category }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(WidgetSnapshotRepository.MAX_PINNED_CATEGORIES)
                .map { shortcut(it.key) }
        }

        return WidgetSnapshot(
            currency       = prefs.currencyCode.first(),
            numberFormat   = prefs.numberFormatIndex.first(),
            hideAmounts    = snapshotRepo.hideAmounts.first(),
            aiEnabled      = prefs.aiEnabled.first(),
            totalBalance   = wallets.sumOf { it.balance },
            spentToday     = stats.spentToday,
            todayAllowance = stats.todayAllowance,
            monthSpent     = monthSpent,
            unbudgetedToday   = stats.unbudgetedToday,
            baseDaily         = stats.dailyAllowance,
            tightestCategory  = tightest?.name,
            tightestRemaining = tightest?.remaining ?: 0.0,
            tightestAlerting  = tightest?.isAlerting ?: false,
            categoryAllowances = stats.categoryPaces.map {
                CategoryAllowance(
                    name = it.name,
                    todayAllowance = it.todayDaily,
                    spentToday = it.spentToday,
                    periodRemaining = it.remaining,
                    periodLabel = it.period.label.lowercase().removeSuffix("ly")
                )
            },
            streakCurrent  = stats.currentStreak,
            streakBest     = stats.bestStreak,
            weekGrid       = stats.weekGrid(),
            topCategories  = topCategories,
            hasData        = transactions.isNotEmpty()
        )
    }
}
