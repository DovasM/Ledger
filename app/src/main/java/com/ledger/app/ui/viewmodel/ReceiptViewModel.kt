package com.ledger.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.GemmaRepository
import com.ledger.app.data.ParsedExpense
import com.ledger.app.data.ReceiptOcrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val ocrRepo: ReceiptOcrRepository,
    private val gemmaRepo: GemmaRepository,
) : ViewModel() {

    sealed class State {
        object Idle : State()
        object OcrRunning : State()
        object AiRunning : State()
        data class Preview(val expense: ParsedExpense) : State()
        data class Error(val msg: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _state.value = State.OcrRunning
                val text = ocrRepo.extractText(bitmap)
                if (text.isBlank()) {
                    _state.value = State.Error("Čekyje teksto nerasta. Bandyk dar kartą su aiškesne nuotrauka.")
                    return@launch
                }
                _state.value = State.AiRunning
                if (!gemmaRepo.isReady()) {
                    _state.value = State.Error("AI modelis neįkeltas. Eik į AI nustatymus ir įkelk modelį.")
                    return@launch
                }
                val expense = gemmaRepo.parseReceiptText(text)
                _state.value = State.Preview(expense)
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Nežinoma klaida")
            }
        }
    }

    fun reset() { _state.value = State.Idle }
}
