package com.ledger.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ledger.app.ui.util.DayState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Widgets never touch the Rust bridge. The app computes everything a widget needs and parks it
// here; a widget refresh is then a preference read instead of opening SQLite from a broadcast
// receiver. It also keeps widgets correct when the app process is cold.
//
// Amounts are stored together with the currency code they were computed in, so introducing
// multi-currency wallets later changes WidgetUpdater only — no widget code.

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger_widget")

data class CategoryShortcut(val name: String, val iconName: String)

// Everything a single-category widget instance needs. Every budgeted category is carried, because
// which one a given widget shows is per-instance state the snapshot can't know.
data class CategoryAllowance(
    val name: String,
    val todayAllowance: Double,
    val spentToday: Double,
    val periodRemaining: Double,
    val periodLabel: String
) {
    val remainingToday: Double get() = todayAllowance - spentToday
}

data class WidgetSnapshot(
    val currency: String = "USD",
    val numberFormat: Int = 0,
    val hideAmounts: Boolean = false,
    val aiEnabled: Boolean = true,
    val totalBalance: Double = 0.0,
    val spentToday: Double = 0.0,
    val todayAllowance: Double = 0.0,
    val monthSpent: Double = 0.0,
    val unbudgetedToday: Double = 0.0,

    // The plain daily share, before any carried surplus/deficit. The widget shows the difference
    // against todayAllowance, which is the per-day effect a user can act on.
    val baseDaily: Double = 0.0,
    // The budgeted category furthest through its limit — shown on the all-budgets widget regardless
    // of whether it is alerting yet; tightestAlerting only drives the colour.
    val tightestCategory: String? = null,
    val tightestRemaining: Double = 0.0,
    val tightestAlerting: Boolean = false,
    val categoryAllowances: List<CategoryAllowance> = emptyList(),
    val streakCurrent: Int = 0,
    val streakBest: Int = 0,
    val weekGrid: List<DayState> = List(7) { DayState.Empty },
    val topCategories: List<CategoryShortcut> = emptyList(),
    val hasData: Boolean = false
) {
    val hasAllowance: Boolean get() = todayAllowance > 0
    val remainingToday: Double get() = todayAllowance - spentToday
}

