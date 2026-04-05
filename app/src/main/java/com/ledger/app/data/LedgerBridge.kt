package com.ledger.app.data

import android.content.Context
import uniffi.ledger.LedgerDb
import uniffi.ledger.openDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around the Rust LedgerDb (UniFFI-generated).
 * Call open() once at app startup (e.g. from LedgerApp or a Hilt module).
 */
@Singleton
class LedgerBridge @Inject constructor() {

    private lateinit var db: LedgerDb

    fun open(context: Context) {
        val dbFile = File(context.filesDir, "ledger.db")
        db = openDatabase(dbFile.absolutePath)
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    fun listTransactions(walletId: String, limit: UInt = 50u, offset: UInt = 0u) =
        db.listTransactions(walletId, limit, offset)

    fun createTransaction(walletId: String, title: String, category: String, amount: Double, isIncome: Boolean, note: String?) =
        db.createTransaction(walletId, title, category, amount, isIncome, note)

    fun updateTransaction(id: String, title: String, category: String, amount: Double, note: String?) =
        db.updateTransaction(id, title, category, amount, note)

    fun deleteTransaction(id: String) = db.deleteTransaction(id)

    // ── Wallets ──────────────────────────────────────────────────────────────

    fun listWallets() = db.listWallets()

    fun createWallet(name: String, description: String, initialBalance: Double) =
        db.createWallet(name, description, initialBalance)

    fun updateWallet(id: String, name: String, description: String) =
        db.updateWallet(id, name, description)

    fun deleteWallet(id: String) = db.deleteWallet(id)

    // ── Goals ────────────────────────────────────────────────────────────────

    fun listGoals() = db.listGoals()

    fun createGoal(name: String, targetAmount: Double, deadline: String?) =
        db.createGoal(name, targetAmount, deadline)

    fun addContribution(goalId: String, amount: Double) =
        db.addContribution(goalId, amount)

    // ── Statistics ───────────────────────────────────────────────────────────

    fun getMonthSummary(year: Int, month: Int) = db.getMonthSummary(year, month)
}
