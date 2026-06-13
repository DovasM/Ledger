package com.ledger.app.data

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long) : ModelStatus()
    object Verifying : ModelStatus()
    object Ready : ModelStatus()
    data class UpdateAvailable(val currentVersion: String) : ModelStatus()
    data class Error(val message: String, val retryable: Boolean) : ModelStatus()
    object Deleting : ModelStatus()
}

data class StorageInfo(
    val modelSizeBytes: Long,
    val availableBytes: Long,
    val hasEnoughSpace: Boolean
)

@Singleton
class GemmaModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir = File(context.filesDir, "models")
    private val modelFile = File(modelsDir, GemmaModelInfo.MODEL_FILENAME)
    private val metaFile  = File(modelsDir, GemmaModelInfo.METADATA_FILENAME)
    private val tempFile  = File(modelsDir, "${GemmaModelInfo.MODEL_FILENAME}.tmp")

    private val json = Json { ignoreUnknownKeys = true }

    fun getModelStatus(): ModelStatus {
        if (!modelFile.exists()) return ModelStatus.NotDownloaded
        if (!metaFile.exists()) return ModelStatus.NotDownloaded

        val info = runCatching {
            json.decodeFromString<GemmaModelInfo>(metaFile.readText())
        }.getOrNull() ?: return ModelStatus.NotDownloaded

        if (info.version != GemmaModelInfo.CURRENT_VERSION) {
            return ModelStatus.UpdateAvailable(info.version)
        }

        val tolerance = (GemmaModelInfo.EXPECTED_SIZE_BYTES * 0.01).toLong()
        if (kotlin.math.abs(modelFile.length() - info.fileSizeBytes) > tolerance) {
            return ModelStatus.NotDownloaded
        }

        return ModelStatus.Ready
    }

    fun downloadModel(): Flow<ModelStatus> = flow {
        modelsDir.mkdirs()
        tempFile.delete()

        emit(ModelStatus.Downloading(0, 0))

        try {
            val connection = (URL(GemmaModelInfo.DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout    = 60_000
                instanceFollowRedirects = true
                connect()
            }

            if (connection.responseCode !in 200..299) {
                emit(ModelStatus.Error("Serverio klaida (${connection.responseCode}). Bandykite vėliau.", retryable = true))
                return@flow
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
                ?: GemmaModelInfo.EXPECTED_SIZE_BYTES

            val storage = getStorageInfo()
            if (!storage.hasEnoughSpace) {
                emit(ModelStatus.Error("Nepakanka vietos. Reikia ~1.3GB.", retryable = false))
                return@flow
            }

            var bytesDownloaded = 0L
            val buffer = ByteArray(8 * 1024)

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (!currentCoroutineContext().isActive) {
                            tempFile.delete()
                            return@flow
                        }
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        val pct = ((bytesDownloaded.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99)
                        emit(ModelStatus.Downloading(pct, bytesDownloaded))
                    }
                }
            }

            emit(ModelStatus.Verifying)

            if (tempFile.length() < 100_000_000L) {
                tempFile.delete()
                emit(ModelStatus.Error("Atsisiųstas failas per mažas (${tempFile.length() / 1_000_000} MB) — serverio klaida.", retryable = true))
                return@flow
            }

            val checksum = computeSha256(tempFile)
            tempFile.renameTo(modelFile)

            val meta = GemmaModelInfo(
                version          = GemmaModelInfo.CURRENT_VERSION,
                downloadedAt     = Instant.now().toString(),
                fileSizeBytes    = modelFile.length(),
                sha256Checksum   = checksum,
                sourceUrl        = GemmaModelInfo.DOWNLOAD_URL
            )
            metaFile.writeText(json.encodeToString(meta))

            emit(ModelStatus.Ready)

        } catch (e: java.io.IOException) {
            tempFile.delete()
            val msg = if (e.message?.contains("ENOSPC") == true)
                "Nepakanka vietos. Atlaisvinkite bent 1.5 GB."
            else
                "Tinklo klaida. Patikrinkite interneto ryšį."
            emit(ModelStatus.Error(msg, retryable = true))
        } catch (e: Exception) {
            tempFile.delete()
            emit(ModelStatus.Error("Klaida: ${e.javaClass.simpleName} — ${e.message ?: "nežinoma klaida"}", retryable = true))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun verifyIntegrity(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists() || !metaFile.exists()) return@withContext false
        val info = runCatching {
            json.decodeFromString<GemmaModelInfo>(metaFile.readText())
        }.getOrNull() ?: return@withContext false
        computeSha256(modelFile) == info.sha256Checksum
    }

    suspend fun checkForUpdate(): Boolean {
        if (!metaFile.exists()) return false
        val info = runCatching {
            json.decodeFromString<GemmaModelInfo>(metaFile.readText())
        }.getOrNull() ?: return false
        return info.version != GemmaModelInfo.CURRENT_VERSION
    }

    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        modelFile.delete()
        metaFile.delete()
        tempFile.delete()
        modelsDir.listFiles { f -> f.extension == "tmp" }?.forEach { it.delete() }
    }

    fun getStorageInfo(): StorageInfo {
        val modelSize = if (modelFile.exists()) modelFile.length() else 0L
        val stat = StatFs(context.filesDir.absolutePath)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        return StorageInfo(
            modelSizeBytes  = modelSize,
            availableBytes  = available,
            hasEnoughSpace  = available > (GemmaModelInfo.EXPECTED_SIZE_BYTES * 1.1).toLong()
        )
    }

    fun getModelFile(): File? = if (modelFile.exists()) modelFile else null

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        file.inputStream().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
