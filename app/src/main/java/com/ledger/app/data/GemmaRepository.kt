package com.ledger.app.data

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
            engine = llamaCreate(file.absolutePath, nCtx = 2048u)
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
        val today = LocalDate.now().toString()
        val response = generate(buildReceiptPrompt(rawText, today), nPredict = 200u, temperature = 0.1f)
        parseJsonResponse(response)
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

    private fun buildReceiptPrompt(rawText: String, today: String) = """
        <start_of_turn>user
        Extract data from this receipt. Reply with ONLY valid JSON, no other text.

        Categories (pick one): Maistas, Transportas, Sveikata,
        Pramogos, Drabužiai, Komunalinės, Restoranas, Kita

        JSON format:
        {"store":"...","date":"YYYY-MM-DD","amount":0.00,"category":"...","items":["..."]}

        Rules:
        - date: use receipt date if found, else $today
        - amount: total sum, else 0.00
        - items: line items, max 5
        - store: merchant name only

        Receipt text:
        $rawText
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()

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
