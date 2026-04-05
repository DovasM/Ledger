package com.ledger.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ledger.app.ui.screens.*

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Notifications : Screen("notifications")
    object Activity : Screen("activity")
    object Transactions : Screen("transactions")
    object AddTransaction : Screen("add_transaction")
    object EditTransaction : Screen("edit_transaction/{id}") {
        fun createRoute(id: Long) = "edit_transaction/$id"
    }
    object WalletsList : Screen("wallets")
    object WalletDetails : Screen("wallet_details/{id}") {
        fun createRoute(id: Long) = "wallet_details/$id"
    }
    object AddWallet : Screen("add_wallet")
    object EditWallet : Screen("edit_wallet/{id}") {
        fun createRoute(id: Long) = "edit_wallet/$id"
    }
    object InvestmentPortfolio : Screen("investments")
    object ConnectAccount : Screen("connect_account")
    object AssetDetails : Screen("asset/{symbol}") {
        fun createRoute(symbol: String) = "asset/$symbol"
    }
    object BudgetInsights : Screen("budget")
    object MonthlyStatistics : Screen("statistics")
    object SavingsGoals : Screen("savings")
    object AddGoal : Screen("add_goal")
    object EditGoal : Screen("edit_goal/{id}") {
        fun createRoute(id: Long) = "edit_goal/$id"
    }
    object GoalDetails : Screen("goal/{id}") {
        fun createRoute(id: Long) = "goal/$id"
    }
    object CategoriesManagement : Screen("categories")
    object AddCategory : Screen("add_category")
    object EditCategory : Screen("edit_category/{name}") {
        fun createRoute(name: String) = "edit_category/$name"
    }
    object CustomReport : Screen("custom_report")
    object CategoryTransactions : Screen("category_transactions/{name}") {
        fun createRoute(name: String) = "category_transactions/$name"
    }
    object Settings : Screen("settings")
    object EditProfile : Screen("edit_profile")
    object ConnectedAccountDetails : Screen("connected_account/{name}") {
        fun createRoute(name: String) = "connected_account/$name"
    }
    object Budgets : Screen("budgets")
    object AddBudget : Screen("add_budget")
    object EditBudget : Screen("edit_budget/{category}") {
        fun createRoute(category: String) = "edit_budget/$category"
    }
    object RecurringTransactions : Screen("recurring")
    object AddRecurring : Screen("add_recurring")
    object GlobalSearch : Screen("search")
    object CashFlowForecast : Screen("cash_flow")
    object AnnualSummary : Screen("annual_summary")
    object PriceAlerts : Screen("price_alerts")
    object InvestmentPnL : Screen("investment_pnl")
    object Dividends : Screen("dividends")
    // New screens
    object NetWorth : Screen("net_worth")
    object DebtTracker : Screen("debt_tracker")
    object AddDebt : Screen("add_debt")
    object EditDebt : Screen("edit_debt/{id}") {
        fun createRoute(id: Long) = "edit_debt/$id"
    }
    object SharedExpenses : Screen("shared_expenses")
    object TransactionImport : Screen("import")
    object FinancialCalendar : Screen("calendar")
    object SpendingStreaks : Screen("streaks")
    object AppearanceSettings : Screen("appearance")
    object NotificationSettings : Screen("notification_settings")
    object SecuritySettings : Screen("security_settings")
    object HelpSupport : Screen("help_support")
    object WidgetSettings : Screen("widget_settings")
}

