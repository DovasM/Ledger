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
import uniffi.ledger.SavingsGoal
import javax.inject.Inject

data class GoalUiState(
    val goals: List<SavingsGoal> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val bridge: LedgerBridge
) : ViewModel() {

    private val _state = MutableStateFlow(GoalUiState())
    val state: StateFlow<GoalUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val goals = bridge.listGoals()
                _state.value = _state.value.copy(goals = goals, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createGoal(name: String, targetAmount: Double, deadline: String?, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.createGoal(name, targetAmount, deadline)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun updateGoal(id: String, name: String, targetAmount: Double, deadline: String?, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.updateGoal(id, name, targetAmount, deadline)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addContribution(goalId: String, amount: Double, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.addContribution(goalId, amount)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun deleteGoal(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteGoal(id)
                load()
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
