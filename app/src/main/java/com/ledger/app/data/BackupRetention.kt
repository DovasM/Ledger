package com.ledger.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Naming and pruning for backup files.
 *
 * Kept apart from anything Android because the dangerous part is a decision, not an API call: the
 * app is given write access to a folder the user chose, which may well be Documents or Downloads
 * with years of their own files in it. Deciding what to delete there has to be provably narrow, and
 * that is only worth trusting if it can be tested on its own.
 */
object BackupRetention {

    private const val PREFIX = "ledger-backup-"
    const val EXTENSION = ".ledgerdb"

    /**
     * Only files this app wrote. Anything else in the folder is somebody's own file and is never a
     * candidate for deletion, however old it looks.
     *
     * Matches the manual name (`ledger-backup-2026-03-10.ledgerdb`) and the automatic one, which
     * carries a time so several backups in one day do not collide.
     */
    private val OURS = Regex("""^ledger-backup-\d{4}-\d{2}-\d{2}(-\d{6})?\.ledgerdb$""")

    fun isOurBackup(name: String): Boolean = OURS.matches(name)

    /** The name a manual backup gets: one a day, dated, sorts correctly as text. */
    fun manualName(today: LocalDate = LocalDate.now()): String =
        "$PREFIX$today$EXTENSION"

    /** The name an automatic backup gets: dated and timed, so a second run the same day is its own file. */
    fun automaticName(now: LocalDateTime = LocalDateTime.now()): String =
        PREFIX + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + EXTENSION

    /**
     * Which files to remove so that only the [keep] most recent of ours remain.
     *
     * Names are dated most-significant-first, so sorting them as text sorts them by time. Anything
     * that is not ours is filtered out *before* the count is applied — otherwise a folder holding
     * one backup and thirty of the user's own documents would look like thirty files to prune.
     */
    fun backupsToDelete(names: List<String>, keep: Int): List<String> {
        if (keep < 1) return emptyList()
        val ours = names.filter(::isOurBackup).sortedDescending()
        return ours.drop(keep)
    }
}
