package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.ILedgerBridge
import com.ledger.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.ledger.Transfer
import javax.inject.Inject

data class TransferUiState(
    val transfers: List<Transfer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// Transfers are deliberately not transactions: they move balance between wallets without ever
// touching income or expense totals. See the Transfers section of project.md.
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val bridge: ILedgerBridge,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = _state.value.copy(transfers = bridge.listTransfers(), isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createTransfer(
        fromWalletId: String,
        toWalletId: String,
        amountCents: Long,
        note: String?,
        createdAt: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.createTransfer(fromWalletId, toWalletId, amountCents, note, createdAt)
                load()
                // Wallet balances moved, so the home-screen figures are stale.
                widgetUpdater.refresh()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteTransfer(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteTransfer(id)
                load()
                widgetUpdater.refresh()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
