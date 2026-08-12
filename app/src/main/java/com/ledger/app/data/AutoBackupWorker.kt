package com.ledger.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
    fun backupRepository(): BackupRepository
    fun preferences(): PreferencesRepository
}

/**
 * The daily backup.
 *
 * A backup you have to remember to take is one you will not have, which is the whole reason this
 * exists. It writes into the folder the user granted lasting access to, then prunes its own older
 * files — and only ever its own, see [BackupRetention].
 *
 * Hilt is reached through an entry point rather than `@HiltWorker`, matching how the widgets already
 * get their dependencies; that avoids adding hilt-work and a custom Application configuration for
 * one worker.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java)
        val prefs = entry.preferences()

        if (!prefs.autoBackup.first()) return Result.success()
        val folder = prefs.autoBackupFolder.first()
        if (folder.isBlank()) {
            prefs.setAutoBackupResult("", "No folder chosen for automatic backups")
            return Result.success()
        }

        return try {
            val tree = Uri.parse(folder)
            val name = BackupRetention.automaticName(LocalDateTime.now())
            val destination = createFile(tree, name)
                ?: error("could not create a file in the chosen folder")

            entry.backupRepository().exportTo(destination)
            prune(tree, prefs.autoBackupKeep.first().toIntOrNull() ?: 7)

            prefs.setAutoBackupResult(LocalDateTime.now().toString(), "")
            Result.success()
        } catch (e: Exception) {
            prefs.setAutoBackupResult(LocalDateTime.now().toString(), e.message ?: "Backup failed")
            // Retried rather than dropped: the folder may simply be unavailable right now — an SD
            // card out, a cloud provider not signed in.
            Result.retry()
        }
    }

    private fun createFile(tree: Uri, name: String): Uri? {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(
            applicationContext.contentResolver, parent, "application/octet-stream", name
        )
    }

    /** Deletes our own older backups. Anything else in the folder is the user's and is left alone. */
    private fun prune(tree: Uri, keep: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree)
        )
        val byName = mutableMapOf<String, String>()
        applicationContext.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                byName[cursor.getString(1)] = cursor.getString(0)
            }
        }

        for (name in BackupRetention.backupsToDelete(byName.keys.toList(), keep)) {
            val id = byName[name] ?: continue
            runCatching {
                DocumentsContract.deleteDocument(
                    applicationContext.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, id)
                )
            }
        }
    }

    companion object {
        private const val WORK_NAME = "ledger-auto-backup"

        /**
         * Schedules or cancels the daily run. `UPDATE` rather than `KEEP` so changing the settings
         * takes effect instead of leaving yesterday's schedule in place.
         */
        fun apply(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        // Writing a few megabytes is cheap, but not worth doing on a dying battery.
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
