package com.ledger.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uniffi.ledger.LlamaEngine
import uniffi.ledger.LlamaException
import uniffi.ledger.llamaCreate
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GemmaRepo"

@Serializable
data class ParsedExpense(
    val store: String,
    val date: String,
    val amount: Double,
    val category: String,
    val items: List<String>
)

@Singleton
class GemmaRepository @Inject constructor(
    private val modelRepo: GemmaModelRepository
) {
    enum class InferenceState { NOT_LOADED, LOADING, READY, ERROR }

    private val _inferenceState = MutableStateFlow(InferenceState.NOT_LOADED)
    val inferenceState: StateFlow<InferenceState> = _inferenceState.asStateFlow()

    // Always true — native lib is bundled in libuniffi_ledger.so (no separate .so needed)
    val isNativeLibraryAvailable: Boolean = true

    private var engine: LlamaEngine? = null
    private val json = Json { ignoreUnknownKeys = true }

    // ── Model lifecycle ───────────────────────────────────────────────────────

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        _inferenceState.value = InferenceState.LOADING
        val file = modelRepo.getModelFile()
        if (file == null) {
            _inferenceState.value = InferenceState.ERROR
            return@withContext false
        }
        return@withContext try {
            val t0 = System.currentTimeMillis()
            engine = llamaCreate(file.absolutePath, nCtx = 1024u)
            val loadMs = System.currentTimeMillis() - t0
            Log.d(TAG, "Model loaded in ${loadMs}ms — ${file.name}")
            Log.d(TAG, "Backend: ${engine!!.systemInfo()}")
            _inferenceState.value = InferenceState.READY
            true
        } catch (e: LlamaException) {
            _inferenceState.value = InferenceState.ERROR
            false
        }
    }

    fun unloadModel() {
        engine?.unload()
        engine = null
        _inferenceState.value = InferenceState.NOT_LOADED
    }

    fun isReady() = _inferenceState.value == InferenceState.READY

    // ── Receipt parsing ───────────────────────────────────────────────────────

    suspend fun parseReceiptText(rawText: String): ParsedExpense = withContext(Dispatchers.IO) {
        Log.d(TAG, "OCR raw: ${rawText.length} chars")

        val today = LocalDate.now().toString()
        val prompt = buildReceiptPrompt(rawText, today)
        val promptTokens = engine?.countTokens(prompt) ?: 0
        Log.d(TAG, "Prompt: ${prompt.length} chars → $promptTokens tokens (nPredict=120, nCtx=1024)")

        val rawResponse = generate(prompt, nPredict = 120u, temperature = 0.1f)
        // Prompt was primed with "{", so prepend it back to form complete JSON.
        val response = "{$rawResponse"

        val prefillMs = engine?.lastPrefillMs() ?: 0L
        val decodeMs  = engine?.lastDecodeMs()  ?: 0L
        val outputTokens = engine?.countTokens(rawResponse) ?: 0

        val prefillTps = if (prefillMs > 0) promptTokens * 1000.0 / prefillMs else 0.0
        val decodeTps  = if (decodeMs  > 0) outputTokens * 1000.0 / decodeMs  else 0.0
        Log.d(TAG, "Prefill: ${prefillMs}ms ($promptTokens tokens, ${String.format("%.1f", prefillTps)} tok/s)")
        Log.d(TAG, "Decode:  ${decodeMs}ms ($outputTokens tokens, ${String.format("%.1f", decodeTps)} tok/s)")
        Log.d(TAG, "Raw response [${rawResponse.length} chars]: ${rawResponse.take(500)}")

        val result = parseJsonResponse(response)
        Log.d(TAG, "Parsed: store=\"${result.store}\" amount=${result.amount} category=\"${result.category}\" items=${result.items.size} date=${result.date}")
        result
    }

    // ── Category suggestion ───────────────────────────────────────────────────

    suspend fun suggestCategory(transactionTitle: String, categories: List<String>): String =
        withContext(Dispatchers.IO) {
            val prompt = """
                <start_of_turn>user
                Classify this transaction into exactly one category.
                Transaction: "$transactionTitle"
                Categories: ${categories.joinToString(", ")}
                Reply with ONLY the category name, nothing else.
                <end_of_turn>
                <start_of_turn>model
            """.trimIndent()
            generate(prompt, nPredict = 20u, temperature = 0.1f).trim()
        }

    // ── Natural language spending query ──────────────────────────────────────

    suspend fun answerSpendingQuery(question: String, contextSummary: String): String =
        withContext(Dispatchers.IO) {
            val prompt = """
                <start_of_turn>user
                You are a personal finance assistant. Answer based only on the data provided.
                Data: $contextSummary
                Question: $question
                Be concise, 1-2 sentences.
                <end_of_turn>
                <start_of_turn>model
            """.trimIndent()
            generate(prompt, nPredict = 150u, temperature = 0.3f).trim()
        }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun generate(prompt: String, nPredict: UInt, temperature: Float): String {
        return try {
            engine?.generate(prompt, nPredict, temperature) ?: ""
        } catch (e: LlamaException) {
            ""
        }
    }

    private fun buildReceiptPrompt(rawText: String, today: String): String {
        // Gemma 4: ~2.2 chars/token. Target <400 total tokens.
        // Template uses ~60 tokens; OCR budget ~300 tokens ≈ 660 chars.
        // Take head (store/items) + tail (totals).
        val ocr = if (rawText.length > 660) {
            val trimmed = rawText.take(440) + "\n...\n" + rawText.takeLast(220)
            Log.d(TAG, "OCR trimmed: ${rawText.length} → ${trimmed.length} chars (head=440 tail=220)")
            trimmed
        } else {
            Log.d(TAG, "OCR no trim needed: ${rawText.length} chars")
            rawText
        }
        // Prime model output with "{" — forces direct JSON without ```json fences.
        // Response from model will be the rest of the JSON (from "store":... onward).
        return """<start_of_turn>user
Receipt→JSON only, no extra text.
{"store":"...","date":"YYYY-MM-DD","amount":0.00,"category":"Maistas|Transportas|Sveikata|Pramogos|Drabužiai|Komunalinės|Restoranas|Kita","items":["..."]}
date=$today if not on receipt. amount=total. items max 5.
$ocr
<end_of_turn>
<start_of_turn>model
{"""
    }

    private fun parseJsonResponse(response: String): ParsedExpense {
        return try {
            val jsonStr = Regex("""\{[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
                .find(response)?.value
                ?: throw IllegalArgumentException("No JSON in response")
            json.decodeFromString<ParsedExpense>(jsonStr)
        } catch (e: Exception) {
            ParsedExpense(
                store    = "Nežinoma parduotuvė",
                date     = LocalDate.now().toString(),
                amount   = 0.00,
                category = "Kita",
                items    = emptyList()
            )
        }
    }
}
