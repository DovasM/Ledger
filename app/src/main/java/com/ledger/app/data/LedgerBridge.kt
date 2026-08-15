package com.ledger.app.data

import android.content.Context
import uniffi.ledger.LedgerDb
import uniffi.ledger.ShareInput
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
        val recurring = listRecurring()
        // The schedule itself lives in RecurringSchedule.kt and is unit-tested there; this half
        // only writes what it decided.
        val plan = planDueRecurring(recurring, LocalDate.now())

        for (post in plan.posts) {
            createTransaction(
                walletId   = post.walletId,
                title      = post.title,
                category   = post.category,
                amountCents = post.amountCents,
                isIncome   = post.isIncome,
                note       = "Auto-posted recurring",
                occurredAt = post.occurredOn.toString()
            )
        }
        for (r in recurring) {
            val advanced = plan.advancedTo[r.id] ?: continue
            updateRecurring(r.id, r.title, r.amountCents, r.category, r.frequency, advanced.toString())
        }
        return plan.posts.map { it.title }
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

    // ── Shared expenses ───────────────────────────────────────────────────────

    override fun listExpenseGroups() = db.listExpenseGroups()

    override fun createExpenseGroup(name: String, emoji: String, colorHex: String, memberNames: List<String>) =
        db.createExpenseGroup(name, emoji, colorHex, memberNames)

    override fun updateExpenseGroup(id: String, name: String, emoji: String, colorHex: String) =
        db.updateExpenseGroup(id, name, emoji, colorHex)

    override fun deleteExpenseGroup(id: String) = db.deleteExpenseGroup(id)

    override fun listGroupMembers(groupId: String) = db.listGroupMembers(groupId)

    override fun addGroupMember(groupId: String, name: String) = db.addGroupMember(groupId, name)

    override fun removeGroupMember(id: String) = db.removeGroupMember(id)

    override fun listSharedExpenses(groupId: String) = db.listSharedExpenses(groupId)

    override fun addSharedExpense(groupId: String, description: String, amountCents: Long, paidByMemberId: String, transactionId: String?, shares: List<ShareInput>, occurredAt: String?) =
        db.addSharedExpense(groupId, description, amountCents, paidByMemberId, transactionId, shares, occurredAt)

    override fun updateSharedExpense(id: String, description: String, amountCents: Long, paidByMemberId: String, shares: List<ShareInput>, occurredAt: String?) =
        db.updateSharedExpense(id, description, amountCents, paidByMemberId, shares, occurredAt)

    override fun addSharedExpenseFromWallet(groupId: String, description: String, amountCents: Long, paidByMemberId: String, walletId: String, category: String, shares: List<ShareInput>, occurredAt: String?) =
        db.addSharedExpenseFromWallet(groupId, description, amountCents, paidByMemberId, walletId, category, shares, occurredAt)

    override fun splitTransaction(transactionId: String, groupId: String, shares: List<ShareInput>) =
        db.splitTransaction(transactionId, groupId, shares)

    override fun deleteSharedExpense(id: String) = db.deleteSharedExpense(id)

    override fun deleteSharedExpenseWithTransaction(id: String) = db.deleteSharedExpenseWithTransaction(id)

    override fun deleteSharedExpenseKeepingTransaction(id: String) = db.deleteSharedExpenseKeepingTransaction(id)

    override fun recordSettlement(groupId: String, fromMemberId: String, toMemberId: String, amountCents: Long, transactionId: String?, occurredAt: String?) =
        db.recordSettlement(groupId, fromMemberId, toMemberId, amountCents, transactionId, occurredAt)

    override fun recordSettlementToWallet(groupId: String, fromMemberId: String, toMemberId: String, amountCents: Long, walletId: String, category: String, occurredAt: String?) =
        db.recordSettlementToWallet(groupId, fromMemberId, toMemberId, amountCents, walletId, category, occurredAt)

    override fun listSettlements(groupId: String) = db.listSettlements(groupId)

    override fun deleteSettlement(id: String) = db.deleteSettlement(id)

    override fun suggestSettlements(groupId: String) = db.suggestSettlements(groupId)

    override fun listExpenseShares(sharedExpenseId: String) = db.listExpenseShares(sharedExpenseId)

    // ── Backup & restore ──────────────────────────────────────────────────────

    override fun backupDatabase(destPath: String) = db.backupDatabase(destPath)

    override fun inspectBackup(path: String) = db.inspectBackup(path)

    override fun restoreBackup(path: String) = db.restoreBackup(path)

    // ── Price Alerts ──────────────────────────────────────────────────────────

    override fun listPriceAlerts() = db.listPriceAlerts()

    override fun createPriceAlert(symbol: String, assetName: String, targetPriceCents: Long, direction: String) =
        db.createPriceAlert(symbol, assetName, targetPriceCents, direction)

    override fun setPriceAlertActive(id: String, active: Boolean) =
        db.setPriceAlertActive(id, active)

    override fun deletePriceAlert(id: String) = db.deletePriceAlert(id)
}
