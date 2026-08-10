package com.ledger.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.ui.viewmodel.SettingsViewModel
import com.ledger.app.ui.viewmodel.TransactionUiState
import uniffi.ledger.Transaction

// Every analysis screen needs the same thing: transactions minus any off-budget account, unless the
// user asked to see them. Putting it here keeps the rule in one place — eight screens each
// re-deriving it is how they drift apart.
//
// Screens that must show *everything* (the transaction list, search, editing) deliberately keep
// using state.transactions instead.
@Composable
fun rememberReportTransactions(state: TransactionUiState): List<Transaction> {
    val settings: SettingsViewModel = hiltViewModel()
    val includeOffBudget by settings.reportsIncludeOffBudget.collectAsStateWithLifecycle()
    return remember(state.transactions, state.offBudgetWalletIds, includeOffBudget) {
        state.forReports(includeOffBudget)
    }
}
