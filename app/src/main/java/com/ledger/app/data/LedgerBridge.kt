package com.ledger.app.data

import android.content.Context
import uniffi.ledger.LedgerDb
import uniffi.ledger.openDatabase
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerBridge @Inject constructor() : ILedgerBridge {

    private lateinit var db: LedgerDb

    fun open(context: Context) {
        val dbFile = File(context.filesDir, "ledger.db")
        db = openDatabase(dbFile.absolutePath)
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    override fun listTransactions(walletId: String, limit: UInt, offset: UInt) =
        db.listTransactions(walletId, limit, offset)

    override fun listAllTransactions(limit: UInt, offset: UInt) =
        db.listAllTransactions(limit, offset)

    override fun createTransaction(walletId: String, title: String, category: String, amountCents: Long, isIncome: Boolean, note: String?, occurredAt: String?) =
        db.createTransaction(walletId, title, category, amountCents, isIncome, note, occurredAt)

    override fun updateTransaction(id: String, title: String, category: String, amountCents: Long, isIncome: Boolean, note: String?, occurredAt: String?) =
        db.updateTransaction(id, title, category, amountCents, isIncome, note, occurredAt)

    override fun deleteTransaction(id: String) = db.deleteTransaction(id)

    // ── Wallets ───────────────────────────────────────────────────────────────

    override fun listWallets() = db.listWallets()

    override fun createWallet(name: String, description: String, currency: String, initialBalanceCents: Long, offBudget: Boolean) =
        db.createWallet(name, description, currency, initialBalanceCents, offBudget)

    override fun updateWallet(id: String, name: String, description: String, currency: String, offBudget: Boolean) =
        db.updateWallet(id, name, description, currency, offBudget)

    override fun deleteWallet(id: String) = db.deleteWallet(id)

    override fun countTransactionsForWallet(id: String) = db.countTransactionsForWallet(id)

    // ── Transfers ─────────────────────────────────────────────────────────────

    override fun listTransfers(limit: UInt, offset: UInt) = db.listTransfers(limit, offset)

    override fun createTransfer(fromWalletId: String, toWalletId: String, amountCents: Long, note: String?, createdAt: String?) =
        db.createTransfer(fromWalletId, toWalletId, amountCents, note, createdAt)

    override fun deleteTransfer(id: String) = db.deleteTransfer(id)

    // ── Savings Goals ─────────────────────────────────────────────────────────

    override fun listGoals() = db.listGoals()

    override fun createGoal(name: String, targetAmountCents: Long, deadline: String?) =
        db.createGoal(name, targetAmountCents, deadline)

    override fun updateGoal(id: String, name: String, targetAmountCents: Long, deadline: String?) =
        try {
            db.updateGoal(id, name, targetAmountCents, deadline)
        } catch (_: UnsatisfiedLinkError) {
            // Rust .so not yet rebuilt — fallback: delete and recreate preserving currentAmount
            val existing = db.listGoals().find { it.id == id }
            val savedAmount = existing?.currentAmountCents ?: 0L
            db.deleteGoal(id)
            val newGoal = db.createGoal(name, targetAmountCents, deadline)
            if (savedAmount > 0) db.addContribution(newGoal.id, savedAmount, null, null)
            newGoal
        }

    override fun addContribution(goalId: String, amountCents: Long, note: String?, occurredAt: String?) =
        db.addContribution(goalId, amountCents, note, occurredAt)

    override fun listGoalContributions(goalId: String) = db.listGoalContributions(goalId)

    override fun deleteContribution(id: String) = db.deleteContribution(id)

    override fun deleteGoal(id: String) = db.deleteGoal(id)

    // ── Recurring Transactions ────────────────────────────────────────────────

    override fun listRecurring() = db.listRecurring()

    override fun createRecurring(title: String, amountCents: Long, category: String, walletId: String, isIncome: Boolean, frequency: String, nextDate: String) =
        db.createRecurring(title, amountCents, category, walletId, isIncome, frequency, nextDate)

    override fun updateRecurring(id: String, title: String, amountCents: Long, category: String, frequency: String, nextDate: String) =
        db.updateRecurring(id, title, amountCents, category, frequency, nextDate)

    override fun deleteRecurring(id: String) = db.deleteRecurring(id)

    /**
     * Finds all recurring transactions whose next_date is today or in the past,
     * posts them as real transactions, and advances next_date by their frequency.
     * Handles multiple missed periods (e.g. a monthly item missed for 3 months posts 3 times).
     * Returns the titles of every transaction that was posted.
     */
    override fun applyDueRecurring(): List<String> {
        val today = LocalDate.now()
        val applied = mutableListOf<String>()
        for (r in listRecurring()) {
            var nextDate = runCatching { LocalDate.parse(r.nextDate.take(10)) }.getOrNull() ?: continue
            while (!nextDate.isAfter(today)) {
                createTransaction(
                    walletId  = r.walletId,
                    title     = r.title,
                    category  = r.category,
                    amountCents    = r.amountCents,
                    isIncome  = r.isIncome,
                    note      = "Auto-posted recurring",
                    occurredAt = nextDate.toString()
                )
                applied.add(r.title)
                nextDate = advanceDate(nextDate, r.frequency)
            }
            updateRecurring(r.id, r.title, r.amountCents, r.category, r.frequency, nextDate.toString())
        }
        return applied
    }

    private fun advanceDate(date: LocalDate, frequency: String): LocalDate = when (frequency.lowercase()) {
        "daily"      -> date.plusDays(1)
        "weekly"     -> date.plusWeeks(1)
        "biweekly"   -> date.plusWeeks(2)
        "monthly"    -> date.plusMonths(1)
        "quarterly"  -> date.plusMonths(3)
        "yearly"     -> date.plusYears(1)
        else         -> date.plusMonths(1)
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    override fun getMonthSummary(year: Int, month: Int) = db.getMonthSummary(year, month)

    // ── Categories ────────────────────────────────────────────────────────────

    override fun listCategories() = db.listCategories()

    override fun createCategory(name: String, iconName: String, colorHex: String, isExpense: Boolean) =
        db.createCategory(name, iconName, colorHex, isExpense)

    override fun updateCategory(id: String, name: String, iconName: String, colorHex: String, isExpense: Boolean) =
        db.updateCategory(id, name, iconName, colorHex, isExpense)

    override fun deleteCategory(id: String) = db.deleteCategory(id)

    override fun countTransactionsForCategory(id: String) = db.countTransactionsForCategory(id)

    // ── Budgets ───────────────────────────────────────────────────────────────

    override fun listBudgets() = db.listBudgets()

    override fun createBudget(categoryId: String?, walletId: String?, limitAmountCents: Long, period: String, alertThreshold: Double, carryOver: Boolean) =
        db.createBudget(categoryId, walletId, limitAmountCents, period, alertThreshold, carryOver)

    override fun updateBudget(id: String, categoryId: String?, walletId: String?, limitAmountCents: Long, period: String, alertThreshold: Double, carryOver: Boolean) =
        db.updateBudget(id, categoryId, walletId, limitAmountCents, period, alertThreshold, carryOver)

    override fun deleteBudget(id: String) = db.deleteBudget(id)

    // ── Debts ─────────────────────────────────────────────────────────────────

    override fun listDebts() = db.listDebts()

    override fun createDebt(name: String, debtType: String, totalAmountCents: Long, remainingAmountCents: Long, apr: Double, monthlyPaymentCents: Long) =
        db.createDebt(name, debtType, totalAmountCents, remainingAmountCents, apr, monthlyPaymentCents)

    override fun updateDebt(id: String, name: String, debtType: String, totalAmountCents: Long, remainingAmountCents: Long, apr: Double, monthlyPaymentCents: Long) =
        db.updateDebt(id, name, debtType, totalAmountCents, remainingAmountCents, apr, monthlyPaymentCents)

    override fun deleteDebt(id: String) = db.deleteDebt(id)

    override fun listDebtPayments(debtId: String) = db.listDebtPayments(debtId)

    override fun addDebtPayment(debtId: String, amountCents: Long, note: String?, occurredAt: String?) =
        db.addDebtPayment(debtId, amountCents, note, occurredAt)

    override fun deleteDebtPayment(id: String) = db.deleteDebtPayment(id)

    // ── Tags ──────────────────────────────────────────────────────────────────

    override fun listTags() = db.listTags()

    override fun createTag(name: String) = db.createTag(name)

    override fun deleteTag(id: String) = db.deleteTag(id)

    override fun addTagToTransaction(transactionId: String, tagId: String) =
        db.addTagToTransaction(transactionId, tagId)

    override fun removeTagFromTransaction(transactionId: String, tagId: String) =
        db.removeTagFromTransaction(transactionId, tagId)

    override fun listTransactionTags(transactionId: String) =
        db.listTransactionTags(transactionId)

    // ── Price Alerts ──────────────────────────────────────────────────────────

    override fun listPriceAlerts() = db.listPriceAlerts()

    override fun createPriceAlert(symbol: String, assetName: String, targetPriceCents: Long, direction: String) =
        db.createPriceAlert(symbol, assetName, targetPriceCents, direction)

    override fun setPriceAlertActive(id: String, active: Boolean) =
        db.setPriceAlertActive(id, active)

    override fun deletePriceAlert(id: String) = db.deletePriceAlert(id)
}
