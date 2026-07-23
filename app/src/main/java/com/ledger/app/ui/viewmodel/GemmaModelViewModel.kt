package com.ledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.GemmaModelRepository
import com.ledger.app.data.GemmaRepository
import com.ledger.app.data.ModelStatus
import com.ledger.app.data.PreferencesRepository
import com.ledger.app.data.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class GemmaUiState(
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val storageInfo: StorageInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val integrityOk: Boolean? = null,
    val isVerifying: Boolean = false,
    val lastChecked: String? = null,
    val inferenceState: GemmaRepository.InferenceState = GemmaRepository.InferenceState.NOT_LOADED,
    val isNativeLibraryAvailable: Boolean = false,
    val testResponse: String? = null,
    val isTestRunning: Boolean = false,
    val backendInfo: String = "",  // "Vulkan" arba "CPU" po modelio įkėlimo
    val autoLoad: Boolean = false,        // vartotojas įjungė automatinį įkėlimą paleidus programą
    val autoLoadPrompted: Boolean = false // ar jau klausėme vieną kartą
)

@HiltViewModel
class GemmaModelViewModel @Inject constructor(
    private val modelRepo: GemmaModelRepository,
    private val gemmaRepo: GemmaRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GemmaUiState())
    val state: StateFlow<GemmaUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm, d MMM")
        .withZone(ZoneId.systemDefault())

    init {
        _state.update { it.copy(isNativeLibraryAvailable = gemmaRepo.isNativeLibraryAvailable) }
        refreshStatus()
        viewModelScope.launch {
            gemmaRepo.inferenceState.collect { infer ->
                _state.update { it.copy(inferenceState = infer) }
            }
        }
        viewModelScope.launch {
            gemmaRepo.backendInfo.collect { info ->
                _state.update { it.copy(backendInfo = info) }
            }
        }
        viewModelScope.launch {
            prefs.aiAutoLoad.collect { v -> _state.update { it.copy(autoLoad = v) } }
        }
        viewModelScope.launch {
            prefs.aiAutoLoadPrompted.collect { v -> _state.update { it.copy(autoLoadPrompted = v) } }
        }
    }

    fun refreshStatus() {
        _state.update { it.copy(
            modelStatus = modelRepo.getModelStatus(),
            storageInfo = modelRepo.getStorageInfo(),
            lastChecked = timeFormatter.format(Instant.now())
        ) }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            modelRepo.downloadModel().collect { status ->
                _state.update { it.copy(modelStatus = status) }
                when (status) {
                    is ModelStatus.Ready -> {
                        _state.update { it.copy(storageInfo = modelRepo.getStorageInfo()) }
                        if (gemmaRepo.isNativeLibraryAvailable &&
                            gemmaRepo.inferenceState.value == GemmaRepository.InferenceState.NOT_LOADED) {
                            loadModelInternal()
                        }
                    }
                    is ModelStatus.Error -> _state.update { it.copy(storageInfo = modelRepo.getStorageInfo()) }
                    else -> {}
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(modelStatus = ModelStatus.NotDownloaded) }
    }

    fun checkUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCheckingUpdate = true) }
            val hasUpdate = modelRepo.checkForUpdate()
            _state.update { it.copy(
                isCheckingUpdate = false,
                modelStatus = if (hasUpdate) modelRepo.getModelStatus() else it.modelStatus,
                lastChecked = timeFormatter.format(Instant.now())
            ) }
        }
    }

    fun verifyIntegrity() {
        if (_state.value.isVerifying) return
        viewModelScope.launch {
            _state.update { it.copy(isVerifying = true, integrityOk = null) }
            val ok = modelRepo.verifyIntegrity()
            _state.update { it.copy(isVerifying = false, integrityOk = ok) }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            gemmaRepo.unloadModel()
            _state.update { it.copy(modelStatus = ModelStatus.Deleting) }
            modelRepo.deleteModel()
            _state.update { it.copy(
                modelStatus = ModelStatus.NotDownloaded,
                storageInfo = modelRepo.getStorageInfo(),
                integrityOk = null,
                testResponse = null
            ) }
        }
    }

    fun loadModelForInference() {
        viewModelScope.launch { loadModelInternal() }
    }

    private suspend fun loadModelInternal() {
        if (_state.value.modelStatus !is ModelStatus.Ready) return
        gemmaRepo.loadModel()
    }

    fun unloadModelFromMemory() {
        gemmaRepo.unloadModel()
    }

    // ── Auto-load (load model into memory on app startup) ─────────────────────

    private fun canLoadNow() =
        _state.value.modelStatus is ModelStatus.Ready &&
        gemmaRepo.isNativeLibraryAvailable &&
        gemmaRepo.inferenceState.value == GemmaRepository.InferenceState.NOT_LOADED

    /** Toggle in AI settings. Enabling also loads the model right away if it isn't already. */
    fun setAutoLoad(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAiAutoLoad(enabled)
            if (enabled && canLoadNow()) loadModelInternal()
        }
    }

    /** One-time prompt: user chose "Enable" — remember the choice and load now. */
    fun enableAutoLoadFromPrompt() {
        viewModelScope.launch {
            prefs.setAiAutoLoadPrompted(true)
            prefs.setAiAutoLoad(true)
            if (canLoadNow()) loadModelInternal()
        }
    }

    /** One-time prompt: user dismissed — don't ask again, leave auto-load off. */
    fun dismissAutoLoadPrompt() {
        viewModelScope.launch { prefs.setAiAutoLoadPrompted(true) }
    }

    fun runTestQuery(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isTestRunning = true, testResponse = null) }
            val context = "Birželis: Maistas 180€, Transportas 45€, Pramogos 60€, Komunalinės 90€, Kita 35€"
            val response = gemmaRepo.answerSpendingQuery(question, context)
            _state.update { it.copy(
                isTestRunning = false,
                testResponse = response.ifBlank { "Atsakymas tuščias — patikrinkite modelio būseną." }
            ) }
        }
    }
}
