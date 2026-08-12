package com.ledger.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.ledger.app.data.AutoBackupWorker
import com.ledger.app.data.BackupRepository
import com.ledger.app.data.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val repo: BackupRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    val autoEnabled = prefs.autoBackup
    val autoFolder = prefs.autoBackupFolder
    val autoKeep = prefs.autoBackupKeep
    val autoLastAt = prefs.autoBackupAt
    val autoLastError = prefs.autoBackupError

    /** Turning it on without a folder would schedule work that can only fail, so it is refused. */
    fun setAutoEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && prefs.autoBackupFolder.first().isBlank()) {
                _state.value = _state.value.copy(error = "Choose a folder first")
                return@launch
            }
            prefs.setAutoBackup(enabled)
            AutoBackupWorker.apply(context, enabled)
        }
    }

    /**
     * Keeps lasting access to the folder. Without taking the permission the app would lose it on the
     * next reboot and the daily backup would quietly stop working — the worst way for it to fail.
     */
    fun setAutoFolder(context: Context, tree: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            prefs.setAutoBackupFolder(tree.toString())
            if (prefs.autoBackup.first()) AutoBackupWorker.apply(context, true)
        }
    }

    fun setAutoKeep(value: String) {
        viewModelScope.launch { prefs.setAutoBackupKeep(value) }
    }

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
