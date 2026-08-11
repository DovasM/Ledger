package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.ILedgerBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.ledger.Debt
import uniffi.ledger.DebtPayment
import javax.inject.Inject

data class DebtUiState(
    val debts: List<Debt> = emptyList(),
    // Keyed by debt id and loaded when a debt's history is opened, not for every debt up front.
    val payments: Map<String, List<DebtPayment>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val bridge: ILedgerBridge
) : ViewModel() {

    private val _state = MutableStateFlow(DebtUiState())
    val state: StateFlow<DebtUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val debts = bridge.listDebts()
                _state.value = _state.value.copy(debts = debts, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createDebt(
        name: String, debtType: String, totalAmountCents: Long,
        remainingAmountCents: Long, apr: Double, monthlyPaymentCents: Long,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.createDebt(name, debtType, totalAmountCents, remainingAmountCents, apr, monthlyPaymentCents)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateDebt(
        id: String, name: String, debtType: String, totalAmountCents: Long,
        remainingAmountCents: Long, apr: Double, monthlyPaymentCents: Long,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.updateDebt(id, name, debtType, totalAmountCents, remainingAmountCents, apr, monthlyPaymentCents)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteDebt(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteDebt(id)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun loadPayments(debtId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = bridge.listDebtPayments(debtId)
                _state.value = _state.value.copy(payments = _state.value.payments + (debtId to rows))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /** Recording a payment is how the remaining amount goes down; it is no longer typed over. */
    fun addPayment(debtId: String, amountCents: Long, note: String? = null, occurredAt: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.addDebtPayment(debtId, amountCents, note, occurredAt)
                load()
                loadPayments(debtId)
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deletePayment(id: String, debtId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteDebtPayment(id)
                load()
                loadPayments(debtId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
