package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.LedgerBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.ledger.RecurringTransaction
import javax.inject.Inject

data class RecurringUiState(
    val recurring: List<RecurringTransaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val justApplied: List<String> = emptyList()   // titles posted this session
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val bridge: LedgerBridge
) : ViewModel() {

    private val _state = MutableStateFlow(RecurringUiState())
    val state: StateFlow<RecurringUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Apply overdue items first — runs every load so back-dated entries are picked up immediately
                val applied = bridge.applyDueRecurring()
                if (applied.isNotEmpty()) {
                    _state.value = _state.value.copy(justApplied = _state.value.justApplied + applied)
                }
                val recurring = bridge.listRecurring()
                _state.value = _state.value.copy(recurring = recurring, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createRecurring(
        title: String, amount: Double, category: String,
        walletId: String, isIncome: Boolean, frequency: String, nextDate: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.createRecurring(title, amount, category, walletId, isIncome, frequency, nextDate)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateRecurring(
        id: String, title: String, amount: Double,
        category: String, frequency: String, nextDate: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.updateRecurring(id, title, amount, category, frequency, nextDate)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteRecurring(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteRecurring(id)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearApplied() { _state.value = _state.value.copy(justApplied = emptyList()) }
}
