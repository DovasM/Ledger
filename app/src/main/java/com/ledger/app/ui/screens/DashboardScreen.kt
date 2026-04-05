package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

private data class DashTxDetail(
    val title: String, val subtitle: String, val amount: String, val isIncome: Boolean,
    val date: String, val wallet: String, val category: String,
    val categoryIcon: ImageVector, val categoryColor: Color
)

private val dashboardTxs = listOf(
    DashTxDetail("Salary", "Apr 1 · Work", "+\$5,200.00", true, "Apr 1, 2026", "Checking Account", "Work", Icons.Filled.Work, Color(0xFF00513F)),
    DashTxDetail("Rent", "Apr 1 · Housing", "-\$1,200.00", false, "Apr 1, 2026", "Checking Account", "Housing", Icons.Filled.Home, Color(0xFF00513F)),
    DashTxDetail("Groceries", "Mar 31 · Food", "-\$124.50", false, "Mar 31, 2026", "Checking Account", "Food & Dining", Icons.Filled.Restaurant, Color(0xFF1565C0)),
    DashTxDetail("Freelance", "Mar 30 · Work", "+\$800.00", true, "Mar 30, 2026", "Checking Account", "Freelance", Icons.Filled.Laptop, Color(0xFF1565C0)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    var showAddSheet by remember { mutableStateOf(false) }
    var sheetTx by remember { mutableStateOf<DashTxDetail?>(null) }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            DashTxDetailSheet(tx) { sheetTx = null }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add New",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                AddActionCard(
                    icon = Icons.Filled.SwapHoriz,
                    title = "Add Transaction",
                    description = "Record income, expense, or transfer between wallets",
                    color = Primary
                ) { showAddSheet = false; navController.navigate(Screen.AddTransaction.route) }
                AddActionCard(
                    icon = Icons.Filled.EmojiEvents,
                    title = "New Savings Goal",
                    description = "Set a target amount with a deadline and track your progress",
                    color = Color(0xFF1565C0)
                ) { showAddSheet = false; navController.navigate(Screen.AddGoal.route) }
                AddActionCard(
                    icon = Icons.Filled.AccountBalanceWallet,
                    title = "Add Wallet",
                    description = "Connect a bank account, cash wallet, or investment account",
                    color = Color(0xFF6A1B9A)
                ) { showAddSheet = false; navController.navigate(Screen.AddWallet.route) }
                AddActionCard(
                    icon = Icons.Filled.LocalOffer,
                    title = "New Category",
                    description = "Create a custom expense or income category with an icon",
                    color = Color(0xFFE65100)
                ) { showAddSheet = false; navController.navigate(Screen.AddCategory.route) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Scaffold(
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = Primary,
                contentColor = OnPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GOOD MORNING", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("Alex", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.navigate(Screen.GlobalSearch.route) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = OnSurface)
                    }
                    BadgedBox(badge = {
                        Badge(containerColor = Tertiary) { Text("3") }
                    }) {
                        IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = OnSurface)
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = OnSurface)
                    }
                }
            }

            // Total Balance Card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("TOTAL BALANCE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$24,530.80",
                        style = MaterialTheme.typography.headlineLarge,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("THIS MONTH INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+$5,200", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                            Text("+9.1% vs last month", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("THIS MONTH EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("-$1,840", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.SemiBold)
                            Text("-12.4% vs last month", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                    }
                }
            }

            // ── 1. Portfolio ──────────────────────────────────────────────────
            Text("Portfolio", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.InvestmentPortfolio.route) }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            Text("PORTFOLIO VALUE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("$3,560.00", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                            Text("+$124.50 (3.6%) today", style = MaterialTheme.typography.bodySmall, color = Primary)
                        }
                        Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                    MiniPortfolioChart()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PortfolioMiniStat("BTC", "+5.4%", Primary)
                        PortfolioMiniStat("AAPL", "+2.1%", Primary)
                        PortfolioMiniStat("ETH", "+1.8%", Primary)
                        PortfolioMiniStat("GOOGL", "-0.4%", Tertiary)
                    }
                }
            }

            // ── 2. Savings Goals ─────────────────────────────────────────────
            Text("Savings Goals", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.SavingsGoals.route) }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GoalProgressRow("Emergency Fund", 6500f, 10000f, Color(0xFF00513F))
                    GoalProgressRow("Vacation", 1200f, 3000f, Color(0xFF1565C0))
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    TextButton(
                        onClick = { navController.navigate(Screen.SavingsGoals.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View All Goals", color = Primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── 3. Recent Transactions ────────────────────────────────────────
            Text("Recent Transactions", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    dashboardTxs.forEach { tx ->
                        TransactionRow(tx.title, tx.subtitle, tx.amount, tx.isIncome, onClick = { sheetTx = tx })
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = OutlineVariant.copy(alpha = 0.2f))
                    TextButton(
                        onClick = { navController.navigate(Screen.Activity.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Timeline, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("See Full Activity", color = Primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── 4. Cash Flow Forecast ─────────────────────────────────────────
            Text("Cash Flow", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.CashFlowForecast.route) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.WaterDrop, null, tint = Primary, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("End of May forecast", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("Based on 7 recurring transactions", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("\$23,529", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                        Text("-\$1,001", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            // ── 5. Budgets ────────────────────────────────────────────────────
            Text("Budgets", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Screen.Budgets.route) }) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("Housing",       1200f / 1300f, Color(0xFF00513F)),
                        Triple("Food & Dining", 340f  / 400f,  Color(0xFF1565C0)),
                        Triple("Shopping",      310f  / 250f,  Color(0xFF00838F)),
                    ).forEach { (name, pct, color) ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(name, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                            LinearProgressIndicator(
                                progress = { pct.coerceIn(0f, 1f) },
                                modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (pct > 1f) Tertiary else color, trackColor = SurfaceContainerHighest
                            )
                            Text(
                                "${"%.0f".format(pct * 100)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pct > 1f) Tertiary else color,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(32.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    TextButton(onClick = { navController.navigate(Screen.Budgets.route) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PieChart, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View All Budgets", color = Primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── 6. Top Categories ─────────────────────────────────────────────
            Text("Top Categories", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TopCategoryRow("Housing", Icons.Filled.Home, 1200f, 1840f, Color(0xFF00513F)) {
                        navController.navigate(Screen.CategoryTransactions.createRoute("Housing"))
                    }
                    TopCategoryRow("Food & Dining", Icons.Filled.Restaurant, 340f, 1840f, Color(0xFF1565C0)) {
                        navController.navigate(Screen.CategoryTransactions.createRoute("Food & Dining"))
                    }
                    TopCategoryRow("Transport", Icons.Filled.DirectionsCar, 180f, 1840f, Color(0xFF6A1B9A)) {
                        navController.navigate(Screen.CategoryTransactions.createRoute("Transport"))
                    }
                    TopCategoryRow("Entertainment", Icons.Filled.Movie, 120f, 1840f, Color(0xFFE65100)) {
                        navController.navigate(Screen.CategoryTransactions.createRoute("Entertainment"))
                    }
                }
            }
        }
    }
}

// ── Composable helpers ───────────────────────────────────────────────────────

@Composable
private fun AddActionCard(icon: ImageVector, title: String, description: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TopCategoryRow(name: String, icon: ImageVector, spent: Float, totalSpent: Float, color: Color, onClick: () -> Unit) {
    val share = spent / totalSpent
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
            Text(
                "-\$${"%.0f".format(spent)}",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.10f)
            ) {
                Text(
                    "${"%.0f".format(share * 100)}%",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = SurfaceContainerHighest
        )
    }
}

@Composable
private fun GoalProgressRow(name: String, current: Float, target: Float, color: Color) {
    val progress = current / target
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "\$${"%.0f".format(current)} / \$${"%.0f".format(target)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
                Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.10f)) {
                    Text(
                        "${"%.0f".format(progress * 100)}%",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = SurfaceContainerHighest
        )
    }
}

@Composable
private fun MiniPortfolioChart() {
    val points = listOf(3200f, 3350f, 3280f, 3400f, 3480f, 3520f, 3560f)
    Canvas(modifier = Modifier.fillMaxWidth().height(52.dp)) {
        val w = size.width; val h = size.height
        val min = points.min(); val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.8f - h * 0.1f
        val path = Path()
        path.moveTo(xOf(0), yOf(points[0]))
        points.forEachIndexed { i, v -> if (i > 0) path.lineTo(xOf(i), yOf(v)) }
        val fill = Path().apply {
            addPath(path); lineTo(xOf(points.size - 1), h); lineTo(xOf(0), h); close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(Color(0xFF00513F).copy(alpha = 0.15f), Color.Transparent)))
        drawPath(path, color = Color(0xFF00513F), style = Stroke(width = 1.5.dp.toPx()))
        // Dots at each data point
        points.forEachIndexed { i, v ->
            drawCircle(color = Color(0xFF00513F), radius = if (i == points.size - 1) 3.5.dp.toPx() else 2.dp.toPx(), center = Offset(xOf(i), yOf(v)))
        }
    }
}

@Composable
private fun PortfolioMiniStat(ticker: String, change: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ticker, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Text(change, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashTxDetailSheet(tx: DashTxDetail, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(
                    tx.amount,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (tx.isIncome) Primary else Tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(tx.categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tx.categoryIcon, contentDescription = null, tint = tx.categoryColor, modifier = Modifier.size(26.dp))
            }
        }

        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DashDetailRow(Icons.Filled.CalendarToday, "Date", tx.date)
            DashDetailRow(Icons.Filled.Category, "Category", tx.category)
            DashDetailRow(Icons.Filled.AccountBalanceWallet, "Wallet", tx.wallet)
            DashDetailRow(
                if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type",
                if (tx.isIncome) "Income" else "Expense"
            )
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", color = Primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DashDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
