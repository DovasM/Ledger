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
data class ParsedItem(
    val name: String,
    val price: Double = 0.0,
    val category: String = ""
)

@Serializable
data class ParsedReceipt(
    val store: String = "",
    val date: String = "",
    val total: Double = 0.0,
    val items: List<ParsedItem> = emptyList()
)

@Singleton
class GemmaRepository @Inject constructor(
    private val modelRepo: GemmaModelRepository
) {
    enum class InferenceState { NOT_LOADED, LOADING, READY, ERROR }

    // "Vulkan" jei GPU aktyvus, "CPU" jei ne. Tuščia kol modelis neįkeltas.
    private val _backendInfo = MutableStateFlow("")
    val backendInfo: StateFlow<String> = _backendInfo.asStateFlow()

    private val _inferenceState = MutableStateFlow(InferenceState.NOT_LOADED)
    val inferenceState: StateFlow<InferenceState> = _inferenceState.asStateFlow()

    // Always true — native lib is bundled in libuniffi_ledger.so (no separate .so needed)
    val isNativeLibraryAvailable: Boolean = true

    private var engine: LlamaEngine? = null
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

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
            // 2048 ctx: per-item receipt parsing needs room for the full item list plus a
            // larger structured JSON response than the old single-total prompt.
            engine = llamaCreate(file.absolutePath, nCtx = 2048u)
            val loadMs = System.currentTimeMillis() - t0
            val sysInfo = engine!!.systemInfo()
            Log.d(TAG, "Model loaded in ${loadMs}ms — ${file.name}")
            Log.d(TAG, "Backend: $sysInfo")
            _backendInfo.value = if (sysInfo.contains("GPU_BACKEND=Vulkan")) "Vulkan" else "CPU"
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
        _backendInfo.value = ""
        _inferenceState.value = InferenceState.NOT_LOADED
    }

    fun isReady() = _inferenceState.value == InferenceState.READY

    // ── Receipt parsing ───────────────────────────────────────────────────────

    suspend fun parseReceipt(rawText: String, categories: List<String>): ParsedReceipt = withContext(Dispatchers.IO) {
        Log.d(TAG, "OCR raw: ${rawText.length} chars")

        val today = LocalDate.now().toString()
        val prompt = buildReceiptPrompt(rawText, today, categories)
        val promptTokens = engine?.countTokens(prompt) ?: 0
        Log.d(TAG, "Prompt: ${prompt.length} chars → $promptTokens tokens (nPredict=512, nCtx=2048)")

        val rawResponse = generate(prompt, nPredict = 512u, temperature = 0.1f)
        // Prompt was primed with "{", so prepend it back to form complete JSON.
        val response = "{$rawResponse"

        val prefillMs = engine?.lastPrefillMs() ?: 0L
        val decodeMs  = engine?.lastDecodeMs()  ?: 0L
        val outputTokens = engine?.countTokens(rawResponse) ?: 0

        val prefillTps = if (prefillMs > 0) promptTokens * 1000.0 / prefillMs else 0.0
        val decodeTps  = if (decodeMs  > 0) outputTokens * 1000.0 / decodeMs  else 0.0
        Log.d(TAG, "Prefill: ${prefillMs}ms ($promptTokens tokens, ${String.format("%.1f", prefillTps)} tok/s)")
        Log.d(TAG, "Decode:  ${decodeMs}ms ($outputTokens tokens, ${String.format("%.1f", decodeTps)} tok/s)")
        Log.d(TAG, "Raw response [${rawResponse.length} chars]: ${rawResponse.take(800)}")

        val result = parseReceiptJson(response, today)
        Log.d(TAG, "Parsed: store=\"${result.store}\" total=${result.total} items=${result.items.size} date=${result.date}")
        result
    }

    // ── Category suggestion ───────────────────────────────────────────────────

    suspend fun suggestCategory(transactionTitle: String, categories: List<String>): String =
        withContext(Dispatchers.IO) {
            // Prefer an existing category; only invent a new one when none genuinely fits.
            // (Forcing a pick from the list made bad matches, e.g. "steak" → "Services".)
            val existing = categories.filter { it.isNotBlank() }.distinct()
            val existingLine = if (existing.isEmpty()) "(none yet)" else existing.joinToString(", ")
            val prompt = """
                <start_of_turn>user
                Choose the best expense category for this purchase.
                Purchase: "$transactionTitle"
                Existing categories: $existingLine
                If one of the existing categories reasonably fits, reply with it EXACTLY as written above.
                Only if none fits, reply with a short new category name (1-2 words, English).
                Reply with ONLY the category name, no other text.
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

    private fun buildReceiptPrompt(rawText: String, today: String, categories: List<String>): String {
        // Per-item extraction needs the whole item list, so allow a larger OCR budget than the
        // old single-total prompt. nCtx=2048 leaves room for the per-item JSON response.
        val ocr = if (rawText.length > 1200) {
            val trimmed = rawText.take(900) + "\n...\n" + rawText.takeLast(300)
            Log.d(TAG, "OCR trimmed: ${rawText.length} → ${trimmed.length} chars (head=900 tail=300)")
            trimmed
        } else {
            Log.d(TAG, "OCR no trim needed: ${rawText.length} chars")
            rawText
        }
        // Reuse the user's existing categories so we don't create near-duplicates; the model
        // may invent a short new one only when nothing fits.
        val catList = categories.filter { it.isNotBlank() }.distinct().take(20)
            .joinToString("|").ifBlank { "Food|Household|Transport|Health|Entertainment|Other" }
        // Prime model output with "{" — forces direct JSON without ```json fences.
        return """<start_of_turn>user
Extract EVERY product line from this receipt as JSON. No extra text.
{"store":"NAME","date":"YYYY-MM-DD","total":0.00,"items":[{"name":"PRODUCT","price":0.00,"category":"CAT"}]}
Pick category from: $catList. If none fits, use a short new English category.
date=$today if missing. One item object per product with its own price.
$ocr
<end_of_turn>
<start_of_turn>model
{"""
    }

    // Resilient to a small flaky model: tries a strict parse of the whole object first, then
    // falls back to regex-extracting fields + individual item objects (survives truncated or
    // slightly malformed JSON, which the q4 model produces fairly often on long receipts).
    private fun parseReceiptJson(response: String, today: String): ParsedReceipt {
        val strict = runCatching {
            val start = response.indexOf('{')
            val end = response.lastIndexOf('}')
            if (start < 0 || end <= start) null
            else json.decodeFromString<ParsedReceipt>(response.substring(start, end + 1))
        }.getOrNull()
        if (strict != null && strict.items.isNotEmpty()) {
            return strict.copy(date = strict.date.ifBlank { today })
        }

        val store = Regex(""""store"\s*:\s*"([^"]*)"""").find(response)?.groupValues?.get(1).orEmpty()
        val date  = Regex(""""date"\s*:\s*"([^"]*)"""").find(response)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() } ?: today
        val total = Regex(""""total"\s*:\s*([0-9]+(?:\.[0-9]+)?)""").find(response)
            ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        val items = Regex("""\{[^{}]*?"name"[^{}]*?\}""").findAll(response).mapNotNull { m ->
            val obj = m.value
            val name = Regex(""""name"\s*:\s*"([^"]*)"""").find(obj)?.groupValues?.get(1)
                ?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val price = Regex(""""price"\s*:\s*([0-9]+(?:\.[0-9]+)?)""").find(obj)
                ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val cat = Regex(""""category"\s*:\s*"([^"]*)"""").find(obj)?.groupValues?.get(1)?.trim().orEmpty()
            ParsedItem(name = name, price = price, category = cat)
        }.toList()

        return ParsedReceipt(
            store = store.ifBlank { strict?.store.orEmpty() },
            date  = date,
            total = if (total > 0) total else (strict?.total ?: 0.0),
            items = if (items.isNotEmpty()) items else (strict?.items ?: emptyList())
        )
    }
}
