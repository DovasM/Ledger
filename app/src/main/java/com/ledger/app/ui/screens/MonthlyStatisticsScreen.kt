package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.RecurringViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import uniffi.ledger.Category
import uniffi.ledger.Transaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyStatisticsScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    recurringViewModel: RecurringViewModel = hiltViewModel()
) {
    val txState        by txViewModel.state.collectAsStateWithLifecycle()
    val catState       by categoryViewModel.state.collectAsStateWithLifecycle()
    val recurringState by recurringViewModel.state.collectAsStateWithLifecycle()

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) {
        txViewModel.loadAll()
        categoryViewModel.load()
        recurringViewModel.load()
    }

    val today = LocalDate.now()

    val availableYears = remember(txState.transactions) {
        txState.transactions
            .mapNotNull { runCatching { LocalDate.parse(it.createdAt.take(10)).year }.getOrNull() }
            .distinct().sorted().ifEmpty { listOf(today.year) }
    }

    var selectedYear     by rememberSaveable { mutableStateOf(today.year) }
    var showYearMenu     by remember        { mutableStateOf(false) }
    var selectedMonthIdx by rememberSaveable { mutableStateOf(today.monthValue - 1) }
    var selectedTab      by rememberSaveable { mutableStateOf(0) }
    var sheetTx          by remember        { mutableStateOf<Transaction?>(null) }

    val selectedMonth = selectedMonthIdx + 1
    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val tabs   = listOf("Summary","Trends","Patterns","Savings","Comparison","Reports")

    fun inMonth(s: String, y: Int, m: Int) = runCatching {
        val d = LocalDate.parse(s.take(10)); d.year == y && d.monthValue == m
    }.getOrDefault(false)

    val selTxs   = txState.transactions.filter { inMonth(it.createdAt, selectedYear, selectedMonth) }
    val prevDate = LocalDate.of(selectedYear, selectedMonth, 1).minusMonths(1)
    val prevTxs  = txState.transactions.filter { inMonth(it.createdAt, prevDate.year, prevDate.monthValue) }

    val sixMonths: List<Triple<LocalDate, List<Transaction>, String>> = (5 downTo 0).map { off ->
        val d = LocalDate.of(selectedYear, selectedMonth, 1).minusMonths(off.toLong())
        Triple(d, txState.transactions.filter { inMonth(it.createdAt, d.year, d.monthValue) },
            d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
    }

    val allYearMonths: List<Triple<LocalDate, List<Transaction>, String>> = (1..12).map { m ->
        val d = LocalDate.of(selectedYear, m, 1)
        Triple(d, txState.transactions.filter { inMonth(it.createdAt, selectedYear, m) },
            d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
    }

    val ytdTxs = txState.transactions.filter {
        runCatching {
            val d = LocalDate.parse(it.createdAt.take(10))
            d.year == selectedYear && d.monthValue <= selectedMonth
        }.getOrDefault(false)
    }

    // Months in the selected year that have any transactions (for empty state nudge)
    val monthsWithData = (1..12).filter { m ->
        txState.transactions.any { inMonth(it.createdAt, selectedYear, m) }
    }

    // ── Transaction detail sheet ──────────────────────────────────────────────
    if (sheetTx != null) {
        val tx = sheetTx!!
        val accentColor = if (tx.isIncome) Primary else Tertiary
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text(
                            "${if (tx.isIncome) "+" else "-"}${"$%,.2f".format(tx.amount)}",
                            style = MaterialTheme.typography.titleLarge, color = accentColor, fontWeight = FontWeight.Bold
                        )
                    }
                    val cat      = catState.categories.find { it.name.equals(tx.category, ignoreCase = true) }
                    val catColor = cat?.let { runCatching { colorHexToColor(it.colorHex) }.getOrNull() } ?: accentColor
                    val catIcon  = cat?.let { iconNameToVector(it.iconName) } ?: Icons.Filled.Category
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(catColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(catIcon, null, tint = catColor, modifier = Modifier.size(26.dp)) }
                }
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatDetailRow(Icons.Filled.CalendarToday, "Date",     tx.createdAt.take(10))
                    StatDetailRow(Icons.Filled.Category,      "Category", tx.category.ifBlank { "—" })
                    StatDetailRow(if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                        "Type", if (tx.isIncome) "Income" else "Expense")
                    if (!tx.note.isNullOrBlank()) StatDetailRow(Icons.Filled.Notes, "Note", tx.note!!)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { sheetTx = null }, modifier = Modifier.weight(1f)) { Text("Close") }
                    Button(
                        onClick = { sheetTx = null; navController.navigate(Screen.EditTransaction.createRoute(tx.id)) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text("Edit") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    Box {
                        TextButton(onClick = { showYearMenu = true }) {
                            Text("$selectedYear", style = MaterialTheme.typography.labelLarge, color = Primary)
                            Icon(Icons.Filled.ArrowDropDown, null, tint = Primary)
                        }
                        DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                            availableYears.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text("$year") },
                                    onClick = { selectedYear = year; showYearMenu = false },
                                    trailingIcon = if (year == selectedYear) ({
                                        Icon(Icons.Filled.Check, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }) else null
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedMonthIdx,
                containerColor = SurfaceContainerLow,
                edgePadding = 16.dp
            ) {
                months.forEachIndexed { i, month ->
                    Tab(
                        selected = selectedMonthIdx == i,
                        onClick  = { selectedMonthIdx = i },
                        text     = { Text(month, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, title ->
                        FilterChip(
                            selected = selectedTab == i,
                            onClick  = { selectedTab = i },
                            label    = { Text(title, style = MaterialTheme.typography.labelMedium) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor     = OnPrimary
                            )
                        )
                    }
                }

                when (selectedTab) {
                    0 -> StatsSummaryTab(selTxs, catState.categories, selectedMonth, monthsWithData,
                            onTxClick = { sheetTx = it },
                            onNavigateToMonth = { selectedMonthIdx = it - 1 })
                    1 -> StatsTrendsTab(sixMonths, catState.categories)
                    2 -> StatsPatternsTab(selTxs, recurringState.recurring.map { it.title.trim().lowercase() }.toSet())
                    3 -> StatsSavingsTab(sixMonths, allYearMonths, navController)
                    4 -> StatsComparisonTab(selTxs, prevTxs, ytdTxs, allYearMonths, selectedMonth)
                    5 -> StatsReportsTab(navController)
                }
            }
        }
    }
}

@Composable
private fun StatDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}

