package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetInsightsScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Categories", "Fixed Costs", "Trends")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget & Insights", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.CategoriesManagement.route) }) {
                        Icon(Icons.Filled.Category, contentDescription = "Categories")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceContainerLow,
                contentColor = Primary
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (selectedTab) {
                    0 -> BudgetOverviewTab(navController)
                    1 -> CategoryBreakdownTab()
                    2 -> FixedCostsTab()
                    3 -> SpendingTrendsTab()
                }
            }
        }
    }
}

@Composable
private fun BudgetOverviewTab(navController: NavController) {
    // Monthly spending card
    LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("APRIL SPENDING", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("$1,840.49", style = MaterialTheme.typography.displaySmall, color = OnSurface, fontWeight = FontWeight.Bold)
            Text("of $2,500.00 budget", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            LinearProgressIndicator(
                progress = { 0.74f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Primary, trackColor = SurfaceContainerHighest
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("74% used", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("$659.51 remaining", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Income vs Expense summary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LedgerCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("+$6,000", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                Text("this month", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
        LedgerCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("-$1,840", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                Text("this month", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
    }

    // Recent Activity
    Text("Recent Activity", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            TransactionRow("Salary", "Apr 1 · Work", "+$5,200.00", isIncome = true)
            TransactionRow("Rent", "Apr 1 · Housing", "-$1,200.00", isIncome = false)
            TransactionRow("Groceries", "Apr 2 · Food", "-$124.50", isIncome = false)
            TransactionRow("Netflix", "Apr 3 · Entertainment", "-$15.99", isIncome = false)
        }
    }

    // Navigate to full statistics
    LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.MonthlyStatistics.route) }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BarChart, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Monthly Statistics & Reports", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Text("Detailed breakdown, comparisons", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryBreakdownTab() {
    Text("Category Breakdown", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BudgetCategoryRow("Housing", 1200f, 1200f, Icons.Filled.Home)
            BudgetCategoryRow("Food & Dining", 340f, 400f, Icons.Filled.Restaurant)
            BudgetCategoryRow("Transportation", 180f, 200f, Icons.Filled.DirectionsCar)
            BudgetCategoryRow("Entertainment", 95f, 150f, Icons.Filled.Movie)
            BudgetCategoryRow("Health", 25f, 100f, Icons.Filled.HealthAndSafety)
            BudgetCategoryRow("Shopping", 0f, 200f, Icons.Filled.ShoppingBag)
        }
    }

    // Top spending categories
    Text("Top Categories This Month", style = MaterialTheme.typography.titleMedium, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryShareRow("Housing", 65.2f)
            CategoryShareRow("Food & Dining", 18.5f)
            CategoryShareRow("Transportation", 9.8f)
            CategoryShareRow("Entertainment", 5.2f)
            CategoryShareRow("Other", 1.3f)
        }
    }
}

@Composable
private fun FixedCostsTab() {
    Text("Fixed Monthly Costs", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

    LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TOTAL FIXED", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("$1,415.99", style = MaterialTheme.typography.displaySmall, color = OnSurface, fontWeight = FontWeight.Bold)
            Text("per month", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
    }

    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FixedCostRow("Rent", "$1,200.00", "1st of month")
            FixedCostRow("Netflix", "$15.99", "3rd of month")
            FixedCostRow("Spotify", "$9.99", "5th of month")
            FixedCostRow("Internet", "$59.99", "10th of month")
            FixedCostRow("Gym", "$29.99", "15th of month")
            FixedCostRow("iCloud", "$2.99", "20th of month")
        }
    }

    OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Primary)
        Spacer(Modifier.width(8.dp))
        Text("Add Fixed Cost", color = Primary)
    }
}

@Composable
private fun SpendingTrendsTab() {
    Text("Spending Trends", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

    // Month comparison bars
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Monthly Comparison", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            MonthBar("January", 2100f, 3000f)
            MonthBar("February", 1950f, 3000f)
            MonthBar("March", 2300f, 3000f)
            MonthBar("April", 1840f, 3000f)
        }
    }

    // Insights
    Text("Insights", style = MaterialTheme.typography.titleMedium, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InsightRow(Icons.Filled.TrendingDown, "Spending is down 20% vs last month", positive = true)
            InsightRow(Icons.Filled.Restaurant, "Food spending increased by $80", positive = false)
            InsightRow(Icons.Filled.Savings, "You saved 69% of your income this month", positive = true)
            InsightRow(Icons.Filled.Warning, "Housing is 65% of total expenses", positive = false)
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun BudgetCategoryRow(name: String, spent: Float, budget: Float, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val progress = (spent / budget).coerceIn(0f, 1f)
    val isOver = spent >= budget
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isOver) Tertiary else Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
            Text(
                "$${"%.0f".format(spent)} / $${"%.0f".format(budget)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isOver) Tertiary else OnSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (isOver) Tertiary else Primary,
            trackColor = SurfaceContainerHighest
        )
    }
}

@Composable
private fun CategoryShareRow(name: String, pct: Float) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Primary))
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Text("${"%.1f".format(pct)}%", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

@Composable
private fun FixedCostRow(name: String, amount: String, due: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
            Text("Due: $due", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
        Text(amount, style = MaterialTheme.typography.bodyMedium, color = Tertiary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MonthBar(month: String, spent: Float, budget: Float) {
    val progress = (spent / budget).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(month, style = MaterialTheme.typography.bodySmall, color = OnSurface)
            Text("$${"%.0f".format(spent)}", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Primary, trackColor = SurfaceContainerHighest
        )
    }
}

@Composable
private fun InsightRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, positive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (positive) Primary else Tertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
    }
}
