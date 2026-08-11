package com.ledger.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.ledger.BackupInfo
import javax.inject.Inject

data class BackupUiState(
    val busy: Boolean = false,
    /** Set after a successful export, so the screen can confirm what was written. */
    val lastExport: BackupInfo? = null,
    /** What the user picked, held until they confirm or cancel the restore. */
    val pending: BackupInfo? = null,
    val pendingUri: Uri? = null,
    val restored: BackupInfo? = null,
    val error: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repo: BackupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName() = repo.suggestedFileName()

    fun export(destination: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, lastExport = null)
            runCatching { repo.exportTo(destination) }
                .onSuccess { _state.value = _state.value.copy(busy = false, lastExport = it) }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message ?: "Backup failed") }
        }
    }

    /** Reads the file the user picked so they can see what it holds before agreeing to restore it. */
    fun stageRestore(source: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, restored = null)
            runCatching { repo.inspect(source) }
                .onSuccess { _state.value = _state.value.copy(busy = false, pending = it, pendingUri = source) }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message ?: "That file could not be read") }
        }
    }

    fun confirmRestore() {
        val uri = _state.value.pendingUri ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            runCatching { repo.restoreFrom(uri) }
                .onSuccess { _state.value = BackupUiState(restored = it) }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message ?: "Restore failed") }
        }
    }

    fun cancelRestore() { _state.value = _state.value.copy(pending = null, pendingUri = null) }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