// ── Summary tab ───────────────────────────────────────────────────────────────

@Composable
private fun StatsSummaryTab(
    txs: List<Transaction>,
    categories: List<Category>,
    selectedMonth: Int,
    monthsWithData: List<Int>,
    onTxClick: (Transaction) -> Unit,
    onNavigateToMonth: (Int) -> Unit
) {
    val income    = txs.filter {  it.isIncome }.sumOf { it.amount }
    val expenses  = txs.filter { !it.isIncome }.sumOf { it.amount }
    val net       = income - expenses
    val rate      = if (income > 0) (net / income * 100) else 0.0
    val txCount   = txs.size
    val expTxs    = txs.filter { !it.isIncome }
    val avgTxSize = if (expTxs.isNotEmpty()) expTxs.sumOf { it.amount } / expTxs.size else 0.0

    // ── Month summary header ──────────────────────────────────────────────────
    if (txs.isNotEmpty()) {
        LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("+${"$%,.0f".format(income)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("-${"$%,.0f".format(expenses)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("NET", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${if (net >= 0) "+" else ""}${"$%,.0f".format(net)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (net >= 0) Primary else Tertiary,
                            fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Savings, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text("Savings rate: ${"%.1f".format(rate)}%", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Receipt, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text("$txCount transactions · avg ${"$%.0f".format(avgTxSize)}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }
        }
    } else {
        // ── Empty state nudge ─────────────────────────────────────────────────
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.SearchOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
                Text("No data for this month", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                if (monthsWithData.isNotEmpty()) {
                    Text("Months with transactions:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                        monthsWithData.forEach { m ->
                            SuggestionChip(
                                onClick = { onNavigateToMonth(m) },
                                label = { Text(monthNames[m - 1], style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // ── Category filter ───────────────────────────────────────────────────────
    val expenseCategories = expTxs.map { it.category }.distinct().sorted()
    var selectedCat by remember(selectedMonth) { mutableStateOf<String?>(null) }

    if (expenseCategories.size > 1) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedCat == null,
                onClick  = { selectedCat = null },
                label    = { Text("All", style = MaterialTheme.typography.labelSmall) },
                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = SurfaceContainerHighest, selectedLabelColor = OnSurface)
            )
            expenseCategories.forEach { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick  = { selectedCat = if (selectedCat == cat) null else cat },
                    label    = { Text(cat, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.15f), selectedLabelColor = Primary)
                )
            }
        }
    }

    val filteredExpenses = (if (selectedCat != null) expTxs.filter { it.category == selectedCat } else expTxs)
        .sortedByDescending { it.amount }.take(5)
    val filteredIncome = txs.filter { it.isIncome }.sortedByDescending { it.amount }.take(5)

    // ── Top expenses ──────────────────────────────────────────────────────────
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Top Expenses", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (filteredExpenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No expenses${if (selectedCat != null) " in $selectedCat" else " this month"}", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            } else {
                filteredExpenses.forEachIndexed { idx, tx ->
                    TransactionRow(
                        title    = tx.title,
                        subtitle = "${tx.createdAt.take(10)} · ${tx.category}",
                        amount   = "-${"$%,.2f".format(tx.amount)}",
                        isIncome = false,
                        onClick  = { onTxClick(tx) }
                    )
                    if (idx < filteredExpenses.size - 1)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }

    // ── Income sources ────────────────────────────────────────────────────────
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Income Sources", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (filteredIncome.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No income this month", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            } else {
                filteredIncome.forEachIndexed { idx, tx ->
                    TransactionRow(
                        title    = tx.title,
                        subtitle = "${tx.createdAt.take(10)} · ${tx.category}",
                        amount   = "+${"$%,.2f".format(tx.amount)}",
                        isIncome = true,
                        onClick  = { onTxClick(tx) }
                    )
                    if (idx < filteredIncome.size - 1)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }
}

// ── Trends tab ────────────────────────────────────────────────────────────────

@Composable
private fun StatsTrendsTab(
    sixMonths: List<Triple<LocalDate, List<Transaction>, String>>,
    categories: List<Category>
) {
    val monthLabels   = sixMonths.map { it.third }
    val incomeValues  = sixMonths.map { (_, txs, _) -> txs.filter {  it.isIncome }.sumOf { it.amount }.toFloat() }
    val expenseValues = sixMonths.map { (_, txs, _) -> txs.filter { !it.isIncome }.sumOf { it.amount }.toFloat() }

    // ── Income vs Expenses dual-line chart ────────────────────────────────────
    Text("Income vs Expenses", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Primary))
                    Text("Income", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Tertiary))
                    Text("Expenses", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
            IncExpDualChart(incomeValues, expenseValues, monthLabels)
        }
    }

    // ── Per-category spending sparklines ──────────────────────────────────────
    val allExpenseCats = sixMonths.flatMap { (_, txs, _) -> txs.filter { !it.isIncome }.map { it.category } }
        .filter { it.isNotBlank() }.distinct()
    val topCatNames = allExpenseCats.sortedByDescending { catName ->
        val values = sixMonths.map { (_, txs, _) ->
            txs.filter { !it.isIncome && it.category.equals(catName, ignoreCase = true) }.sumOf { it.amount }.toFloat()
        }.filter { it > 0f }
        if (values.size >= 2) values.max() - values.min() else 0f
    }.take(5)
    val fallbackColors = listOf(Color(0xFF5C6BC0), Color(0xFFEF5350), Color(0xFF26A69A), Color(0xFFFFA726), Color(0xFF66BB6A))

    Text("6-Month Spending Trends", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

    if (topCatNames.isEmpty()) {
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No expense data available", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
        }
        return
    }

    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            topCatNames.forEachIndexed { index, catName ->
                val cat   = categories.find { it.name.equals(catName, ignoreCase = true) }
                val color = cat?.let { runCatching { colorHexToColor(it.colorHex) }.getOrNull() }
                    ?: fallbackColors[index % fallbackColors.size]
                val values = sixMonths.map { (_, txs, _) ->
                    txs.filter { !it.isIncome && it.category.equals(catName, ignoreCase = true) }.sumOf { it.amount }.toFloat()
                }
                val diff = values.last() - values[values.size - 2]
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(catName, style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.Medium)
                        }
                        when {
                            values.last() == 0f ->
                                Text("no data this month", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.5f))
                            values[values.size - 2] == 0f ->
                                Text("new this month", style = MaterialTheme.typography.labelSmall, color = Primary)
                            else ->
                                Text("${if (diff >= 0) "+" else ""}${"$%.0f".format(diff)} vs last month",
                                    style = MaterialTheme.typography.labelSmall, color = if (diff <= 0f) Primary else Tertiary)
                        }
                    }
                    TrendSparkline(values, color)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        monthLabels.forEach { m -> Text(m, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant) }
                    }
                }
                if (index < topCatNames.size - 1) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun IncExpDualChart(income: List<Float>, expenses: List<Float>, labels: List<String>) {
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    var canvasWidth by remember { mutableStateOf(0f) }
    val n = income.size

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth().height(72.dp)
                .onSizeChanged { canvasWidth = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val x = event.changes.firstOrNull()?.position?.x ?: continue
                            when (event.type) {
                                PointerEventType.Press, PointerEventType.Move -> {
                                    if (canvasWidth > 0f)
                                        selectedIdx = ((x / canvasWidth) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                    event.changes.forEach { it.consume() }
                                }
                                PointerEventType.Release -> selectedIdx = null
                            }
                        }
                    }
                }
        ) {
            val w = size.width; val h = size.height
            val maxVal = (income + expenses).maxOrNull()?.takeIf { it > 0f } ?: 1f
            fun xOf(i: Int) = w * i / (n - 1)
            fun yOf(v: Float) = h - (h * v / maxVal) * 0.85f - h * 0.05f

            // Build paths that restart (moveTo) after any zero-value gap rather than
            // drawing from the 0-y position, which would produce a misleading baseline line.
            val incPath = Path(); var incMoved = false
            income.forEachIndexed { i, v ->
                if (v > 0f) { if (!incMoved) { incPath.moveTo(xOf(i), yOf(v)); incMoved = true } else incPath.lineTo(xOf(i), yOf(v)) }
                else incMoved = false
            }
            val expPath = Path(); var expMoved = false
            expenses.forEachIndexed { i, v ->
                if (v > 0f) { if (!expMoved) { expPath.moveTo(xOf(i), yOf(v)); expMoved = true } else expPath.lineTo(xOf(i), yOf(v)) }
                else expMoved = false
            }

            drawPath(incPath, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))
            drawPath(expPath, color = Tertiary, style = Stroke(width = 2.dp.toPx()))

            income.forEachIndexed  { i, v -> if (v > 0f) drawCircle(Color(0xFF00513F), 2.5.dp.toPx(), Offset(xOf(i), yOf(v))) }
            expenses.forEachIndexed { i, v -> if (v > 0f) drawCircle(Tertiary, 2.5.dp.toPx(), Offset(xOf(i), yOf(v))) }

            selectedIdx?.let { idx ->
                val ix = xOf(idx)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(ix, 0f), Offset(ix, h), strokeWidth = 1.dp.toPx())
                if (income[idx]   > 0f) drawCircle(Color(0xFF00513F), 4.dp.toPx(), Offset(ix, yOf(income[idx])))
                if (expenses[idx] > 0f) drawCircle(Tertiary,          4.dp.toPx(), Offset(ix, yOf(expenses[idx])))
            }
        }

        // X-axis labels
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { m -> Text(m, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant) }
        }

        // Tooltip
        selectedIdx?.let { idx ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(labels[idx], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(28.dp))
                if (income[idx] > 0f)
                    Text("+${"$%,.0f".format(income[idx])}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00513F), fontWeight = FontWeight.SemiBold)
                if (expenses[idx] > 0f)
                    Text("-${"$%,.0f".format(expenses[idx])}", style = MaterialTheme.typography.labelSmall, color = Tertiary, fontWeight = FontWeight.SemiBold)
                if (income[idx] > 0f && expenses[idx] > 0f) {
                    val net = income[idx] - expenses[idx]
                    Text("net ${if (net >= 0) "+" else ""}${"$%,.0f".format(net)}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        } ?: Text("Hold & drag to inspect", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.5f))
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
            drawCircle(color, radius = if (i == values.size - 1) 3.dp.toPx() else 1.5.dp.toPx(), center = Offset(xOf(i), yOf(v)))
        }
    }
}

// ── Patterns tab ──────────────────────────────────────────────────────────────

@Composable
private fun StatsPatternsTab(txs: List<Transaction>, recurringTitles: Set<String>) {
    val expenses = txs.filter { !it.isIncome }

    // ── Day of week ───────────────────────────────────────────────────────────
    val dowOrder = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val byDow   = dowOrder.associateWith { dow ->
        expenses.filter { runCatching { LocalDate.parse(it.createdAt.take(10)).dayOfWeek == dow }.getOrDefault(false) }
            .sumOf { it.amount }.toFloat()
    }
    val maxDow  = byDow.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val peakDow = byDow.entries.filter { it.value > 0f }.maxByOrNull { it.value }?.key

    Text("Spending by Day of Week", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (expenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No expense data this month", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            } else {
                dowOrder.forEach { dow ->
                    val amount = byDow[dow] ?: 0f
                    val isPeak = dow == peakDow
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(32.dp))
                        LinearProgressIndicator(progress = { amount / maxDow },
                            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = if (isPeak) Tertiary else Primary, trackColor = SurfaceContainerHighest)
                        Text("${"$%.0f".format(amount)}", style = MaterialTheme.typography.labelSmall, color = OnSurface,
                            fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(44.dp))
                    }
                }
                if (peakDow != null)
                    Text("You spend most on ${peakDow.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // ── Week of month ─────────────────────────────────────────────────────────
    val monthLength = expenses.firstOrNull()
        ?.let { runCatching { LocalDate.parse(it.createdAt.take(10)) }.getOrNull()?.lengthOfMonth() } ?: 30
    val weekRanges    = listOf(1..7, 8..14, 15..21, 22..monthLength)
    val weekDayCounts = listOf(7, 7, 7, (monthLength - 21).coerceAtLeast(1))
    val weekLabels    = listOf("Week 1  (1–7)", "Week 2  (8–14)", "Week 3  (15–21)", "Week 4  (22+)")
    val byWeek = weekRanges.map { range ->
        expenses.filter { runCatching { LocalDate.parse(it.createdAt.take(10)).dayOfMonth in range }.getOrDefault(false) }
            .sumOf { it.amount }.toFloat()
    }
    // Normalize by days per bucket so W4 (9-10 days) isn't unfairly inflated vs W1-W3 (7 days each)
    val byWeekNorm  = byWeek.zip(weekDayCounts).map { (amt, days) -> amt / days }
    val maxWeek     = byWeekNorm.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val peakWeekIdx = byWeekNorm.indexOfFirst { it == byWeekNorm.maxOrNull() }.takeIf { byWeekNorm.getOrElse(it) { 0f } > 0f }

    Text("Spending by Week of Month", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (expenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No expense data this month", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            } else {
                byWeek.forEachIndexed { i, amount ->
                    val isPeak = i == peakWeekIdx
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("W${i + 1}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(24.dp))
                        LinearProgressIndicator(progress = { byWeekNorm[i] / maxWeek },  // normalized height
                            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = if (isPeak) Color(0xFF1565C0) else Primary.copy(alpha = 0.8f), trackColor = SurfaceContainerHighest)
                        Text("${"$%.0f".format(amount)}", style = MaterialTheme.typography.labelSmall, color = OnSurface, modifier = Modifier.width(44.dp))  // actual total
                    }
                }
                if (peakWeekIdx != null)
                    Text("Highest daily spending rate in ${weekLabels[peakWeekIdx]}",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // ── Merchant frequency ────────────────────────────────────────────────────
    val merchantFreq = expenses.groupBy { it.title.trim() }
        .mapValues { it.value.size }
        .entries.sortedByDescending { it.value }.take(5)
    val maxFreq = merchantFreq.firstOrNull()?.value?.toFloat()?.takeIf { it > 0f } ?: 1f

    if (merchantFreq.isNotEmpty()) {
        Text("Most Frequent Payees", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                merchantFreq.forEach { (title, count) ->
                    val totalSpent = expenses.filter { it.title.trim() == title }.sumOf { it.amount }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title, style = MaterialTheme.typography.bodySmall, color = OnSurface,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(progress = { count / maxFreq },
                            modifier = Modifier.width(60.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Primary, trackColor = SurfaceContainerHighest)
                        Text("${count}×", style = MaterialTheme.typography.labelSmall, color = Primary,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
                        Text("${"$%.0f".format(totalSpent)}", style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant, modifier = Modifier.width(44.dp))
                    }
                }
                Text("Count × visits  ·  total spent", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }

    // ── Largest single transaction ────────────────────────────────────────────
    val largest = expenses.maxByOrNull { it.amount }
    if (largest != null) {
        Text("Largest Transaction", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Tertiary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowUpward, null, tint = Tertiary, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(largest.title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("${largest.createdAt.take(10)} · ${largest.category}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
                Text("-${"$%,.2f".format(largest.amount)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ── Recurring vs discretionary split ─────────────────────────────────────
    if (expenses.isNotEmpty()) {
        // Match against actual recurring transactions by title
        val recurringExp     = expenses.filter { it.title.trim().lowercase() in recurringTitles }
        val discretionaryExp = expenses.filter { it.title.trim().lowercase() !in recurringTitles }
        val recurringTotal     = recurringExp.sumOf { it.amount }
        val discretionaryTotal = discretionaryExp.sumOf { it.amount }
        val totalExp           = recurringTotal + discretionaryTotal
        val recurringPct       = if (totalExp > 0) (recurringTotal / totalExp * 100).toFloat() else 0f

        Text("Recurring vs Discretionary", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("Recurring = transactions matching your saved recurring entries.",
            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Stacked bar
                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceContainerHighest)) {
                    Box(modifier = Modifier.fillMaxWidth(recurringPct / 100f).fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xFF1565C0)))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF1565C0)))
                            Text("Recurring", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                        Text("${"$%,.0f".format(recurringTotal)} · ${"%.0f".format(recurringPct)}%",
                            style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(SurfaceContainerHighest))
                            Text("Discretionary", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                        Text("${"$%,.0f".format(discretionaryTotal)} · ${"%.0f".format(100 - recurringPct)}%",
                            style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Savings tab ───────────────────────────────────────────────────────────────

@Composable
private fun StatsSavingsTab(
    sixMonths: List<Triple<LocalDate, List<Transaction>, String>>,
    allYearMonths: List<Triple<LocalDate, List<Transaction>, String>>,
    navController: NavController
) {
    // rate is null when income == 0 (no income data for that month — not "saved 0%")
    val savingsData: List<Triple<String, Float?, Double>> = sixMonths.map { (_, txs, label) ->
        val income   = txs.filter { it.isIncome }.sumOf { it.amount }
        val expenses = txs.filter { !it.isIncome }.sumOf { it.amount }
        val rate     = if (income > 0) ((income - expenses) / income * 100).toFloat().coerceIn(-100f, 100f) else null
        Triple(label, rate, income - expenses)
    }
    // Exclude no-data months (null rate AND net == 0 means zero transactions)
    val avg = savingsData.mapNotNull { it.second }.let { if (it.isEmpty()) 0f else it.average().toFloat() }
    val avgMonthlySavingsAmount = savingsData.filter { it.second != null || it.third != 0.0 }
        .let { valid -> if (valid.isEmpty()) 0.0 else valid.map { it.third }.average() }

    // ── Savings streak ────────────────────────────────────────────────────────
    val allRates = allYearMonths.map { (_, txs, _) ->
        val inc = txs.filter { it.isIncome }.sumOf { it.amount }
        val exp = txs.filter { !it.isIncome }.sumOf { it.amount }
        if (inc > 0) ((inc - exp) / inc * 100).toFloat() else -1f
    }
    // Drop trailing months with no data (future months in the current year)
    val trimmedRates = allRates.dropLastWhile { it < 0f }
    var streak = 0
    for (rate in trimmedRates.reversed()) {
        if (rate >= 15f) streak++ else break
    }
    val streakBest = run {
        var best = 0; var cur = 0
        for (rate in trimmedRates) { if (rate >= 15f) { cur++; if (cur > best) best = cur } else cur = 0 }
        best
    }

    Text("Monthly Savings Rate", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    Text("How much of your income you kept each month — (income − expenses) ÷ income. " +
        "Green ≥ 50%  ·  Blue ≥ 20%  ·  Amber ≥ 0%  ·  Red = spending exceeded income.",
        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            savingsData.forEach { (month, rate, _) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(month, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(28.dp))
                    if (rate != null) {
                        LinearProgressIndicator(progress = { (rate / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = when { rate >= 50f -> Primary; rate >= 20f -> Color(0xFF1565C0); rate >= 0f -> Color(0xFFFFA726); else -> Tertiary },
                            trackColor = SurfaceContainerHighest)
                        Text("${"%.1f".format(rate)}%", style = MaterialTheme.typography.labelSmall, color = OnSurface,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                    } else {
                        Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(SurfaceContainerHighest.copy(alpha = 0.5f)))
                        Text("—", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant,
                            modifier = Modifier.width(40.dp))
                    }
                }
            }
        }
    }

    // ── Streak card ───────────────────────────────────────────────────────────
    if (streakBest > 0) {
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF9A825).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFF9A825), modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Savings Streak", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("Consecutive months above 15% (expert recommended)", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$streak now", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF9A825), fontWeight = FontWeight.Bold)
                    Text("best $streakBest", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }

    // ── Benchmarks ────────────────────────────────────────────────────────────
    Text("Benchmarks", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    Text("How your average stacks up against common financial targets.",
        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            data class Benchmark(val label: String, val hint: String, val value: Float, val color: Color)
            listOf(
                Benchmark("Your 6M average", "Your own average savings rate over the last 6 months.", avg, Primary),
                Benchmark("50/30/20 rule", "Budget 50% to needs, 30% to wants, 20% to savings. The 20% savings slice is this benchmark.", 20f, Color(0xFF1565C0)),
                Benchmark("FIRE target", "Financial Independence / Retire Early. Saving 50%+ of income is the common FIRE threshold — at this rate you can retire in roughly 17 years from scratch.", 50f, Color(0xFFE65100)),
                Benchmark("Expert recommended", "The widely-cited minimum — save at least 15% of gross income for a comfortable retirement, per most financial planners.", 15f, OnSurfaceVariant),
            ).forEach { b ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(b.label, style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        LinearProgressIndicator(progress = { (b.value / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = b.color, trackColor = SurfaceContainerHighest)
                        Text("${"%.0f".format(b.value)}%", style = MaterialTheme.typography.labelSmall,
                            color = b.color, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                    }
                    Text(b.hint, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.8f))
                }
            }
        }
    }

    // ── Projection ────────────────────────────────────────────────────────────
    if (avgMonthlySavingsAmount > 0) {
        val targets = listOf(1_000.0, 5_000.0, 10_000.0, 25_000.0, 50_000.0)
        Text("Savings Projection", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("At your 6-month avg net savings of ${"$%,.0f".format(avgMonthlySavingsAmount)}/month, how long to reach key milestones.",
            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                targets.forEach { target ->
                    val months = (target / avgMonthlySavingsAmount).toInt()
                    val years  = months / 12
                    val rem    = months % 12
                    val label  = when {
                        years > 0 && rem > 0 -> "${years}y ${rem}mo"
                        years > 0            -> "${years}y"
                        else                 -> "${rem}mo"
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${"$%,.0f".format(target)}", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Surface(shape = RoundedCornerShape(4.dp), color = Primary.copy(alpha = 0.10f)) {
                            Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    OutlinedButton(onClick = { navController.navigate(Screen.AnnualSummary.route) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
        Icon(Icons.Filled.CalendarMonth, null, tint = Primary)
        Spacer(Modifier.width(8.dp))
        Text("View Annual Summary", color = Primary)
    }
}

// ── Comparison tab ────────────────────────────────────────────────────────────

@Composable
private fun StatsComparisonTab(
    selTxs: List<Transaction>,
    prevTxs: List<Transaction>,
    ytdTxs: List<Transaction>,
    allYearMonths: List<Triple<LocalDate, List<Transaction>, String>>,
    selectedMonth: Int
) {
    val selExp  = selTxs.filter { !it.isIncome }
    val prevExp = prevTxs.filter { !it.isIncome }

    val topCatNames = (selExp + prevExp).map { it.category }.filter { it.isNotBlank() }.distinct()
        .filter { cat -> selExp.any { it.category == cat } || prevExp.any { it.category == cat } }
        .sortedByDescending { cat ->
            val cur  = selExp.filter  { it.category == cat }.sumOf { it.amount }
            val prev = prevExp.filter { it.category == cat }.sumOf { it.amount }
            abs(cur - prev)
        }.take(6)

    val maxCatVal = topCatNames.maxOfOrNull { cat ->
        maxOf(selExp.filter { it.category.equals(cat, ignoreCase = true) }.sumOf { it.amount },
            prevExp.filter { it.category.equals(cat, ignoreCase = true) }.sumOf { it.amount })
    }?.toFloat()?.takeIf { it > 0f } ?: 1f

    // ── Category comparison ───────────────────────────────────────────────────
    Text("Category Comparison", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    Text("This month vs last month · sorted by biggest change.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (topCatNames.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No expense data to compare", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            } else {
                topCatNames.forEach { catName ->
                    val current  = selExp.filter  { it.category.equals(catName, ignoreCase = true) }.sumOf { it.amount }.toFloat()
                    val previous = prevExp.filter { it.category.equals(catName, ignoreCase = true) }.sumOf { it.amount }.toFloat()
                    CategoryCompareRow(catName, current, previous, maxCatVal)
                }
            }
        }
    }

    // ── Best / worst month per category ──────────────────────────────────────
    // Pick top 4 categories by highest delta (max month spend − min month spend) across the year.
    // This surfaces the most volatile categories — the ones where behaviour varies most.
    val allYearTopCats = allYearMonths
        .flatMap { (_, txs, _) -> txs.filter { !it.isIncome }.map { it.category } }
        .filter { it.isNotBlank() }.distinct()
        .map { catName ->
            val monthlyAmts = allYearMonths.map { (_, txs, _) ->
                txs.filter { !it.isIncome && it.category.equals(catName, ignoreCase = true) }.sumOf { it.amount }
            }.filter { it > 0 }
            val delta = if (monthlyAmts.size >= 2) monthlyAmts.max() - monthlyAmts.min() else 0.0
            catName to delta
        }
        .sortedByDescending { it.second }.take(4).map { it.first }

    if (allYearTopCats.isNotEmpty()) {
        Text("Best & Worst Month per Category", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("Top 4 most volatile categories (highest gap between best and worst month).", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                allYearTopCats.forEach { catName ->
                    val monthlyAmounts = allYearMonths.map { (_, txs, label) ->
                        val amt = txs.filter { !it.isIncome && it.category.equals(catName, ignoreCase = true) }.sumOf { it.amount }
                        Pair(label, amt)
                    }.filter { it.second > 0 }

                    if (monthlyAmounts.isNotEmpty()) {
                        val best  = monthlyAmounts.minByOrNull { it.second }!!
                        val worst = monthlyAmounts.maxByOrNull { it.second }!!
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(catName, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = Primary.copy(alpha = 0.08f)) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.TrendingDown, null, tint = Primary, modifier = Modifier.size(14.dp))
                                            Text("Best", style = MaterialTheme.typography.labelSmall, color = Primary)
                                        }
                                        Text(best.first, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                        Text("${"$%,.0f".format(best.second)}", style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), color = Tertiary.copy(alpha = 0.08f)) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.TrendingUp, null, tint = Tertiary, modifier = Modifier.size(14.dp))
                                            Text("Worst", style = MaterialTheme.typography.labelSmall, color = Tertiary)
                                        }
                                        Text(worst.first, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                        Text("${"$%,.0f".format(worst.second)}", style = MaterialTheme.typography.bodyMedium, color = Tertiary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (catName != allYearTopCats.last()) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }

    // ── Income stability ──────────────────────────────────────────────────────
    val incomeValues = allYearMonths.map { (_, txs, _) -> txs.filter { it.isIncome }.sumOf { it.amount }.toFloat() }.filter { it > 0f }
    if (incomeValues.size >= 3) {
        val mean   = incomeValues.average().toFloat()
        val stdDev = sqrt(incomeValues.map { (it - mean).let { d -> d * d } }.average()).toFloat()
        val cov    = if (mean > 0f) stdDev / mean * 100f else 0f
        val (stability, stabilityColor) = when {
            cov < 10f  -> Pair("Stable",   Primary)
            cov < 25f  -> Pair("Moderate", Color(0xFFFFA726))
            else       -> Pair("Variable", Tertiary)
        }

        Text("Income Stability", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
        Text("Coefficient of variation (std dev ÷ mean). Lower = more predictable income.",
            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Variability (CoV)", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Text("${"%.1f".format(cov)}%", style = MaterialTheme.typography.titleLarge, color = stabilityColor, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = stabilityColor.copy(alpha = 0.12f)) {
                        Text(stability, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge, color = stabilityColor, fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(progress = { (cov / 50f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = stabilityColor, trackColor = SurfaceContainerHighest)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("< 10% Stable", style = MaterialTheme.typography.labelSmall, color = Primary)
                    Text("10–25% Moderate", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA726))
                    Text("> 25% Variable", style = MaterialTheme.typography.labelSmall, color = Tertiary)
                }
            }
        }
    }

    // ── Year-to-date ──────────────────────────────────────────────────────────
    val ytdIncome   = ytdTxs.filter {  it.isIncome }.sumOf { it.amount }
    val ytdExpenses = ytdTxs.filter { !it.isIncome }.sumOf { it.amount }
    val ytdNet      = ytdIncome - ytdExpenses
    val monthsWithYtdData = (1..selectedMonth).count { m ->
        ytdTxs.any { runCatching { LocalDate.parse(it.createdAt.take(10)).monthValue == m }.getOrDefault(false) }
    }.coerceAtLeast(1)
    val avgMonthlySpend = ytdExpenses / monthsWithYtdData

    Text("Year-to-Date", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            YtdStatRow("Total Income",      ytdIncome,       Primary)
            YtdStatRow("Total Expenses",    ytdExpenses,     Tertiary)
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
            YtdStatRow("Net Savings",       ytdNet,          if (ytdNet >= 0) Primary else Tertiary)
            YtdStatRow("Avg Monthly Spend", avgMonthlySpend, OnSurface)
        }
    }
}

@Composable
private fun CategoryCompareRow(name: String, current: Float, previous: Float, maxVal: Float) {
    val diff = current - previous
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${"$%.0f".format(current)}", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                if (previous > 0f) {
                    Surface(shape = RoundedCornerShape(4.dp), color = (if (diff <= 0f) Primary else Tertiary).copy(alpha = 0.10f)) {
                        Text("${if (diff >= 0) "+" else ""}${"$%.0f".format(diff)}",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diff <= 0f) Primary else Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            LinearProgressIndicator(progress = { (previous / maxVal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Primary.copy(alpha = 0.25f), trackColor = SurfaceContainerHighest)
            LinearProgressIndicator(progress = { (current / maxVal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (diff > 0f) Tertiary else Primary, trackColor = Color.Transparent)
        }
        Text("vs ${"$%.0f".format(previous)} last month", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun YtdStatRow(label: String, value: Double, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text("${if (value >= 0) "" else "-"}${"$%,.0f".format(abs(value))}",
            style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

// ── Reports tab (placeholder) ─────────────────────────────────────────────────

@Composable
private fun StatsReportsTab(navController: NavController) {
    Text("Reports", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

    data class ReportEntry(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String)
    listOf(
        ReportEntry("Monthly Summary",  "Full breakdown for any single month",     Icons.Filled.Description,   Screen.MonthlyReport.route),
        ReportEntry("Quarterly Report", "3-month comparison and category totals",  Icons.Filled.Assessment,    Screen.QuarterlyReport.route),
        ReportEntry("Net Worth",        "Assets, liabilities and cash trajectory", Icons.Filled.AccountBalance, Screen.NetWorth.route),
    ).forEach { r ->
        LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(r.route) }) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(r.icon, null, tint = Primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.title,    style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(r.subtitle, style = MaterialTheme.typography.bodySmall,   color = OnSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant)
            }
        }
    }

    OutlinedButton(onClick = { navController.navigate(Screen.CustomReport.route) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
        Icon(Icons.Filled.Tune, null, tint = Primary)
        Spacer(Modifier.width(8.dp))
        Text("Custom Report", color = Primary)
    }
}
