package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.ILedgerBridge
import com.ledger.app.widget.WidgetSnapshot
import com.ledger.app.widget.WidgetSnapshotRepository
import com.ledger.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetSettingsViewModel @Inject constructor(
    private val bridge: ILedgerBridge,
    private val snapshotRepo: WidgetSnapshotRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    val snapshot = snapshotRepo.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, WidgetSnapshot())

    val pinnedCategories = snapshotRepo.pinnedCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _expenseCategories = MutableStateFlow<List<String>>(emptyList())
    val expenseCategories: StateFlow<List<String>> = _expenseCategories.asStateFlow()

    val maxPinned = WidgetSnapshotRepository.MAX_PINNED_CATEGORIES

    init {
        loadCategories()
        refresh()
    }

    private fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            _expenseCategories.value = runCatching {
                bridge.listCategories().filter { it.isExpense }.map { it.name }
            }.getOrDefault(emptyList())
        }
    }

    fun setHideAmounts(value: Boolean) {
        viewModelScope.launch {
            snapshotRepo.setHideAmounts(value)
            widgetUpdater.refresh()
        }
    }

    // Selecting past the cap drops the oldest pick, so the chips stay directly tappable instead of
    // silently doing nothing once two are chosen.
    fun toggleCategory(name: String) {
        viewModelScope.launch {
            val current = snapshotRepo.pinnedCategories.first()
            val next = if (current.any { it.equals(name, ignoreCase = true) }) {
                current.filterNot { it.equals(name, ignoreCase = true) }
            } else {
                (current + name).takeLast(maxPinned)
            }
            snapshotRepo.setPinnedCategories(next)
            widgetUpdater.refresh()
        }
    }

    // Empty list = fall back to the most-used categories of the last 60 days.
    fun useAutomaticCategories() {
        viewModelScope.launch {
            snapshotRepo.setPinnedCategories(emptyList())
            widgetUpdater.refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { widgetUpdater.refresh() }
    }
}
