package com.ledger.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Automatic backups are given write access to a folder the user picked — very possibly Documents,
 * with years of their own files in it. Everything here is about one question: what is this app
 * allowed to delete?
 */
class BackupRetentionTest {

    @Test
    fun `the newest are kept and the rest are dropped`() {
        val names = listOf(
            "ledger-backup-2026-03-01.ledgerdb",
            "ledger-backup-2026-03-05.ledgerdb",
            "ledger-backup-2026-03-10.ledgerdb",
            "ledger-backup-2026-02-20.ledgerdb"
        )
        assertEquals(
            listOf("ledger-backup-2026-03-01.ledgerdb", "ledger-backup-2026-02-20.ledgerdb"),
            BackupRetention.backupsToDelete(names, keep = 2)
        )
    }

    /** The decisive one: a folder full of the user's own files must lose nothing. */
    @Test
    fun `files this app did not write are never touched`() {
        val names = listOf(
            "tax-return-2019.pdf",
            "CV.docx",
            "holiday.jpg",
            "backup.db",
            "ledger-backup.ledgerdb",
            "my-ledger-backup-2026-03-01.ledgerdb",
            "ledger-backup-2026-03-01.ledgerdb.bak",
            "ledger-backup-2026-03-01.ledgerdb"
        )
        // Exactly one of those eight is ours, so with keep = 1 nothing at all is deleted.
        assertEquals(emptyList<String>(), BackupRetention.backupsToDelete(names, keep = 1))

        // And even when there is enough of ours to prune, only ours is ever considered.
        val withMore = names + listOf(
            "ledger-backup-2026-03-02.ledgerdb",
            "ledger-backup-2026-03-03.ledgerdb"
        )
        val doomed = BackupRetention.backupsToDelete(withMore, keep = 1)
        assertEquals(listOf("ledger-backup-2026-03-02.ledgerdb", "ledger-backup-2026-03-01.ledgerdb"), doomed)
        assertTrue(doomed.all(BackupRetention::isOurBackup))
    }

    @Test
    fun `only our exact naming counts as ours`() {
        assertTrue(BackupRetention.isOurBackup("ledger-backup-2026-03-10.ledgerdb"))
        assertTrue(BackupRetention.isOurBackup("ledger-backup-2026-03-10-143005.ledgerdb"))

        assertFalse(BackupRetention.isOurBackup("ledger-backup.ledgerdb"))
        assertFalse(BackupRetention.isOurBackup("my-ledger-backup-2026-03-10.ledgerdb"))
        assertFalse(BackupRetention.isOurBackup("ledger-backup-2026-03-10.ledgerdb.bak"))
        assertFalse(BackupRetention.isOurBackup("ledger-backup-2026-3-1.ledgerdb"))
        assertFalse(BackupRetention.isOurBackup("Ledger-Backup-2026-03-10.ledgerdb"))
        assertFalse(BackupRetention.isOurBackup("tax-return-2019.pdf"))
    }

    /** A nonsensical retention setting must not be read as "delete everything". */
    @Test
    fun `keeping none deletes nothing rather than everything`() {
        val names = listOf("ledger-backup-2026-03-01.ledgerdb", "ledger-backup-2026-03-02.ledgerdb")
        assertEquals(emptyList<String>(), BackupRetention.backupsToDelete(names, keep = 0))
        assertEquals(emptyList<String>(), BackupRetention.backupsToDelete(names, keep = -5))
    }

    @Test
    fun `fewer backups than the limit means nothing to prune`() {
        val names = listOf("ledger-backup-2026-03-01.ledgerdb")
        assertEquals(emptyList<String>(), BackupRetention.backupsToDelete(names, keep = 7))
        assertEquals(emptyList<String>(), BackupRetention.backupsToDelete(emptyList(), keep = 7))
    }

    /** Several runs on one day are separate files, and the oldest of them goes first. */
    @Test
    fun `same-day backups are ordered by their time`() {
        val names = listOf(
            "ledger-backup-2026-03-10-090000.ledgerdb",
            "ledger-backup-2026-03-10-235959.ledgerdb",
            "ledger-backup-2026-03-10-120000.ledgerdb"
        )
        assertEquals(
            listOf("ledger-backup-2026-03-10-090000.ledgerdb"),
            BackupRetention.backupsToDelete(names, keep = 2)
        )
    }

    /** A dated name sorts by date as plain text, which is the only reason the pruning is this simple. */
    @Test
    fun `the names sort chronologically as text`() {
        val chronological = listOf(
            "ledger-backup-2025-12-31.ledgerdb",
            "ledger-backup-2026-01-01.ledgerdb",
            "ledger-backup-2026-01-10.ledgerdb",
            "ledger-backup-2026-02-01.ledgerdb"
        )
        assertEquals(chronological, chronological.shuffled().sorted())
    }

    @Test
    fun `generated names match the pattern that protects everything else`() {
        val manual = BackupRetention.manualName(LocalDate.parse("2026-03-10"))
        val automatic = BackupRetention.automaticName(LocalDateTime.parse("2026-03-10T14:30:05"))

        assertEquals("ledger-backup-2026-03-10.ledgerdb", manual)
        assertEquals("ledger-backup-2026-03-10-143005.ledgerdb", automatic)
        assertTrue(BackupRetention.isOurBackup(manual))
        assertTrue(BackupRetention.isOurBackup(automatic))
    }

    /** Two automatic runs in the same day must not produce the same file name. */
    @Test
    fun `two runs on one day get different names`() {
        val morning = BackupRetention.automaticName(LocalDateTime.parse("2026-03-10T09:00:00"))
        val evening = BackupRetention.automaticName(LocalDateTime.parse("2026-03-10T21:00:00"))
        assertFalse(morning == evening)
        assertTrue(morning < evening)
    }
}
