package com.ledger.app.data

import uniffi.ledger.*

interface ILedgerBridge {

    // ── Transactions ──────────────────────────────────────────────────────────
    fun listTransactions(walletId: String, limit: UInt = 50u, offset: UInt = 0u): List<Transaction>
    fun listAllTransactions(limit: UInt = 100u, offset: UInt = 0u): List<Transaction>
    fun createTransaction(walletId: String, title: String, category: String, amountCents: Long, isIncome: Boolean, note: String?, occurredAt: String? = null): Transaction
    fun updateTransaction(id: String, title: String, category: String, amountCents: Long, isIncome: Boolean, note: String?, occurredAt: String? = null): Transaction
    fun deleteTransaction(id: String)

    // ── Wallets ───────────────────────────────────────────────────────────────
    fun listWallets(): List<Wallet>
    fun createWallet(name: String, description: String, currency: String, initialBalanceCents: Long, offBudget: Boolean = false): Wallet
    fun updateWallet(id: String, name: String, description: String, currency: String, offBudget: Boolean = false): Wallet
    fun deleteWallet(id: String)
    fun countTransactionsForWallet(id: String): UInt

    // ── Transfers ─────────────────────────────────────────────────────────────
    fun listTransfers(limit: UInt = 500u, offset: UInt = 0u): List<Transfer>
    fun createTransfer(fromWalletId: String, toWalletId: String, amountCents: Long, note: String?, createdAt: String? = null): Transfer
    fun deleteTransfer(id: String)

    // ── Savings Goals ─────────────────────────────────────────────────────────
    fun listGoals(): List<SavingsGoal>
    fun createGoal(name: String, targetAmountCents: Long, deadline: String?): SavingsGoal
    fun updateGoal(id: String, name: String, targetAmountCents: Long, deadline: String?): SavingsGoal
    fun addContribution(goalId: String, amountCents: Long, note: String? = null, occurredAt: String? = null): SavingsGoal
    fun listGoalContributions(goalId: String): List<GoalContribution>
    fun deleteContribution(id: String): SavingsGoal
    fun deleteGoal(id: String)

    // ── Recurring Transactions ────────────────────────────────────────────────
    fun listRecurring(): List<RecurringTransaction>
    fun createRecurring(title: String, amountCents: Long, category: String, walletId: String, isIncome: Boolean, frequency: String, nextDate: String): RecurringTransaction
    fun updateRecurring(id: String, title: String, amountCents: Long, category: String, frequency: String, nextDate: String): RecurringTransaction
    fun deleteRecurring(id: String)
    fun applyDueRecurring(): List<String>

    // ── Statistics ────────────────────────────────────────────────────────────
    fun getMonthSummary(year: Int, month: Int): MonthSummary

    // ── Categories ────────────────────────────────────────────────────────────
    fun listCategories(): List<Category>
    fun createCategory(name: String, iconName: String, colorHex: String, isExpense: Boolean): Category
    fun updateCategory(id: String, name: String, iconName: String, colorHex: String, isExpense: Boolean): Category
    fun deleteCategory(id: String)
    fun countTransactionsForCategory(id: String): UInt

    // ── Budgets ───────────────────────────────────────────────────────────────
    fun listBudgets(): List<Budget>
    fun createBudget(categoryId: String?, walletId: String?, limitAmountCents: Long, period: String, alertThreshold: Double, carryOver: Boolean = false): Budget
    fun updateBudget(id: String, categoryId: String?, walletId: String?, limitAmountCents: Long, period: String, alertThreshold: Double, carryOver: Boolean = false): Budget
    fun deleteBudget(id: String)

    // ── Debts ─────────────────────────────────────────────────────────────────
    fun listDebts(): List<Debt>
    fun createDebt(name: String, debtType: String, totalAmountCents: Long, remainingAmountCents: Long, apr: Double, monthlyPaymentCents: Long): Debt
    fun updateDebt(id: String, name: String, debtType: String, totalAmountCents: Long, remainingAmountCents: Long, apr: Double, monthlyPaymentCents: Long): Debt
    fun deleteDebt(id: String)
    fun listDebtPayments(debtId: String): List<DebtPayment>
    fun addDebtPayment(debtId: String, amountCents: Long, note: String? = null, occurredAt: String? = null): Debt
    fun deleteDebtPayment(id: String): Debt

    // ── Tags ──────────────────────────────────────────────────────────────────
    fun listTags(): List<Tag>
    fun createTag(name: String): Tag
    fun deleteTag(id: String)
    fun addTagToTransaction(transactionId: String, tagId: String)
    fun removeTagFromTransaction(transactionId: String, tagId: String)
    fun listTransactionTags(transactionId: String): List<Tag>

    // ── Price Alerts ──────────────────────────────────────────────────────────
    fun listPriceAlerts(): List<PriceAlert>
    fun createPriceAlert(symbol: String, assetName: String, targetPriceCents: Long, direction: String): PriceAlert
    fun setPriceAlertActive(id: String, active: Boolean): PriceAlert
    fun deletePriceAlert(id: String)
}