@Composable
fun LedgerNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) { DashboardScreen(navController) }
        composable(Screen.Notifications.route) { NotificationsScreen(navController) }
        composable(Screen.Activity.route) { ActivityScreen(navController) }
        composable(Screen.Transactions.route) { TransactionsScreen(navController) }
        composable(Screen.AddTransaction.route) { AddTransactionScreen(navController) }
        composable(Screen.EditTransaction.route) { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            EditTransactionScreen(navController, id)
        }
        composable(Screen.WalletsList.route) { WalletsListScreen(navController) }
        composable(Screen.WalletDetails.route) { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            WalletDetailsScreen(navController, id)
        }
        composable(Screen.AddWallet.route) { AddWalletScreen(navController) }
        composable(Screen.EditWallet.route) { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            EditWalletScreen(navController, id)
        }
        composable(Screen.InvestmentPortfolio.route) { InvestmentPortfolioScreen(navController) }
        composable(Screen.ConnectAccount.route) { ConnectAccountScreen(navController) }
        composable(Screen.AssetDetails.route) { backStack ->
            val symbol = backStack.arguments?.getString("symbol") ?: return@composable
            AssetDetailsScreen(navController, symbol)
        }
        composable(Screen.BudgetInsights.route) { BudgetInsightsScreen(navController) }
        composable(Screen.MonthlyStatistics.route) { MonthlyStatisticsScreen(navController) }
        composable(Screen.SavingsGoals.route) { SavingsGoalsScreen(navController) }
        composable(Screen.AddGoal.route) { AddGoalScreen(navController) }
        composable(Screen.EditGoal.route) { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            EditGoalScreen(navController, id)
        }
        composable(Screen.GoalDetails.route) { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            GoalDetailsScreen(navController, id)
        }
        composable(Screen.CategoriesManagement.route) { CategoriesManagementScreen(navController) }
        composable(Screen.AddCategory.route) { AddCategoryScreen(navController) }
        composable(Screen.EditCategory.route) { backStack ->
            val name = backStack.arguments?.getString("name") ?: return@composable
            EditCategoryScreen(navController, name)
        }
        composable(Screen.CustomReport.route) { CustomReportScreen(navController) }
        composable(Screen.CategoryTransactions.route) { backStack ->
            val name = backStack.arguments?.getString("name") ?: return@composable
            CategoryTransactionsScreen(navController, name)
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.EditProfile.route) { EditProfileScreen(navController) }
        composable(Screen.ConnectedAccountDetails.route) { backStack ->
            val name = backStack.arguments?.getString("name") ?: return@composable
            ConnectedAccountDetailsScreen(navController, name)
        }
        composable(Screen.Budgets.route) { BudgetsScreen(navController) }
        composable(Screen.AddBudget.route) { AddEditBudgetScreen(navController) }
        composable(Screen.EditBudget.route) { backStack ->
            val category = backStack.arguments?.getString("category") ?: return@composable
            AddEditBudgetScreen(navController, categoryName = category)
        }
        composable(Screen.RecurringTransactions.route) { RecurringTransactionsScreen(navController) }
        composable(Screen.AddRecurring.route) { AddRecurringScreen(navController) }
        composable(Screen.GlobalSearch.route) { GlobalSearchScreen(navController) }
        composable(Screen.CashFlowForecast.route) { CashFlowForecastScreen(navController) }
        composable(Screen.AnnualSummary.route) { AnnualSummaryScreen(navController) }
        composable(Screen.PriceAlerts.route) { PriceAlertsScreen(navController) }
        composable(Screen.InvestmentPnL.route) { InvestmentPnLScreen(navController) }
        composable(Screen.Dividends.route) { DividendsScreen(navController) }
        // New screens
        composable(Screen.NetWorth.route) { NetWorthScreen(navController) }
        composable(Screen.DebtTracker.route) { DebtTrackerScreen(navController) }
        composable(Screen.AddDebt.route) { AddEditDebtScreen(navController) }
        composable(Screen.EditDebt.route) { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            AddEditDebtScreen(navController, debtId = id)
        }
        composable(Screen.SharedExpenses.route) { SharedExpensesScreen(navController) }
        composable(Screen.TransactionImport.route) { TransactionImportScreen(navController) }
        composable(Screen.FinancialCalendar.route) { FinancialCalendarScreen(navController) }
        composable(Screen.SpendingStreaks.route) { SpendingStreaksScreen(navController) }
        composable(Screen.AppearanceSettings.route) { AppearanceSettingsScreen(navController) }
        composable(Screen.NotificationSettings.route) { NotificationSettingsScreen(navController) }
        composable(Screen.SecuritySettings.route) { SecuritySettingsScreen(navController) }
        composable(Screen.HelpSupport.route) { HelpSupportScreen(navController) }
        composable(Screen.WidgetSettings.route) { WidgetSettingsScreen(navController) }
    }
}
