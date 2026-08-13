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
import uniffi.ledger.ExpenseGroup
import uniffi.ledger.ExpenseShare
import uniffi.ledger.GroupMember
import uniffi.ledger.ShareInput
import uniffi.ledger.SharedExpense
import javax.inject.Inject

data class SharedExpenseUiState(
    val groups: List<ExpenseGroup> = emptyList(),
    // Keyed by group id and loaded when a group is opened, rather than reading every group's
    // expenses up front.
    val members: Map<String, List<GroupMember>> = emptyMap(),
    val expenses: Map<String, List<SharedExpense>> = emptyMap(),
    val shares: Map<String, List<ExpenseShare>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SharedExpenseViewModel @Inject constructor(
    private val bridge: ILedgerBridge
) : ViewModel() {

    private val _state = MutableStateFlow(SharedExpenseUiState())
    val state: StateFlow<SharedExpenseUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = _state.value.copy(groups = bridge.listExpenseGroups(), isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val members = bridge.listGroupMembers(groupId)
                val expenses = bridge.listSharedExpenses(groupId)
                _state.value = _state.value.copy(
                    members = _state.value.members + (groupId to members),
                    expenses = _state.value.expenses + (groupId to expenses)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun loadShares(expenseId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(
                    shares = _state.value.shares + (expenseId to bridge.listExpenseShares(expenseId))
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun createGroup(name: String, emoji: String, colorHex: String, memberNames: List<String>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.createExpenseGroup(name, emoji, colorHex, memberNames)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteGroup(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteExpenseGroup(id)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addMember(groupId: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.addGroupMember(groupId, name)
                loadGroup(groupId)
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /**
     * The shares are worked out on the screen and handed over whole. The Rust side refuses them if
     * they do not add up to the amount, which is what stops a lost cent from quietly unbalancing the
     * group.
     */
    fun addExpense(
        groupId: String,
        description: String,
        amountCents: Long,
        paidByMemberId: String,
        shares: List<ShareInput>,
        occurredAt: String? = null,
        transactionId: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.addSharedExpense(groupId, description, amountCents, paidByMemberId, transactionId, shares, occurredAt)
                loadGroup(groupId)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /**
     * Correcting an entry rather than deleting and retyping it. The shares are handed over whole and
     * replace what was there; the Rust side refuses the lot if they no longer add up, which is why a
     * mistyped correction cannot leave the group half-changed.
     */
    fun updateExpense(
        id: String,
        groupId: String,
        description: String,
        amountCents: Long,
        paidByMemberId: String,
        shares: List<ShareInput>,
        occurredAt: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.updateSharedExpense(id, description, amountCents, paidByMemberId, shares, occurredAt)
                loadShares(id)
                loadGroup(groupId)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun renameGroup(id: String, name: String, emoji: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.updateExpenseGroup(id, name, emoji, "#1565C0")
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteExpense(id: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteSharedExpense(id)
                loadGroup(groupId)
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
