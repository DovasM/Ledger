package com.ledger.app.ui.viewmodel

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.LedgerBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject

data class MmAccount(
    val uid: String,
    val title: String,
    val currencyCode: String,
    val balance: Double
)

data class MmCategory(
    val uid: String,
    val title: String,
    val isExpense: Boolean,
    val ledgerIcon: String,
    val colorHex: String
)

data class MmTransaction(
    val uid: String,
    val isIncome: Boolean,
    val amount: Double,
    val date: String,
    val comment: String,
    val accountUid: String,
    val categoryTitle: String
)

data class MmParseResult(
    val accounts: List<MmAccount>,
    val categories: List<MmCategory>,
    val transactions: List<MmTransaction>
)

data class ImportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val parseResult: MmParseResult? = null,
    val selectedAccountUids: Set<String> = emptySet(),
    val replaceExisting: Boolean = true,
    val importedCount: Int = 0,
    val isDone: Boolean = false
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val bridge: LedgerBridge,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun parseMmBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null, parseResult = null)
            try {
                val tempDir = File(context.cacheDir, "mmimport").also { it.mkdirs() }
                val dbFile = File(tempDir, "MyFinance.db")

                // Copy the mmbackup to a local temp file first — ZipInputStream can fail
                // silently on content:// URIs; ZipFile (which seeks the central directory)
                // is much more reliable once we have a real file path.
                dbFile.delete()
                val archiveFile = File(tempDir, "backup.mmbackup")
                context.contentResolver.openInputStream(uri)
                    ?.use { input -> archiveFile.outputStream().use { out -> input.copyTo(out) } }
                    ?: throw IllegalStateException("Could not open file")

                // Read entire file once — we'll detect format and handle headers in one pass.
                // Some mmbackup versions prepend non-ZIP bytes before the actual ZIP data,
                // so we scan for the first PK local-file-header signature (50 4B 03 04).
                val bytes = archiveFile.readBytes()

                fun isSqliteMagic(b: ByteArray) =
                    b.size >= 2 && b[0] == 0x53.toByte() && b[1] == 0x51.toByte() // "SQ"

                fun findPkOffset(b: ByteArray): Int {
                    for (i in 0..b.size - 4) {
                        if (b[i] == 0x50.toByte() && b[i+1] == 0x4B.toByte() &&
                            b[i+2] == 0x03.toByte() && b[i+3] == 0x04.toByte()) return i
                    }
                    return -1
                }

                when {
                    isSqliteMagic(bytes) -> {
                        // Already a raw SQLite database
                        archiveFile.copyTo(dbFile, overwrite = true)
                    }
                    else -> {
                        val pkOffset = findPkOffset(bytes)
                        if (pkOffset < 0) {
                            val hex = bytes.take(8).joinToString(" ") { "%02X".format(it) }
                            throw IllegalStateException(
                                "No ZIP data found in file (first bytes: $hex). " +
                                "Make sure to select the .mmbackup file from Money Manager's backup."
                            )
                        }
                        // Extract using ZipInputStream starting from the PK offset
                        // (handles files that have a custom header prepended before the ZIP data)
                        var found = false
                        ZipInputStream(bytes.inputStream().also { it.skip(pkOffset.toLong()) }).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name.substringAfterLast('/')
                                if (name.equals("MyFinance.db", ignoreCase = true) ||
                                    (!found && name.endsWith(".db", ignoreCase = true))) {
                                    dbFile.outputStream().use { dst -> zip.copyTo(dst) }
                                    found = true
                                    if (name.equals("MyFinance.db", ignoreCase = true)) break
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                        if (!found) throw IllegalStateException(
                            "No database file found inside backup (ZIP offset: $pkOffset)."
                        )
                    }
                }

                val sqlDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )

                val accounts = parseAccounts(sqlDb)
                val categories = parseCategories(sqlDb)
                val transactions = parseTransactions(sqlDb)
                sqlDb.close()

                _state.value = _state.value.copy(
                    isLoading = false,
                    parseResult = MmParseResult(accounts, categories, transactions),
                    selectedAccountUids = accounts.map { it.uid }.toSet()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to parse backup")
            }
        }
    }

    fun toggleAccount(uid: String) {
        val current = _state.value.selectedAccountUids
        _state.value = _state.value.copy(
            selectedAccountUids = if (uid in current) current - uid else current + uid
        )
    }

    fun setReplaceExisting(value: Boolean) {
        _state.value = _state.value.copy(replaceExisting = value)
    }

    fun confirmImport() {
        val result = _state.value.parseResult ?: return
        val selected = _state.value.selectedAccountUids
        val replace = _state.value.replaceExisting
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Wipe all existing data before importing if replace mode is on
                if (replace) {
                    bridge.listAllTransactions(10000u).forEach { bridge.deleteTransaction(it.id) }
                    bridge.listRecurring().forEach { bridge.deleteRecurring(it.id) }
                    bridge.listBudgets().forEach { bridge.deleteBudget(it.id) }
                    bridge.listWallets().forEach { bridge.deleteWallet(it.id) }
                    bridge.listCategories().forEach { bridge.deleteCategory(it.id) }
                }

                // Import selected accounts → wallets; track uid→walletId
                val walletIdMap = mutableMapOf<String, String>()
                for (acc in result.accounts) {
                    if (acc.uid !in selected) continue
                    val w = bridge.createWallet(acc.title, acc.currencyCode, acc.balance)
                    walletIdMap[acc.uid] = w.id
                }

                // Import categories
                for (cat in result.categories) {
                    if (cat.title.isBlank()) continue
                    if (!replace) {
                        val existing = bridge.listCategories().find { it.name == cat.title }
                        if (existing != null) continue
                    }
                    bridge.createCategory(cat.title, cat.ledgerIcon, cat.colorHex, cat.isExpense)
                }

                // Import transactions for selected accounts only
                var count = 0
                val txsForSelected = result.transactions.filter { it.accountUid in selected }
                for (tx in txsForSelected) {
                    val walletId = walletIdMap[tx.accountUid] ?: continue
                    val categoryName = tx.categoryTitle.takeIf { it.isNotBlank() } ?: "Other"
                    bridge.createTransaction(
                        walletId = walletId,
                        title = tx.comment.takeIf { it.isNotBlank() } ?: if (tx.isIncome) "Income" else "Expense",
                        category = categoryName,
                        amount = tx.amount,
                        isIncome = tx.isIncome,
                        note = null,
                        createdAt = tx.date
                    )
                    count++
                }

                _state.value = _state.value.copy(isLoading = false, importedCount = count, isDone = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Import failed")
            }
        }
    }

    fun reset() {
        _state.value = ImportUiState()
    }

    // ── Internal parsing ──────────────────────────────────────────────────────

    private fun parseAccounts(db: SQLiteDatabase): List<MmAccount> {
        // account_balance stores the balance AT BACKUP TIME (current balance).
        // Since Ledger computes wallet balance as initialBalance + transaction net,
        // we must derive the opening balance as: current - net_transactions,
        // so that after importing all transactions the final balance matches MM.
        val currentBalances = mutableMapOf<String, Long>()
        db.rawQuery("SELECT uid, value FROM account_balance", null).use { c ->
            while (c.moveToNext()) currentBalances[c.getString(0)] = c.getLong(1)
        }

        // Net transaction amount per account (income − expense, in raw integer cents)
        val txNets = mutableMapOf<String, Long>()
        db.rawQuery("""
            SELECT la.otherUid,
                   SUM(CASE WHEN t.type='Income'  THEN  t.amountInDefaultCurrency ELSE 0 END) -
                   SUM(CASE WHEN t.type='Expense' THEN  t.amountInDefaultCurrency ELSE 0 END)
            FROM "transaction" t
            JOIN sync_link la ON la.entityType='Transaction' AND la.entityUid=t.uid
                              AND la.otherType='Account' AND la.isRemoved=0
            WHERE t.isRemoved=0
            GROUP BY la.otherUid
        """.trimIndent(), null).use { c ->
            while (c.moveToNext()) txNets[c.getString(0)] = c.getLong(1)
        }

        val accounts = mutableListOf<MmAccount>()
        db.rawQuery(
            "SELECT uid, title, currencyCode FROM account WHERE isRemoved=0 AND isActive=1",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val uid = c.getString(0)
                val currentCents = currentBalances[uid] ?: 0L
                val netCents     = txNets[uid] ?: 0L
                val openingBalance = (currentCents - netCents) / 100.0
                accounts.add(
                    MmAccount(
                        uid = uid,
                        title = c.getString(1) ?: "Account",
                        currencyCode = c.getString(2) ?: "EUR",
                        balance = openingBalance
                    )
                )
            }
        }
        return accounts
    }

    private fun parseCategories(db: SQLiteDatabase): List<MmCategory> {
        val cats = mutableListOf<MmCategory>()
        db.rawQuery(
            "SELECT uid, title, type, icon, color FROM category WHERE isRemoved=0",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1) ?: ""
                if (title.isBlank()) continue
                val isExpense = c.getString(2)?.equals("Expense", ignoreCase = true) ?: true
                val mmIcon = c.getString(3) ?: "other"
                val argb = c.getInt(4)
                cats.add(
                    MmCategory(
                        uid = c.getString(0),
                        title = title,
                        isExpense = isExpense,
                        ledgerIcon = mapMmIcon(mmIcon),
                        colorHex = argbToHex(argb)
                    )
                )
            }
        }
        return cats
    }

    private fun parseTransactions(db: SQLiteDatabase): List<MmTransaction> {
        val txs = mutableListOf<MmTransaction>()
        val query = """
            SELECT t.uid, t.type, t.amountInDefaultCurrency, t.date, t.comment,
                   acc.uid, cat.title
            FROM "transaction" t
            LEFT JOIN sync_link la ON la.entityType='Transaction' AND la.entityUid=t.uid AND la.otherType='Account' AND la.isRemoved=0
            LEFT JOIN account acc ON acc.uid=la.otherUid
            LEFT JOIN sync_link lc ON lc.entityType='Transaction' AND lc.entityUid=t.uid AND lc.otherType='Category' AND lc.isRemoved=0
            LEFT JOIN category cat ON cat.uid=lc.otherUid
            WHERE t.isRemoved=0
        """.trimIndent()
        db.rawQuery(query, null).use { c ->
            while (c.moveToNext()) {
                txs.add(
                    MmTransaction(
                        uid = c.getString(0),
                        isIncome = c.getString(1)?.equals("Income", ignoreCase = true) ?: false,
                        amount = c.getLong(2) / 100.0,
                        date = c.getString(3) ?: "",
                        comment = c.getString(4) ?: "",
                        accountUid = c.getString(5) ?: "",
                        categoryTitle = c.getString(6) ?: ""
                    )
                )
            }
        }
        return txs
    }

    private fun mapMmIcon(mmIcon: String): String = when (mmIcon.lowercase()) {
        "sport", "sports", "basketball", "football" -> "fitness_center"
        "bank", "calculator", "percent", "percents" -> "payments"
        "travels", "travel", "plane", "trip" -> "flight"
        "education" -> "school"
        "sweets", "pizza", "fastfood", "eating" -> "restaurant"
        "health", "medicine", "doctor", "medical" -> "health_and_safety"
        "cafe", "coffee", "tea" -> "local_cafe"
        "shopping", "shop", "clothes", "dress" -> "shopping_bag"
        "home", "house", "rent" -> "home"
        "car", "transport", "auto", "fuel", "gas" -> "directions_car"
        "entertainment", "movie", "cinema", "film" -> "movie"
        "music", "headphones" -> "music_note"
        "art", "design", "brush" -> "brush"
        "pets", "animal", "cat", "dog" -> "pets"
        "game", "games", "gaming" -> "sports_esports"
        "grocery", "supermarket", "food" -> "local_grocery_store"
        "hospital" -> "local_hospital"
        "work", "job", "office" -> "work"
        "restaurant" -> "restaurant"
        else -> "payments"
    }

    private fun argbToHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return "#%02X%02X%02X".format(r, g, b)
    }
}
