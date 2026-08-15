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
import uniffi.ledger.Category
import uniffi.ledger.Settlement
import uniffi.ledger.SettlementSuggestion
import uniffi.ledger.SharedExpense
import uniffi.ledger.Wallet
import javax.inject.Inject

data class SharedExpenseUiState(
    val groups: List<ExpenseGroup> = emptyList(),
    // Keyed by group id and loaded when a group is opened, rather than reading every group's
    // expenses up front.
    val members: Map<String, List<GroupMember>> = emptyMap(),
    val expenses: Map<String, List<SharedExpense>> = emptyMap(),
    val shares: Map<String, List<ExpenseShare>> = emptyMap(),
    val settlements: Map<String, List<Settlement>> = emptyMap(),
    // Who the app thinks should pay whom. Recomputed whenever a group is reloaded, because every
    // expense and every payment changes the answer.
    val suggestions: Map<String, List<SettlementSuggestion>> = emptyMap(),
    // Needed by the dialogs the moment a split touches your wallet, so they are loaded with the
    // groups rather than fetched when a dialog opens and arriving a frame late.
    val wallets: List<Wallet> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
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
                _state.value = _state.value.copy(
                    groups = bridge.listExpenseGroups(),
                    wallets = bridge.listWallets(),
                    expenseCategories = bridge.listCategories().filter { it.isExpense },
                    isLoading = false
                )
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
                val settlements = bridge.listSettlements(groupId)
                val suggestions = bridge.suggestSettlements(groupId)
                _state.value = _state.value.copy(
                    members = _state.value.members + (groupId to members),
                    expenses = _state.value.expenses + (groupId to expenses),
                    settlements = _state.value.settlements + (groupId to settlements),
                    suggestions = _state.value.suggestions + (groupId to suggestions)
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

    /**
     * Recording an expense you paid for as one transaction for the **whole** amount plus the split.
     * Not your share — your wallet really did lose all of it, and the reports have to go on saying
     * so.
     */
    fun addExpenseFromWallet(
        groupId: String,
        description: String,
        amountCents: Long,
        paidByMemberId: String,
        walletId: String,
        category: String,
        shares: List<ShareInput>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.addSharedExpenseFromWallet(groupId, description, amountCents, paidByMemberId, walletId, category, shares, null)
                loadGroup(groupId)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun settleToWallet(
        groupId: String,
        fromMemberId: String,
        toMemberId: String,
        amountCents: Long,
        walletId: String,
        category: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.recordSettlementToWallet(groupId, fromMemberId, toMemberId, amountCents, walletId, category, null)
                loadGroup(groupId)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /**
     * [alsoTransaction] is asked for rather than assumed, because the two cases are opposite: the
     * dinner never happened, or it happened and you no longer care who owed what. Guessing wrong
     * either leaves money missing from the wallet or takes back a payment that was real.
     */
    fun deleteExpense(id: String, groupId: String, alsoTransaction: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (alsoTransaction) bridge.deleteSharedExpenseWithTransaction(id)
                else bridge.deleteSharedExpenseKeepingTransaction(id)
                loadGroup(groupId)
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /**
     * Recording that somebody handed money over. The suggested amount is only a default — a partial
     * payment is a normal thing and is recorded as what was actually paid, not as what was owed.
     */
    fun settle(groupId: String, fromMemberId: String, toMemberId: String, amountCents: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.recordSettlement(groupId, fromMemberId, toMemberId, amountCents, null, null)
                loadGroup(groupId)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteSettlement(id: String, groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteSettlement(id)
                loadGroup(groupId)
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
