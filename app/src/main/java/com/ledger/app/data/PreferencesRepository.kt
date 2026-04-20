package com.ledger.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger_settings")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
        // General
        val KEY_CURRENCY           = stringPreferencesKey("currency_code")
        // Appearance
        val KEY_ACCENT_INDEX       = intPreferencesKey("accent_index")
        val KEY_DENSITY_INDEX      = intPreferencesKey("density_index")
        val KEY_HOME_TAB_INDEX     = intPreferencesKey("home_tab_index")
        val KEY_NUMBER_FORMAT      = intPreferencesKey("number_format_index")
        val KEY_DARK_MODE          = booleanPreferencesKey("dark_mode")
        // Notifications
        val KEY_NOTIF_MASTER       = booleanPreferencesKey("notif_master")
        val KEY_NOTIF_BUDGET       = booleanPreferencesKey("notif_budget")
        val KEY_NOTIF_BUDGET_PCT   = stringPreferencesKey("notif_budget_pct")
        val KEY_NOTIF_BILLS        = booleanPreferencesKey("notif_bills")
        val KEY_NOTIF_BILL_DAYS    = stringPreferencesKey("notif_bill_days")
        val KEY_NOTIF_WEEKLY       = booleanPreferencesKey("notif_weekly")
        val KEY_NOTIF_WEEKLY_DAY   = stringPreferencesKey("notif_weekly_day")
        val KEY_NOTIF_UNUSUAL      = booleanPreferencesKey("notif_unusual")
        val KEY_NOTIF_SENSITIVITY  = stringPreferencesKey("notif_sensitivity")
        val KEY_NOTIF_INVESTMENT   = booleanPreferencesKey("notif_investment")
        val KEY_NOTIF_GOALS        = booleanPreferencesKey("notif_goals")
        val KEY_NOTIF_RECURRING    = booleanPreferencesKey("notif_recurring")
        // Security
        val KEY_SEC_BIOMETRIC      = booleanPreferencesKey("sec_biometric")
        val KEY_SEC_PIN_ENABLED    = booleanPreferencesKey("sec_pin_enabled")
        val KEY_SEC_AUTO_LOCK      = stringPreferencesKey("sec_auto_lock")
        val KEY_SEC_HIDE_SWITCHER  = booleanPreferencesKey("sec_hide_switcher")
        val KEY_SEC_AUTH_LARGE     = booleanPreferencesKey("sec_auth_large")
        val KEY_SEC_LARGE_AMOUNT   = stringPreferencesKey("sec_large_amount")
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val currencyCode: Flow<String>    = ds.data.map { it[KEY_CURRENCY]          ?: "USD" }
    val accentIndex: Flow<Int>        = ds.data.map { it[KEY_ACCENT_INDEX]       ?: 0 }
    val densityIndex: Flow<Int>       = ds.data.map { it[KEY_DENSITY_INDEX]      ?: 1 }
    val homeTabIndex: Flow<Int>       = ds.data.map { it[KEY_HOME_TAB_INDEX]     ?: 0 }
    val numberFormatIndex: Flow<Int>  = ds.data.map { it[KEY_NUMBER_FORMAT]      ?: 0 }
    val darkMode: Flow<Boolean>       = ds.data.map { it[KEY_DARK_MODE]          ?: false }

    val notifMaster: Flow<Boolean>    = ds.data.map { it[KEY_NOTIF_MASTER]       ?: true }
    val notifBudget: Flow<Boolean>    = ds.data.map { it[KEY_NOTIF_BUDGET]       ?: true }
    val notifBudgetPct: Flow<String>  = ds.data.map { it[KEY_NOTIF_BUDGET_PCT]   ?: "80" }
    val notifBills: Flow<Boolean>     = ds.data.map { it[KEY_NOTIF_BILLS]        ?: true }
    val notifBillDays: Flow<String>   = ds.data.map { it[KEY_NOTIF_BILL_DAYS]    ?: "3" }
    val notifWeekly: Flow<Boolean>    = ds.data.map { it[KEY_NOTIF_WEEKLY]       ?: true }
    val notifWeeklyDay: Flow<String>  = ds.data.map { it[KEY_NOTIF_WEEKLY_DAY]   ?: "Sunday" }
    val notifUnusual: Flow<Boolean>   = ds.data.map { it[KEY_NOTIF_UNUSUAL]      ?: true }
    val notifSensitivity: Flow<String> = ds.data.map { it[KEY_NOTIF_SENSITIVITY]  ?: "Medium" }
    val notifInvestment: Flow<Boolean> = ds.data.map { it[KEY_NOTIF_INVESTMENT]   ?: false }
    val notifGoals: Flow<Boolean>     = ds.data.map { it[KEY_NOTIF_GOALS]        ?: true }
    val notifRecurring: Flow<Boolean> = ds.data.map { it[KEY_NOTIF_RECURRING]    ?: true }

    val secBiometric: Flow<Boolean>   = ds.data.map { it[KEY_SEC_BIOMETRIC]      ?: false }
    val secPinEnabled: Flow<Boolean>  = ds.data.map { it[KEY_SEC_PIN_ENABLED]    ?: false }
    val secAutoLock: Flow<String>     = ds.data.map { it[KEY_SEC_AUTO_LOCK]      ?: "1 min" }
    val secHideSwitcher: Flow<Boolean> = ds.data.map { it[KEY_SEC_HIDE_SWITCHER]  ?: true }
    val secAuthLarge: Flow<Boolean>   = ds.data.map { it[KEY_SEC_AUTH_LARGE]     ?: false }
    val secLargeAmount: Flow<String>  = ds.data.map { it[KEY_SEC_LARGE_AMOUNT]   ?: "500" }

    // ── Setters ───────────────────────────────────────────────────────────────

    suspend fun setCurrency(value: String)          = ds.edit { it[KEY_CURRENCY]          = value }
    suspend fun setAccentIndex(value: Int)          = ds.edit { it[KEY_ACCENT_INDEX]       = value }
    suspend fun setDensityIndex(value: Int)         = ds.edit { it[KEY_DENSITY_INDEX]      = value }
    suspend fun setHomeTabIndex(value: Int)         = ds.edit { it[KEY_HOME_TAB_INDEX]     = value }
    suspend fun setNumberFormatIndex(value: Int)    = ds.edit { it[KEY_NUMBER_FORMAT]      = value }
    suspend fun setDarkMode(value: Boolean)         = ds.edit { it[KEY_DARK_MODE]          = value }

    suspend fun setNotifMaster(value: Boolean)      = ds.edit { it[KEY_NOTIF_MASTER]       = value }
    suspend fun setNotifBudget(value: Boolean)      = ds.edit { it[KEY_NOTIF_BUDGET]       = value }
    suspend fun setNotifBudgetPct(value: String)    = ds.edit { it[KEY_NOTIF_BUDGET_PCT]   = value }
    suspend fun setNotifBills(value: Boolean)       = ds.edit { it[KEY_NOTIF_BILLS]        = value }
    suspend fun setNotifBillDays(value: String)     = ds.edit { it[KEY_NOTIF_BILL_DAYS]    = value }
    suspend fun setNotifWeekly(value: Boolean)      = ds.edit { it[KEY_NOTIF_WEEKLY]       = value }
    suspend fun setNotifWeeklyDay(value: String)    = ds.edit { it[KEY_NOTIF_WEEKLY_DAY]   = value }
    suspend fun setNotifUnusual(value: Boolean)     = ds.edit { it[KEY_NOTIF_UNUSUAL]      = value }
    suspend fun setNotifSensitivity(value: String)  = ds.edit { it[KEY_NOTIF_SENSITIVITY]  = value }
    suspend fun setNotifInvestment(value: Boolean)  = ds.edit { it[KEY_NOTIF_INVESTMENT]   = value }
    suspend fun setNotifGoals(value: Boolean)       = ds.edit { it[KEY_NOTIF_GOALS]        = value }
    suspend fun setNotifRecurring(value: Boolean)   = ds.edit { it[KEY_NOTIF_RECURRING]    = value }

    suspend fun setSecBiometric(value: Boolean)     = ds.edit { it[KEY_SEC_BIOMETRIC]      = value }
    suspend fun setSecPinEnabled(value: Boolean)    = ds.edit { it[KEY_SEC_PIN_ENABLED]    = value }
    suspend fun setSecAutoLock(value: String)       = ds.edit { it[KEY_SEC_AUTO_LOCK]      = value }
    suspend fun setSecHideSwitcher(value: Boolean)  = ds.edit { it[KEY_SEC_HIDE_SWITCHER]  = value }
    suspend fun setSecAuthLarge(value: Boolean)     = ds.edit { it[KEY_SEC_AUTH_LARGE]     = value }
    suspend fun setSecLargeAmount(value: String)    = ds.edit { it[KEY_SEC_LARGE_AMOUNT]   = value }
}
