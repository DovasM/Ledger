package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository
) : ViewModel() {

    // ── State flows ───────────────────────────────────────────────────────────

    val currencyCode    = prefs.currencyCode.stateIn(viewModelScope, SharingStarted.Eagerly, "USD")
    val accentIndex     = prefs.accentIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val densityIndex    = prefs.densityIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val homeTabIndex    = prefs.homeTabIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val numberFormatIndex = prefs.numberFormatIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val darkMode        = prefs.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notifMaster     = prefs.notifMaster.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifBudget     = prefs.notifBudget.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifBudgetPct  = prefs.notifBudgetPct.stateIn(viewModelScope, SharingStarted.Eagerly, "80")
    val notifBills      = prefs.notifBills.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifBillDays   = prefs.notifBillDays.stateIn(viewModelScope, SharingStarted.Eagerly, "3")
    val notifWeekly     = prefs.notifWeekly.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifWeeklyDay  = prefs.notifWeeklyDay.stateIn(viewModelScope, SharingStarted.Eagerly, "Sunday")
    val notifUnusual    = prefs.notifUnusual.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifSensitivity= prefs.notifSensitivity.stateIn(viewModelScope, SharingStarted.Eagerly, "Medium")
    val notifInvestment = prefs.notifInvestment.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifGoals      = prefs.notifGoals.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifRecurring  = prefs.notifRecurring.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val secBiometric    = prefs.secBiometric.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val secPinEnabled   = prefs.secPinEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val secAutoLock     = prefs.secAutoLock.stateIn(viewModelScope, SharingStarted.Eagerly, "1 min")
    val secHideSwitcher = prefs.secHideSwitcher.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val secAuthLarge    = prefs.secAuthLarge.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val secLargeAmount  = prefs.secLargeAmount.stateIn(viewModelScope, SharingStarted.Eagerly, "500")

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setCurrency(v: String)         = viewModelScope.launch { prefs.setCurrency(v) }
    fun setAccentIndex(v: Int)         = viewModelScope.launch { prefs.setAccentIndex(v) }
    fun setDensityIndex(v: Int)        = viewModelScope.launch { prefs.setDensityIndex(v) }
    fun setHomeTabIndex(v: Int)        = viewModelScope.launch { prefs.setHomeTabIndex(v) }
    fun setNumberFormatIndex(v: Int)   = viewModelScope.launch { prefs.setNumberFormatIndex(v) }
    fun setDarkMode(v: Boolean)        = viewModelScope.launch { prefs.setDarkMode(v) }

    fun setNotifMaster(v: Boolean)     = viewModelScope.launch { prefs.setNotifMaster(v) }
    fun setNotifBudget(v: Boolean)     = viewModelScope.launch { prefs.setNotifBudget(v) }
    fun setNotifBudgetPct(v: String)   = viewModelScope.launch { prefs.setNotifBudgetPct(v) }
    fun setNotifBills(v: Boolean)      = viewModelScope.launch { prefs.setNotifBills(v) }
    fun setNotifBillDays(v: String)    = viewModelScope.launch { prefs.setNotifBillDays(v) }
    fun setNotifWeekly(v: Boolean)     = viewModelScope.launch { prefs.setNotifWeekly(v) }
    fun setNotifWeeklyDay(v: String)   = viewModelScope.launch { prefs.setNotifWeeklyDay(v) }
    fun setNotifUnusual(v: Boolean)    = viewModelScope.launch { prefs.setNotifUnusual(v) }
    fun setNotifSensitivity(v: String) = viewModelScope.launch { prefs.setNotifSensitivity(v) }
    fun setNotifInvestment(v: Boolean) = viewModelScope.launch { prefs.setNotifInvestment(v) }
    fun setNotifGoals(v: Boolean)      = viewModelScope.launch { prefs.setNotifGoals(v) }
    fun setNotifRecurring(v: Boolean)  = viewModelScope.launch { prefs.setNotifRecurring(v) }

    fun setSecBiometric(v: Boolean)    = viewModelScope.launch { prefs.setSecBiometric(v) }
    fun setSecPinEnabled(v: Boolean)   = viewModelScope.launch { prefs.setSecPinEnabled(v) }
    fun setSecAutoLock(v: String)      = viewModelScope.launch { prefs.setSecAutoLock(v) }
    fun setSecHideSwitcher(v: Boolean) = viewModelScope.launch { prefs.setSecHideSwitcher(v) }
    fun setSecAuthLarge(v: Boolean)    = viewModelScope.launch { prefs.setSecAuthLarge(v) }
    fun setSecLargeAmount(v: String)   = viewModelScope.launch { prefs.setSecLargeAmount(v) }
}
