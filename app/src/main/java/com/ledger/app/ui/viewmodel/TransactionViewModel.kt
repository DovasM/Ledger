package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.GemmaRepository
import com.ledger.app.data.ILedgerBridge
import com.ledger.app.ui.util.capitalizeFirst
import com.ledger.app.ui.util.normalizeCategoryName
import com.ledger.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.ledger.MonthSummary
import uniffi.ledger.ShareInput
import uniffi.ledger.Transaction
import kotlin.math.abs
import javax.inject.Inject

data class TransactionUiState(
    val transactions: List<Transaction> = emptyList(),
    val monthSummary: MonthSummary? = null,
    val offBudgetWalletIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    // `transactions` stays complete on purpose — editing, search and the transaction list must be
    // able to reach an off-budget row by id. Analysis screens ask for this instead, so excluding a
    // work account cannot accidentally hide a transaction from the screens that must show it.
    fun forReports(includeOffBudget: Boolean): List<Transaction> =
        if (includeOffBudget || offBudgetWalletIds.isEmpty()) transactions
        else transactions.filter { it.walletId !in offBudgetWalletIds }
}

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val bridge: ILedgerBridge,
    private val gemmaRepo: GemmaRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionUiState())
    val state: StateFlow<TransactionUiState> = _state.asStateFlow()

    // One native inference engine → serialize category suggestions (same as ReceiptViewModel).
    private val inferenceMutex = Mutex()

    // One product line the user is turning into its own transaction (split mode).
    data class LineItem(val title: String, val amountCents: Long, val category: String)

    // Colors for auto-created categories (mirrors ReceiptViewModel / CategoryIcons hexes).
    private val palette = listOf(
        "#00513F", "#920009", "#1565C0", "#E65100", "#6A1B9A",
        "#00838F", "#558B2F", "#F9A825", "#4E342E"
    )

    init { loadAll() }

    fun loadAll(limit: UInt = 10000u, offset: UInt = 0u) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val txns = bridge.listAllTransactions(limit, offset)
                val now = java.time.LocalDate.now()
                val summary = try { bridge.getMonthSummary(now.year, now.monthValue) } catch (e: Exception) { null }
                val offBudget = runCatching {
                    bridge.listWallets().filter { it.offBudget }.map { it.id }.toSet()
                }.getOrDefault(emptySet())
                _state.value = _state.value.copy(
                    transactions = txns, monthSummary = summary,
                    offBudgetWalletIds = offBudget, isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadForWallet(walletId: String, limit: UInt = 50u, offset: UInt = 0u) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val txns = bridge.listTransactions(walletId, limit, offset)
                _state.value = _state.value.copy(transactions = txns, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createTransaction(
        walletId: String, title: String, category: String,
        amountCents: Long, isIncome: Boolean, note: String?,
        occurredAt: String? = null,
        tagNames: List<String> = emptyList(),
        // Set when this expense was shared. The split is written against the transaction that was
        // just saved rather than typed a second time on another screen.
        splitIntoGroupId: String? = null,
        splitShares: List<ShareInput> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tx = bridge.createTransaction(walletId, title, category, amountCents, isIncome, note, occurredAt)
                for (name in tagNames) {
                    val tag = bridge.createTag(name)
                    bridge.addTagToTransaction(tx.id, tag.id)
                }
                // After the transaction, because the split has to name it. A split that fails
                // surfaces as an error over a transaction that is already saved and correct — the
                // transaction is the fact that matters, and it is not thrown away because the
                // bookkeeping beside it did not take.
                if (splitIntoGroupId != null && splitShares.isNotEmpty()) {
                    bridge.splitTransaction(tx.id, splitIntoGroupId, splitShares)
                }
                loadAll()
                widgetUpdater.refresh()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // Split mode: creates one transaction per line item, matching each category case-insensitively
    // against existing categories and auto-creating any missing one. The shared wallet/date/note/tags
    // apply to every created transaction. Mirrors ReceiptViewModel.confirmAndCreate.
    fun createSplitTransactions(
        walletId: String,
        items: List<LineItem>,
        isIncome: Boolean,
        note: String?,
        occurredAt: String? = null,
        tagNames: List<String> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        val valid = items.filter { it.amountCents > 0 }
        if (valid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val byName = runCatching { bridge.listCategories() }.getOrDefault(emptyList())
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()
                for (item in valid) {
                    val catName = normalizeCategoryName(item.category).ifBlank { "Other" }
                    val key = catName.lowercase()
                    val canonical = byName[key]?.name ?: run {
                        val created = runCatching {
                            bridge.createCategory(
                                name = catName,
                                iconName = "shopping_bag",
                                colorHex = palette[abs(key.hashCode()) % palette.size],
                                isExpense = !isIncome
                            )
                        }.getOrNull()
                        if (created != null) { byName[key] = created; created.name } else catName
                    }
                    val tx = bridge.createTransaction(
                        walletId = walletId,
                        title = item.title.trim().ifBlank { canonical },
                        category = canonical,
                        amountCents = item.amountCents,
                        isIncome = isIncome,
                        note = note,
                        occurredAt = occurredAt
                    )
                    for (name in tagNames) {
                        val tag = bridge.createTag(name)
                        bridge.addTagToTransaction(tx.id, tag.id)
                    }
                }
                loadAll()
                widgetUpdater.refresh()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateTransaction(
        id: String, title: String, category: String,
        amountCents: Long, isIncome: Boolean, note: String?,
        occurredAt: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.updateTransaction(id, title, category, amountCents, isIncome, note, occurredAt)
                loadAll()
                widgetUpdater.refresh()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteTransaction(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteTransaction(id)
                loadAll()
                widgetUpdater.refresh()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // AI-picks a category for a transaction from its title, preferring one of the supplied
    // categories (case-insensitive) and capitalizing a newly-invented one. Mirrors
    // ReceiptViewModel.suggestCategory; returns null when the model isn't ready or gives nothing.
    suspend fun suggestCategory(title: String, categories: List<String>): String? {
        val name = title.trim()
        if (name.isBlank() || !gemmaRepo.isReady()) return null
        return inferenceMutex.withLock {
            withContext(Dispatchers.IO) {
                val raw = runCatching { gemmaRepo.suggestCategory(name, categories) }.getOrNull()
                    ?.lineSequence()?.firstOrNull()          // model can ramble — take the first line
                    ?.trim()?.trim('"', '\'', '.', ',', ':') // strip stray quotes/punctuation
                    ?.trim()?.takeIf { it.isNotBlank() }
                when {
                    raw == null -> null
                    else -> categories.firstOrNull { it.equals(raw, ignoreCase = true) } ?: capitalizeFirst(raw)
                }
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
