package com.ledger.app.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.ledger.BackupInfo
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup and restore, across the gap between SQLite and Android storage.
 *
 * The Rust side works in file paths; the Storage Access Framework works in `content://` URIs and is
 * the only way a file can outlive the app being uninstalled. So every operation goes through a
 * staging file in the cache directory: write the snapshot there and copy it out, or copy the user's
 * pick in and read it there.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: ILedgerBridge
) {

    /** A name that sorts by date and says what it is, so a folder of them stays readable. */
    fun suggestedFileName(): String = "ledger-backup-${LocalDate.now()}.ledgerdb"

    private fun staging(name: String): File =
        File(context.cacheDir, "backup").apply { mkdirs() }.resolve(name)

    /** Snapshots the database and copies it to wherever the user chose. */
    suspend fun exportTo(destination: Uri): BackupInfo = withContext(Dispatchers.IO) {
        val staged = staging("export.ledgerdb")
        try {
            val info = bridge.backupDatabase(staged.absolutePath)
            context.contentResolver.openOutputStream(destination, "wt")
                ?.use { out -> staged.inputStream().use { it.copyTo(out) } }
                ?: error("could not open the destination for writing")
            info
        } finally {
            staged.delete()
        }
    }

    /** What the chosen file holds, without changing anything. */
    suspend fun inspect(source: Uri): BackupInfo = withContext(Dispatchers.IO) {
        val staged = copyIn(source, "inspect.ledgerdb")
        try {
            bridge.inspectBackup(staged.absolutePath)
        } finally {
            staged.delete()
        }
    }

    /** Replaces everything in the database with the chosen file. */
    suspend fun restoreFrom(source: Uri): BackupInfo = withContext(Dispatchers.IO) {
        val staged = copyIn(source, "restore.ledgerdb")
        try {
            bridge.restoreBackup(staged.absolutePath)
        } finally {
            staged.delete()
            // restore_backup works on its own copy alongside the file it was given.
            File("${staged.absolutePath}.restore-staging").delete()
        }
    }

    private fun copyIn(source: Uri, name: String): File {
        val staged = staging(name)
        context.contentResolver.openInputStream(source)
            ?.use { input -> staged.outputStream().use { input.copyTo(it) } }
            ?: error("could not read the selected file")
        return staged
    }
}
