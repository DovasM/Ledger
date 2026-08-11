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
import uniffi.ledger.GoalContribution
import uniffi.ledger.SavingsGoal
import javax.inject.Inject

data class GoalUiState(
    val goals: List<SavingsGoal> = emptyList(),
    // Keyed by goal id and loaded on demand, so opening one goal does not read every goal's history.
    val contributions: Map<String, List<GoalContribution>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val bridge: ILedgerBridge
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

    fun addContribution(goalId: String, amount: Double, note: String? = null, occurredAt: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.addContribution(goalId, amount, note, occurredAt)
                load()
                loadContributions(goalId)
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun loadContributions(goalId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = bridge.listGoalContributions(goalId)
                _state.value = _state.value.copy(contributions = _state.value.contributions + (goalId to rows))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /** Removing a mistyped contribution is the whole reason the history exists. */
    fun deleteContribution(id: String, goalId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.deleteContribution(id)
                load()
                loadContributions(goalId)
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
