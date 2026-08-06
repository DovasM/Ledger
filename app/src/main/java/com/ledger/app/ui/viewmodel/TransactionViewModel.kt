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
import uniffi.ledger.Transaction
import kotlin.math.abs
import javax.inject.Inject

data class TransactionUiState(
    val transactions: List<Transaction> = emptyList(),
    val monthSummary: MonthSummary? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

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
    data class LineItem(val title: String, val amount: Double, val category: String)

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
                _state.value = _state.value.copy(transactions = txns, monthSummary = summary, isLoading = false)
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
        amount: Double, isIncome: Boolean, note: String?,
        createdAt: String? = null,
        tagNames: List<String> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tx = bridge.createTransaction(walletId, title, category, amount, isIncome, note, createdAt)
                for (name in tagNames) {
                    val tag = bridge.createTag(name)
                    bridge.addTagToTransaction(tx.id, tag.id)
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
        createdAt: String? = null,
        tagNames: List<String> = emptyList(),
        onSuccess: () -> Unit = {}
    ) {
        val valid = items.filter { it.amount > 0 }
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
                        amount = item.amount,
                        isIncome = isIncome,
                        note = note,
                        createdAt = createdAt
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
        amount: Double, isIncome: Boolean, note: String?,
        createdAt: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.updateTransaction(id, title, category, amount, isIncome, note, createdAt)
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
