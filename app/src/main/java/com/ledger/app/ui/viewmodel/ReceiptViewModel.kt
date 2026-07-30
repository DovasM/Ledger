package com.ledger.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.GemmaRepository
import com.ledger.app.data.ILedgerBridge
import com.ledger.app.data.ParsedReceipt
import com.ledger.app.data.ReceiptOcrRepository
import com.ledger.app.ui.util.capitalizeFirst
import com.ledger.app.ui.util.normalizeCategoryName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import javax.inject.Inject

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val ocrRepo: ReceiptOcrRepository,
    private val gemmaRepo: GemmaRepository,
    private val bridge: ILedgerBridge,
) : ViewModel() {

    sealed class State {
        object Idle : State()
        object OcrRunning : State()
        object AiRunning : State()
        // aiFailed = the model returned nothing usable (no items), so the preview opens empty
        // for manual entry instead of silently looking like a successful scan.
        data class Preview(val receipt: ParsedReceipt, val aiFailed: Boolean = false) : State()
        object Saving : State()
        data class Error(val msg: String) : State()
    }

    // One product the user is about to turn into a transaction.
    data class NewItem(val name: String, val amount: Double, val category: String)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // One native inference engine → serialize all category suggestions so concurrent
    // generate() calls (auto-fill loop + wand buttons) can't corrupt the context.
    private val inferenceMutex = Mutex()

    // Palette for auto-created categories (mirrors ui/util CategoryIcons hexes).
    private val palette = listOf(
        "#00513F", "#920009", "#1565C0", "#E65100", "#6A1B9A",
        "#00838F", "#558B2F", "#F9A825", "#4E342E"
    )

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _state.value = State.OcrRunning
                val text = ocrRepo.extractText(bitmap)
                if (text.isBlank()) {
                    _state.value = State.Error("No text found on the receipt. Try again with a clearer, well-lit photo.")
                    return@launch
                }
                if (!gemmaRepo.isReady()) {
                    _state.value = State.Error("AI model isn't loaded. Open AI settings and load the model first.")
                    return@launch
                }
                _state.value = State.AiRunning
                val categories = withContext(Dispatchers.IO) {
                    runCatching { bridge.listCategories().filter { it.isExpense }.map { it.name } }
                        .getOrDefault(emptyList())
                }
                val receipt = gemmaRepo.parseReceipt(text, categories)
                // No items means the model produced garbage or truncated JSON that even the
                // regex fallback couldn't salvage — there is nothing to turn into transactions.
                _state.value = State.Preview(receipt, aiFailed = receipt.items.isEmpty())
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Creates one transaction per product. For each item the category is matched
    // case-insensitively against existing categories; if none exists it is created.
    fun confirmAndCreate(
        walletId: String,
        store: String,
        dateIso: String,
        items: List<NewItem>,
        onDone: () -> Unit
    ) {
        val valid = items.filter { it.amount > 0 }
        if (valid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = State.Saving
                val byName = runCatching { bridge.listCategories() }.getOrDefault(emptyList())
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()
                val note = store.trim().ifBlank { null }
                for (item in valid) {
                    val catName = normalizeCategoryName(item.category).ifBlank { "Other" }
                    val key = catName.lowercase()
                    val canonical = byName[key]?.name ?: run {
                        val created = runCatching {
                            bridge.createCategory(
                                name = catName,
                                iconName = "shopping_bag",
                                colorHex = palette[abs(key.hashCode()) % palette.size],
                                isExpense = true
                            )
                        }.getOrNull()
                        if (created != null) { byName[key] = created; created.name } else catName
                    }
                    bridge.createTransaction(
                        walletId = walletId,
                        title = item.name.trim().ifBlank { canonical },
                        category = canonical,
                        amount = item.amount,
                        isIncome = false,
                        note = note,
                        createdAt = dateIso
                    )
                }
                withContext(Dispatchers.Main) {
                    _state.value = State.Idle
                    onDone()
                }
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Failed to create transactions")
            }
        }
    }

    // AI-picks a category for one product name. Returns an existing category (matched
    // case-insensitively) when the model's answer matches one, else the model's raw answer,
    // or null when the model isn't ready / gives nothing. Serialized via inferenceMutex.
    suspend fun suggestCategory(productName: String): String? {
        val name = productName.trim()
        if (name.isBlank() || !gemmaRepo.isReady()) return null
        return inferenceMutex.withLock {
            withContext(Dispatchers.IO) {
                val categories = runCatching { bridge.listCategories().filter { it.isExpense }.map { it.name } }
                    .getOrDefault(emptyList())
                val raw = runCatching { gemmaRepo.suggestCategory(name, categories) }.getOrNull()
                    ?.lineSequence()?.firstOrNull()          // model can ramble — take the first line
                    ?.trim()?.trim('"', '\'', '.', ',', ':') // strip stray quotes/punctuation
                    ?.trim()?.takeIf { it.isNotBlank() }
                when {
                    raw == null -> null
                    // Reuse the exact existing category when the model echoed one; else capitalize the new one.
                    else -> categories.firstOrNull { it.equals(raw, ignoreCase = true) } ?: capitalizeFirst(raw)
                }
            }
        }
    }

    fun reset() { _state.value = State.Idle }
}
