package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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

// ── Data model for a tappable transaction entry ────────────────────────────
private data class TxDetail(
    val title: String,
    val subtitle: String,        // "Category · Wallet"
    val amount: String,
    val isIncome: Boolean,
    val date: String,
    val time: String,
    val wallet: String,
    val category: String,
    val categoryIcon: ImageVector,
    val categoryColor: Color,
    val note: String = ""
)

private val topExpenses = listOf(
    TxDetail("Rent", "Housing · Checking", "-\$1,200.00", false, "Apr 1, 2026", "9:05 AM", "Checking Account", "Housing", Icons.Filled.Home, Color(0xFF00513F), "Monthly rent payment"),
    TxDetail("Groceries", "Food · Checking", "-\$340.00", false, "Apr 3, 2026", "5:22 PM", "Checking Account", "Food & Dining", Icons.Filled.Restaurant, Color(0xFF1565C0)),
    TxDetail("Transport", "Travel · Cash", "-\$180.00", false, "Apr 5, 2026", "8:10 AM", "Cash", "Transportation", Icons.Filled.DirectionsCar, Color(0xFF6A1B9A), "Bus pass + taxi"),
    TxDetail("Entertainment", "Leisure · Checking", "-\$95.00", false, "Apr 8, 2026", "7:30 PM", "Checking Account", "Entertainment", Icons.Filled.Movie, Color(0xFFE65100)),
    TxDetail("Health", "Medical · Savings", "-\$25.49", false, "Apr 12, 2026", "2:15 PM", "Savings Account", "Health", Icons.Filled.HealthAndSafety, Color(0xFF920009), "Pharmacy"),
)

private val incomeSources = listOf(
    TxDetail("Salary", "Work · Checking", "+\$5,200.00", true, "Apr 1, 2026", "9:00 AM", "Checking Account", "Salary", Icons.Filled.Work, Color(0xFF00513F), "April paycheck"),
    TxDetail("Freelance", "Work · Checking", "+\$800.00", true, "Mar 30, 2026", "2:14 PM", "Checking Account", "Freelance", Icons.Filled.Laptop, Color(0xFF1565C0), "Web project invoice"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyStatisticsScreen(navController: NavController) {
    val years = listOf("2024", "2025", "2026")
    var selectedYear by remember { mutableStateOf("2026") }
    var showYearMenu by remember { mutableStateOf(false) }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    var selectedMonth by remember { mutableStateOf("Apr") }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Summary", "Trends", "Patterns", "Savings", "Comparison", "Reports")

    // Transaction detail sheet state
    var sheetTx by remember { mutableStateOf<TxDetail?>(null) }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            TxDetailSheet(tx) { sheetTx = null }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    Box {
                        TextButton(onClick = { showYearMenu = true }) {
                            Text(selectedYear, style = MaterialTheme.typography.labelLarge, color = Primary)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Primary)
                        }
                        DropdownMenu(
                            expanded = showYearMenu,
                            onDismissRequest = { showYearMenu = false }
                        ) {
                            years.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(year) },
                                    onClick = { selectedYear = year; showYearMenu = false },
                                    trailingIcon = if (year == selectedYear) ({ Icon(Icons.Filled.Check, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp)) }) else null
                                )
                            }
                        }
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
            // Month tabs
            ScrollableTabRow(
                selectedTabIndex = months.indexOf(selectedMonth),
                containerColor = SurfaceContainerLow,
                edgePadding = 16.dp
            ) {
                months.forEach { month ->
                    Tab(
                        selected = selectedMonth == month,
                        onClick = { selectedMonth = month },
                        text = { Text(month, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section selector chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { i, title ->
                        FilterChip(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor = OnPrimary
                            )
                        )
                    }
                }

                when (selectedTab) {
                    0 -> StatsSummaryTab(onTxClick = { sheetTx = it })
                    1 -> StatsTrendsTab()
                    2 -> StatsPatternsTab()
                    3 -> StatsSavingsTab(navController)
                    4 -> StatsComparisonTab()
                    5 -> StatsReportsTab(navController)
                }
            }
        }
    }
}