@Singleton
class WidgetSnapshotRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.widgetDataStore

    companion object {
        private val KEY_CURRENCY       = stringPreferencesKey("w_currency")
        private val KEY_NUMBER_FORMAT  = intPreferencesKey("w_number_format")
        private val KEY_HIDE_AMOUNTS   = booleanPreferencesKey("w_hide_amounts")
        private val KEY_AI_ENABLED     = booleanPreferencesKey("w_ai_enabled")
        private val KEY_TOTAL_BALANCE  = doublePreferencesKey("w_total_balance")
        private val KEY_SPENT_TODAY    = doublePreferencesKey("w_spent_today")
        private val KEY_TODAY_ALLOW    = doublePreferencesKey("w_today_allowance")
        private val KEY_MONTH_SPENT    = doublePreferencesKey("w_month_spent")
        private val KEY_UNBUDGETED     = doublePreferencesKey("w_unbudgeted_today")
        private val KEY_BASE_DAILY     = doublePreferencesKey("w_base_daily")
        private val KEY_TIGHT_NAME     = stringPreferencesKey("w_tightest_name")
        private val KEY_TIGHT_LEFT     = doublePreferencesKey("w_tightest_remaining")
        private val KEY_TIGHT_ALERT    = booleanPreferencesKey("w_tightest_alerting")
        private val KEY_CAT_ALLOWANCES = stringPreferencesKey("w_category_allowances")
        private val KEY_STREAK_CURRENT = intPreferencesKey("w_streak_current")
        private val KEY_STREAK_BEST    = intPreferencesKey("w_streak_best")
        private val KEY_WEEK_GRID      = stringPreferencesKey("w_week_grid")
        private val KEY_TOP_CATEGORIES = stringPreferencesKey("w_top_categories")
        private val KEY_PINNED_CATS    = stringPreferencesKey("w_pinned_categories")
        private val KEY_HAS_DATA       = booleanPreferencesKey("w_has_data")

        // The quick-add row fits two shortcuts next to the add and scan buttons.
        const val MAX_PINNED_CATEGORIES = 2

        // ASCII record/unit separators - category names can contain anything a user types.
        private const val RECORD_SEP = '\u001E'
        private const val FIELD_SEP = '\u001F'
    }

    val snapshot: Flow<WidgetSnapshot> = ds.data.map { p -> p.toSnapshot() }

    // hideAmounts and pinnedCategories are user settings rather than derived data — write() never
    // touches their keys, so they survive every refresh.
    val hideAmounts: Flow<Boolean> = ds.data.map { it[KEY_HIDE_AMOUNTS] ?: false }

    // Empty means "pick automatically from recent spending".
    val pinnedCategories: Flow<List<String>> = ds.data.map { p ->
        (p[KEY_PINNED_CATS] ?: "").split(RECORD_SEP).filter { it.isNotBlank() }
    }

    suspend fun setHideAmounts(value: Boolean) = ds.edit { it[KEY_HIDE_AMOUNTS] = value }

    suspend fun setPinnedCategories(names: List<String>) = ds.edit {
        it[KEY_PINNED_CATS] = names.take(MAX_PINNED_CATEGORIES).joinToString(RECORD_SEP.toString())
    }

    suspend fun write(snapshot: WidgetSnapshot) {
        ds.edit { p ->
            p[KEY_CURRENCY]       = snapshot.currency
            p[KEY_NUMBER_FORMAT]  = snapshot.numberFormat
            p[KEY_AI_ENABLED]     = snapshot.aiEnabled
            p[KEY_TOTAL_BALANCE]  = snapshot.totalBalance
            p[KEY_SPENT_TODAY]    = snapshot.spentToday
            p[KEY_TODAY_ALLOW]    = snapshot.todayAllowance
            p[KEY_MONTH_SPENT]    = snapshot.monthSpent
            p[KEY_UNBUDGETED]     = snapshot.unbudgetedToday
            p[KEY_BASE_DAILY]     = snapshot.baseDaily
            p[KEY_TIGHT_NAME]     = snapshot.tightestCategory ?: ""
            p[KEY_TIGHT_LEFT]     = snapshot.tightestRemaining
            p[KEY_TIGHT_ALERT]    = snapshot.tightestAlerting
            p[KEY_CAT_ALLOWANCES] = snapshot.categoryAllowances.joinToString(RECORD_SEP.toString()) {
                listOf(it.name, it.todayAllowance, it.spentToday, it.periodRemaining, it.periodLabel)
                    .joinToString(FIELD_SEP.toString())
            }
            p[KEY_STREAK_CURRENT] = snapshot.streakCurrent
            p[KEY_STREAK_BEST]    = snapshot.streakBest
            p[KEY_WEEK_GRID]      = snapshot.weekGrid.joinToString("") { it.code() }
            p[KEY_TOP_CATEGORIES] = snapshot.topCategories.joinToString(RECORD_SEP.toString()) {
                "${it.name}$FIELD_SEP${it.iconName}"
            }
            p[KEY_HAS_DATA]       = snapshot.hasData
        }
    }

    private fun Preferences.toSnapshot(): WidgetSnapshot {
        val grid = (this[KEY_WEEK_GRID] ?: "").padEnd(7, 'E').take(7).map { it.toDayState() }
        val cats = (this[KEY_TOP_CATEGORIES] ?: "")
            .split(RECORD_SEP)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_SEP)
                if (parts.size == 2) CategoryShortcut(parts[0], parts[1]) else null
            }
        return WidgetSnapshot(
            currency       = this[KEY_CURRENCY]       ?: "USD",
            numberFormat   = this[KEY_NUMBER_FORMAT]  ?: 0,
            hideAmounts    = this[KEY_HIDE_AMOUNTS]   ?: false,
            aiEnabled      = this[KEY_AI_ENABLED]     ?: true,
            totalBalance   = this[KEY_TOTAL_BALANCE]  ?: 0.0,
            spentToday     = this[KEY_SPENT_TODAY]    ?: 0.0,
            todayAllowance = this[KEY_TODAY_ALLOW]    ?: 0.0,
            monthSpent     = this[KEY_MONTH_SPENT]    ?: 0.0,
            unbudgetedToday = this[KEY_UNBUDGETED]    ?: 0.0,
            baseDaily       = this[KEY_BASE_DAILY]     ?: 0.0,
            tightestCategory = this[KEY_TIGHT_NAME]?.takeIf { it.isNotBlank() },
            tightestRemaining = this[KEY_TIGHT_LEFT]  ?: 0.0,
            tightestAlerting = this[KEY_TIGHT_ALERT]  ?: false,
            categoryAllowances = (this[KEY_CAT_ALLOWANCES] ?: "")
                .split(RECORD_SEP)
                .filter { it.isNotBlank() }
                .mapNotNull { entry ->
                    val f = entry.split(FIELD_SEP)
                    if (f.size != 5) return@mapNotNull null
                    CategoryAllowance(
                        name = f[0],
                        todayAllowance = f[1].toDoubleOrNull() ?: return@mapNotNull null,
                        spentToday = f[2].toDoubleOrNull() ?: 0.0,
                        periodRemaining = f[3].toDoubleOrNull() ?: 0.0,
                        periodLabel = f[4]
                    )
                },
            streakCurrent  = this[KEY_STREAK_CURRENT] ?: 0,
            streakBest     = this[KEY_STREAK_BEST]    ?: 0,
            weekGrid       = grid,
            topCategories  = cats,
            hasData        = this[KEY_HAS_DATA]       ?: false
        )
    }
}

private fun DayState.code(): String = when (this) {
    DayState.Future -> "F"
    DayState.Good   -> "G"
    DayState.Over   -> "O"
    DayState.Empty  -> "E"
}

private fun Char.toDayState(): DayState = when (this) {
    'F'  -> DayState.Future
    'G'  -> DayState.Good
    'O'  -> DayState.Over
    else -> DayState.Empty
}
