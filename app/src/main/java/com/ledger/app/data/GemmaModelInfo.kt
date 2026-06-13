package com.ledger.app.data

import kotlinx.serialization.Serializable

@Serializable
data class GemmaModelInfo(
    val version: String,
    val downloadedAt: String,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val sourceUrl: String
) {
    companion object {
        const val CURRENT_VERSION     = "gemma4-e2b-q4km-v1"
        const val MODEL_FILENAME      = "gemma4-e2b-q4km.gguf"
        const val METADATA_FILENAME   = "gemma4-e2b.json"
        const val EXPECTED_SIZE_BYTES = 2_700_000_000L

        // HuggingFace public URL — no token required
        const val DOWNLOAD_URL =
            "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf"
    }
}