@Composable
private fun TxDetailSheet(tx: TxDetail, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
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

        // Details grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TxDetailRow(Icons.Filled.CalendarToday, "Date", tx.date)
            TxDetailRow(Icons.Filled.AccessTime, "Time", tx.time)
            TxDetailRow(Icons.Filled.Category, "Category", tx.category)
            TxDetailRow(Icons.Filled.AccountBalanceWallet, "Wallet", tx.wallet)
            if (tx.note.isNotBlank()) {
                TxDetailRow(Icons.Filled.Notes, "Note", tx.note)
            }
            TxDetailRow(
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
private fun TxDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatsSummaryTab(onTxClick: (TxDetail) -> Unit) {
    LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("+\$6,000.00", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("-\$1,840.49", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Net Savings", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                Text("+\$4,159.51", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Savings Rate", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                Text("69.3%", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Top Expenses", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            topExpenses.forEachIndexed { idx, tx ->
                TransactionRow(
                    title = tx.title,
                    subtitle = tx.subtitle,
                    amount = tx.amount,
                    isIncome = tx.isIncome,
                    onClick = { onTxClick(tx) }
                )
                if (idx < topExpenses.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Income Sources", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            incomeSources.forEachIndexed { idx, tx ->
                TransactionRow(
                    title = tx.title,
                    subtitle = tx.subtitle,
                    amount = tx.amount,
                    isIncome = tx.isIncome,
                    onClick = { onTxClick(tx) }
                )
                if (idx < incomeSources.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun StatsComparisonTab() {
    Text("Month-over-Month", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ComparisonRow("Income", "+\$6,000", "+\$5,500", positive = true)
            ComparisonRow("Expenses", "-\$1,840", "-\$2,100", positive = true)
            ComparisonRow("Savings", "+\$4,160", "+\$3,400", positive = true)
            ComparisonRow("Transactions", "24", "31", positive = false)
        }
    }

    Text("Category Comparison", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CategoryCompareRow("Housing", 1200f, 1200f)
            CategoryCompareRow("Food", 340f, 420f)
            CategoryCompareRow("Transport", 180f, 210f)
            CategoryCompareRow("Entertainment", 95f, 180f)
        }
    }

    Text("Year-to-Date", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            YtdStatRow("Total Income", "\$22,400.00")
            YtdStatRow("Total Expenses", "\$7,940.00")
            YtdStatRow("Net Savings", "\$14,460.00")
            YtdStatRow("Avg Monthly Spend", "\$1,985.00")
        }
    }
}

@Composable
private fun StatsReportsTab(navController: NavController) {
    Text("Generated Reports", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

    val reports = listOf(
        Triple("April 2026 Summary", "Full monthly breakdown", Icons.Filled.Description),
        Triple("Q1 2026 Report", "Quarterly analysis", Icons.Filled.Assessment),
        Triple("Tax Report 2025", "Annual income & expenses", Icons.Filled.AccountBalance),
        Triple("Net Worth Report", "Assets & liabilities", Icons.Filled.TrendingUp),
    )

    reports.forEach { (title, subtitle, icon) ->
        LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { }) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
                Icon(Icons.Filled.Download, contentDescription = "Download", tint = Primary)
            }
        }
    }

    OutlinedButton(
        onClick = { navController.navigate(Screen.CustomReport.route) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Primary)
        Spacer(Modifier.width(8.dp))
        Text("Generate Custom Report", color = Primary)
    }
}

// ── Private sub-components ────────────────────────────────────────────────────

@Composable
private fun ComparisonRow(label: String, current: String, previous: String, positive: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(current, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text("vs $previous", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (positive) Icons.Filled.TrendingDown else Icons.Filled.TrendingUp,
            contentDescription = null,
            tint = if (positive) Primary else Tertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CategoryCompareRow(name: String, current: Float, previous: Float) {
    val diff = current - previous
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\$${"%.0f".format(current)}", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                Text(
                    "${if (diff <= 0) "" else "+"}\$${"%.0f".format(diff)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (diff <= 0) Primary else Tertiary
                )
            }
        }
        LinearProgressIndicator(
            progress = { (current / 1500f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Primary, trackColor = SurfaceContainerHighest
        )
    }
}

@Composable
private fun YtdStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}

// ── Trends tab: category spending over 6 months ───────────────────────────────
private val trendMonths = listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr")
private val trendData = listOf(
    Triple("Housing",       listOf(1200f, 1200f, 1200f, 1200f, 1200f, 1200f), Color(0xFF00513F)),
    Triple("Food & Dining", listOf(310f,  390f,  280f,  420f,  340f,  340f),  Color(0xFF1565C0)),
    Triple("Transport",     listOf(160f,  200f,  180f,  210f,  180f,  180f),  Color(0xFF6A1B9A)),
    Triple("Entertainment", listOf(120f,  200f,  90f,   180f,  95f,   95f),   Color(0xFFE65100)),
    Triple("Health",        listOf(0f,    40f,   22f,   50f,   22f,   22f),   Color(0xFF920009)),
)

@Composable
private fun StatsTrendsTab() {
    Text("6-Month Spending Trends", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            trendData.forEach { (name, values, color) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(name, style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.Medium)
                        }
                        val diff = values.last() - values[values.size - 2]
                        Text(
                            "${if (diff >= 0) "+" else ""}\$${"%.0f".format(diff)} vs last month",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diff <= 0f) Primary else Tertiary
                        )
                    }
                    // Mini sparkline
                    TrendSparkline(values, color)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        trendMonths.forEach { m -> Text(m, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant) }
                    }
                }
                if (name != trendData.last().first) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
            }
        }
    }

    Text("Category Totals vs Last Month", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            trendData.forEach { (name, values, color) ->
                val current = values.last(); val previous = values[values.size - 2]
                val diff = current - previous
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text(name, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                    Text("\$${"%.0f".format(current)}", style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(4.dp), color = (if (diff <= 0f) Primary else Tertiary).copy(alpha = 0.10f)) {
                        Text(
                            "${if (diff >= 0) "+" else ""}\$${"%.0f".format(diff)}",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diff <= 0f) Primary else Tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendSparkline(values: List<Float>, color: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
        val w = size.width; val h = size.height
        val min = values.min(); val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (values.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.8f - h * 0.1f
        val path = Path(); path.moveTo(xOf(0), yOf(values[0]))
        values.forEachIndexed { i, v -> if (i > 0) path.lineTo(xOf(i), yOf(v)) }
        drawPath(path, color = color, style = Stroke(width = 1.5.dp.toPx()))
        values.forEachIndexed { i, v ->
            drawCircle(color = color, radius = if (i == values.size - 1) 3.dp.toPx() else 1.5.dp.toPx(), center = Offset(xOf(i), yOf(v)))
        }
    }
}

// ── Patterns tab: day-of-week and time-of-day ────────────────────────────────

private val dayOfWeekSpend = listOf(
    Pair("Mon", 85f), Pair("Tue", 120f), Pair("Wed", 145f), Pair("Thu", 95f),
    Pair("Fri", 280f), Pair("Sat", 190f), Pair("Sun", 60f)
)
private val hourlySpend = listOf(
    Pair("6am", 10f), Pair("9am", 45f), Pair("12pm", 180f), Pair("3pm", 60f),
    Pair("6pm", 240f), Pair("9pm", 90f), Pair("12am", 15f)
)

@Composable
private fun StatsPatternsTab() {
    Text("Spending by Day of Week", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val maxDay = dayOfWeekSpend.maxOf { it.second }
            dayOfWeekSpend.forEach { (day, amount) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(day, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(28.dp))
                    LinearProgressIndicator(
                        progress = { amount / maxDay },
                        modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = if (amount == maxDay) Tertiary else Primary,
                        trackColor = SurfaceContainerHighest
                    )
                    Text("\$${"%.0f".format(amount)}", style = MaterialTheme.typography.labelSmall, color = OnSurface,
                        fontWeight = if (amount == maxDay) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(36.dp))
                }
            }
            val peakDay = dayOfWeekSpend.maxByOrNull { it.second }!!.first
            Text("You spend most on $peakDay", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }

    Text("Spending by Time of Day", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val maxHour = hourlySpend.maxOf { it.second }
            hourlySpend.forEach { (hour, amount) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(hour, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(36.dp))
                    LinearProgressIndicator(
                        progress = { amount / maxHour },
                        modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = if (amount == maxHour) Color(0xFF1565C0) else Primary.copy(alpha = 0.7f),
                        trackColor = SurfaceContainerHighest
                    )
                    Text("\$${"%.0f".format(amount)}", style = MaterialTheme.typography.labelSmall, color = OnSurface, modifier = Modifier.width(36.dp))
                }
            }
            val peakHour = hourlySpend.maxByOrNull { it.second }!!.first
            Text("Most transactions happen around $peakHour", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ── Savings rate tab ──────────────────────────────────────────────────────────

private val savingsHistory = listOf(
    Pair("Nov", 58.2f), Pair("Dec", 48.5f), Pair("Jan", 61.9f),
    Pair("Feb", 66.5f), Pair("Mar", 65.0f), Pair("Apr", 69.3f)
)

@Composable
private fun StatsSavingsTab(navController: NavController) {
    val current = savingsHistory.last().second
    val avg = savingsHistory.map { it.second }.average().toFloat()
    val best = savingsHistory.maxByOrNull { it.second }!!

    LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SAVINGS RATE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("${"%.1f".format(current)}%", style = MaterialTheme.typography.displaySmall, color = Primary, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("6M Average", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("${"%.1f".format(avg)}%", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Best Month", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("${best.first}: ${"%.1f".format(best.second)}%", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    Text("Monthly Savings Rate", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            savingsHistory.forEach { (month, rate) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(month, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(28.dp))
                    LinearProgressIndicator(
                        progress = { rate / 100f },
                        modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = when { rate >= 65f -> Primary; rate >= 50f -> Color(0xFF1565C0); else -> Tertiary },
                        trackColor = SurfaceContainerHighest
                    )
                    Text("${"%.1f".format(rate)}%", style = MaterialTheme.typography.labelSmall, color = OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(38.dp))
                }
            }
        }
    }

    Text("Benchmarks", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("Your average", avg, Primary),
                Triple("50/30/20 rule target", 20f, Color(0xFF1565C0)),
                Triple("FIRE target", 50f, Color(0xFFE65100)),
                Triple("Expert recommended", 15f, OnSurfaceVariant),
            ).forEach { (label, value, color) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                    LinearProgressIndicator(
                        progress = { (value / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = color, trackColor = SurfaceContainerHighest
                    )
                    Text("${"%.0f".format(value)}%", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                }
            }
        }
    }

    OutlinedButton(onClick = { navController.navigate(Screen.AnnualSummary.route) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
        Icon(Icons.Filled.CalendarMonth, null, tint = Primary)
        Spacer(Modifier.width(8.dp))
        Text("View Annual Summary", color = Primary)
    }
}
