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
import kotlinx.coroutines.withContext
import uniffi.ledger.Tag
import javax.inject.Inject

data class TagUiState(
    val tags: List<Tag> = emptyList(),
    val transactionTags: List<Tag> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TagViewModel @Inject constructor(
    private val bridge: ILedgerBridge
) : ViewModel() {

    private val _state = MutableStateFlow(TagUiState())
    val state: StateFlow<TagUiState> = _state.asStateFlow()

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tags = bridge.listTags()
                _state.value = _state.value.copy(tags = tags, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadTransactionTags(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tags = bridge.listTransactionTags(transactionId)
                _state.value = _state.value.copy(transactionTags = tags)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    suspend fun loadTagsForTransaction(transactionId: String): List<Tag> =
        withContext(Dispatchers.IO) {
            try { bridge.listTransactionTags(transactionId) } catch (e: Exception) { emptyList() }
        }

    fun createTag(name: String, onSuccess: (Tag) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tag = bridge.createTag(name)
                loadAll()
                launch(Dispatchers.Main) { onSuccess(tag) }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteTag(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteTag(id)
                loadAll()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addTagToTransaction(transactionId: String, tagId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.addTagToTransaction(transactionId, tagId)
                loadTransactionTags(transactionId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun removeTagFromTransaction(transactionId: String, tagId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.removeTagFromTransaction(transactionId, tagId)
                loadTransactionTags(transactionId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
