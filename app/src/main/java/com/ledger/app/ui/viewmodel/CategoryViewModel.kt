package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.ILedgerBridge
import com.ledger.app.ui.util.normalizeCategoryName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.ledger.Category
import javax.inject.Inject

data class CategoryUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val bridge: ILedgerBridge
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryUiState())
    val state: StateFlow<CategoryUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val categories = bridge.listCategories()
                _state.value = _state.value.copy(categories = categories, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createCategory(name: String, iconName: String, colorHex: String, isExpense: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val clean = normalizeCategoryName(name)
                if (clean.isBlank()) {
                    _state.value = _state.value.copy(error = "Category name is required")
                    return@launch
                }
                if (bridge.listCategories().any { it.name.trim().equals(clean, ignoreCase = true) }) {
                    _state.value = _state.value.copy(error = "A category named \"$clean\" already exists")
                    return@launch
                }
                bridge.createCategory(clean, iconName, colorHex, isExpense)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateCategory(id: String, name: String, iconName: String, colorHex: String, isExpense: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val clean = normalizeCategoryName(name)
                if (clean.isBlank()) {
                    _state.value = _state.value.copy(error = "Category name is required")
                    return@launch
                }
                // A different category already using this name (case-insensitive) is a duplicate.
                if (bridge.listCategories().any { it.id != id && it.name.trim().equals(clean, ignoreCase = true) }) {
                    _state.value = _state.value.copy(error = "A category named \"$clean\" already exists")
                    return@launch
                }
                bridge.updateCategory(id, clean, iconName, colorHex, isExpense)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteCategory(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteCategory(id)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
