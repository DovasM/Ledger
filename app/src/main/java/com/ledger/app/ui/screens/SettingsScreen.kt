package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var currencyCode by remember { mutableStateOf("USD") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Profile section
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", style = MaterialTheme.typography.titleLarge, color = OnPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Alex Johnson", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("alex@example.com", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    IconButton(onClick = { navController.navigate(Screen.EditProfile.route) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit profile", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Finance
            SettingsSection(title = "Finance") {
                SettingsNavItem(Icons.Filled.AccountBalance, Color(0xFF00513F), "Net Worth", "Assets, liabilities and total net worth") { navController.navigate(Screen.NetWorth.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.CreditCard, Tertiary, "Debt Tracker", "Track loans, credit cards and payoff progress") { navController.navigate(Screen.DebtTracker.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Group, Color(0xFF1565C0), "Shared Expenses", "Split bills and track group spending") { navController.navigate(Screen.SharedExpenses.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.CalendarMonth, Color(0xFF6A1B9A), "Financial Calendar", "View bills, income and events by date") { navController.navigate(Screen.FinancialCalendar.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.EmojiEvents, Color(0xFFE65100), "Spending Streaks", "Streak tracking and achievements") { navController.navigate(Screen.SpendingStreaks.route) }
            }

            // Categories & Data
            SettingsSection(title = "Categories & Data") {
                SettingsNavItem(Icons.Filled.LocalOffer, Color(0xFF00513F), "Manage Categories", "Add, edit or delete transaction categories") { navController.navigate(Screen.CategoriesManagement.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.PieChart, Color(0xFF00513F), "Budgets", "Set monthly spending limits per category") { navController.navigate(Screen.Budgets.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Repeat, Color(0xFF1565C0), "Recurring Transactions", "Manage subscriptions, bills and regular income") { navController.navigate(Screen.RecurringTransactions.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.AccountBalanceWallet, Color(0xFF6A1B9A), "Wallets", "Manage your bank accounts and cash wallets") { navController.navigate(Screen.WalletsList.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.ShowChart, Color(0xFFE65100), "Connected Accounts", "Revolut, Trading 212 and other brokers") { navController.navigate(Screen.ConnectAccount.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.FileUpload, Color(0xFF00838F), "Import from Money Manager", "Import accounts, categories and transactions from a .mmbackup file") { navController.navigate(Screen.TransactionImport.route) }
            }

            // Preferences
            SettingsSection(title = "Preferences") {
                SettingsNavItem(Icons.Filled.Palette, Color(0xFF6A1B9A), "Appearance", "Colors, density, theme and number format") { navController.navigate(Screen.AppearanceSettings.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Notifications, Color(0xFF00513F), "Notifications", "Budget alerts, reminders and summaries") { navController.navigate(Screen.NotificationSettings.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Lock, Color(0xFF1565C0), "Security", "Biometric, PIN and auto-lock settings") { navController.navigate(Screen.SecuritySettings.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.AttachMoney, Color(0xFF00513F), "Currency", "Currently: $currencyCode") {}
            }

            // Reports & Insights
            SettingsSection(title = "Reports & Insights") {
                SettingsNavItem(Icons.Filled.BarChart, Color(0xFF00513F), "Custom Reports", "Generate and export spending reports") { navController.navigate(Screen.CustomReport.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Insights, Color(0xFF1565C0), "Budget Insights", "View spending patterns and budget alerts") { navController.navigate(Screen.BudgetInsights.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Widgets, Color(0xFF6A1B9A), "Widgets", "Configure home screen widgets") { navController.navigate(Screen.WidgetSettings.route) }
            }

            // Help & Support
            SettingsSection(title = "Help & Support") {
                SettingsNavItem(Icons.Filled.HelpOutline, OnSurfaceVariant, "Help Center", "FAQ, guides and support") { navController.navigate(Screen.HelpSupport.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Feedback, OnSurfaceVariant, "Send Feedback", "Report bugs or suggest improvements") { navController.navigate(Screen.HelpSupport.route) }
                SettingsDivider()
                SettingsNavItem(Icons.Filled.Info, OnSurfaceVariant, "App Version", "Ledger v1.0.0") {}
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp))
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
}

@Composable
private fun SettingsNavItem(icon: ImageVector, iconColor: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
