package com.ledger.app.data

import android.content.Context
import uniffi.ledger.LedgerDb
import uniffi.ledger.openDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerBridge @Inject constructor() {

    private lateinit var db: LedgerDb

    fun open(context: Context) {
        val dbFile = File(context.filesDir, "ledger.db")
        db = openDatabase(dbFile.absolutePath)
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    fun listTransactions(walletId: String, limit: UInt = 50u, offset: UInt = 0u) =
        db.listTransactions(walletId, limit, offset)

    fun listAllTransactions(limit: UInt = 100u, offset: UInt = 0u) =
        db.listAllTransactions(limit, offset)

    fun createTransaction(walletId: String, title: String, category: String, amount: Double, isIncome: Boolean, note: String?) =
        db.createTransaction(walletId, title, category, amount, isIncome, note)

    fun updateTransaction(id: String, title: String, category: String, amount: Double, isIncome: Boolean, note: String?) =
        db.updateTransaction(id, title, category, amount, isIncome, note)

    fun deleteTransaction(id: String) = db.deleteTransaction(id)

    // ── Wallets ───────────────────────────────────────────────────────────────

    fun listWallets() = db.listWallets()

    fun createWallet(name: String, description: String, initialBalance: Double) =
        db.createWallet(name, description, initialBalance)

    fun updateWallet(id: String, name: String, description: String) =
        db.updateWallet(id, name, description)

    fun deleteWallet(id: String) = db.deleteWallet(id)

    // ── Savings Goals ─────────────────────────────────────────────────────────

    fun listGoals() = db.listGoals()

    fun createGoal(name: String, targetAmount: Double, deadline: String?) =
        db.createGoal(name, targetAmount, deadline)

    fun addContribution(goalId: String, amount: Double) =
        db.addContribution(goalId, amount)

    fun deleteGoal(id: String) = db.deleteGoal(id)

    // ── Statistics ────────────────────────────────────────────────────────────

    fun getMonthSummary(year: Int, month: Int) = db.getMonthSummary(year, month)

    // ── Categories ────────────────────────────────────────────────────────────

    fun listCategories() = db.listCategories()

    fun createCategory(name: String, iconName: String, colorHex: String, isExpense: Boolean) =
        db.createCategory(name, iconName, colorHex, isExpense)

    fun updateCategory(id: String, name: String, iconName: String, colorHex: String, isExpense: Boolean) =
        db.updateCategory(id, name, iconName, colorHex, isExpense)

    fun deleteCategory(id: String) = db.deleteCategory(id)

    // ── Budgets ───────────────────────────────────────────────────────────────

    fun listBudgets() = db.listBudgets()

    fun createBudget(categoryId: String, limitAmount: Double, period: String, alertThreshold: Double) =
        db.createBudget(categoryId, limitAmount, period, alertThreshold)

    fun updateBudget(id: String, limitAmount: Double, period: String, alertThreshold: Double) =
        db.updateBudget(id, limitAmount, period, alertThreshold)

    fun deleteBudget(id: String) = db.deleteBudget(id)

    // ── Debts ─────────────────────────────────────────────────────────────────

    fun listDebts() = db.listDebts()

    fun createDebt(name: String, debtType: String, totalAmount: Double, remainingAmount: Double, apr: Double, monthlyPayment: Double) =
        db.createDebt(name, debtType, totalAmount, remainingAmount, apr, monthlyPayment)

    fun updateDebt(id: String, name: String, debtType: String, totalAmount: Double, remainingAmount: Double, apr: Double, monthlyPayment: Double) =
        db.updateDebt(id, name, debtType, totalAmount, remainingAmount, apr, monthlyPayment)

    fun deleteDebt(id: String) = db.deleteDebt(id)

    // ── Recurring Transactions ────────────────────────────────────────────────

    fun listRecurring() = db.listRecurring()

    fun createRecurring(title: String, amount: Double, category: String, walletId: String, isIncome: Boolean, frequency: String, nextDate: String) =
        db.createRecurring(title, amount, category, walletId, isIncome, frequency, nextDate)

    fun updateRecurring(id: String, title: String, amount: Double, category: String, frequency: String, nextDate: String) =
        db.updateRecurring(id, title, amount, category, frequency, nextDate)

    fun deleteRecurring(id: String) = db.deleteRecurring(id)

    // ── Tags ──────────────────────────────────────────────────────────────────

    fun listTags() = db.listTags()

    fun createTag(name: String) = db.createTag(name)

    fun deleteTag(id: String) = db.deleteTag(id)

    fun addTagToTransaction(transactionId: String, tagId: String) =
        db.addTagToTransaction(transactionId, tagId)

    fun removeTagFromTransaction(transactionId: String, tagId: String) =
        db.removeTagFromTransaction(transactionId, tagId)

    fun listTransactionTags(transactionId: String) =
        db.listTransactionTags(transactionId)

    // ── Price Alerts ──────────────────────────────────────────────────────────

    fun listPriceAlerts() = db.listPriceAlerts()

    fun createPriceAlert(symbol: String, assetName: String, targetPrice: Double, direction: String) =
        db.createPriceAlert(symbol, assetName, targetPrice, direction)

    fun setPriceAlertActive(id: String, active: Boolean) =
        db.setPriceAlertActive(id, active)

    fun deletePriceAlert(id: String) = db.deletePriceAlert(id)
}
