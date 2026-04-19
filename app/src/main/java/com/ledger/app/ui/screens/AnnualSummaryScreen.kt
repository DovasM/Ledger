package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import kotlin.math.roundToInt
import java.time.LocalDate

private val monthsShort = listOf("J","F","M","A","M","J","J","A","S","O","N","D")
private val monthsFull  = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualSummaryScreen(
    navController: NavController,
    txViewModel: TransactionViewModel  = hiltViewModel(),
    catViewModel: CategoryViewModel    = hiltViewModel()
) {
    val txState  by txViewModel.state.collectAsStateWithLifecycle()
    val catState by catViewModel.state.collectAsStateWithLifecycle()

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) {
        txViewModel.loadAll()
        catViewModel.load()
    }

    val today = LocalDate.now()

    val availableYears = remember(txState.transactions) {
        txState.transactions
            .mapNotNull { runCatching { LocalDate.parse(it.createdAt.take(10)).year }.getOrNull() }
            .distinct().sorted().ifEmpty { listOf(today.year) }
    }

    var selectedYear  by rememberSaveable { mutableStateOf(today.year) }
    var showYearMenu  by remember { mutableStateOf(false) }

    fun inMonth(s: String, y: Int, m: Int) = runCatching {
        val d = LocalDate.parse(s.take(10)); d.year == y && d.monthValue == m
    }.getOrDefault(false)

    // Per-month data for selected year
    val monthData = (1..12).map { m ->
        val txs      = txState.transactions.filter { inMonth(it.createdAt, selectedYear, m) }
        val income   = txs.filter {  it.isIncome }.sumOf { it.amount }.toFloat()
        val expenses = txs.filter { !it.isIncome }.sumOf { it.amount }.toFloat()
        val rate     = if (income > 0f) ((income - expenses) / income * 100f).coerceIn(-100f, 100f) else 0f
        Triple(income, expenses, rate)
    }

    val monthlyIncome   = monthData.map { it.first }
    val monthlyExpenses = monthData.map { it.second }
    val savingsRates    = monthData.map { it.third }

    val totalIncome    = monthlyIncome.sum().toDouble()
    val totalExpenses  = monthlyExpenses.sum().toDouble()
    val totalSavings   = totalIncome - totalExpenses
    val activeSavings  = savingsRates.filter { it > 0f }
    val avgSavingsRate = if (activeSavings.isNotEmpty()) activeSavings.average().toFloat() else 0f
    val bestMonthIdx   = savingsRates.indexOfFirst { it == activeSavings.maxOrNull() }
    val bestMonth      = if (bestMonthIdx >= 0) monthsFull[bestMonthIdx] else "—"
    val monthsTracked  = monthlyIncome.count { it > 0f }

    // Top categories YTD
    val ytdExpenses = txState.transactions.filter {
        runCatching { LocalDate.parse(it.createdAt.take(10)).year == selectedYear && !it.isIncome }.getOrDefault(false)
    }
    val topCategories = ytdExpenses
        .groupBy { it.category }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .entries.sortedByDescending { it.value }.take(5)
    val maxCatAmount = topCategories.firstOrNull()?.value?.toFloat()?.takeIf { it > 0f } ?: 1f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annual Summary", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showYearMenu = true }) {
                            Text("$selectedYear", style = MaterialTheme.typography.labelLarge, color = Primary)
                            Icon(Icons.Filled.ArrowDropDown, null, tint = Primary)
                        }
                        DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                            availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y") },
                                    onClick = { selectedYear = y; showYearMenu = false },
                                    trailingIcon = if (y == selectedYear) ({
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
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero totals
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("$selectedYear YEAR TO DATE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AnnualStat("Total Income",  "+${"$%,.0f".format(totalIncome)}",    Primary)
                        AnnualStat("Total Spent",   "-${"$%,.0f".format(totalExpenses)}",  Tertiary, alignment = Alignment.CenterHorizontally)
                        AnnualStat("Net Saved",
                            "${if (totalSavings >= 0) "+" else ""}${"$%,.0f".format(totalSavings)}",
                            if (totalSavings >= 0) Primary else Tertiary, alignment = Alignment.End)
                    }
                    AnnualChart(monthlyIncome, monthlyExpenses, monthsFull)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        monthsShort.forEach { m -> Text(m, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant) }
                    }
                }
            }

            // Highlights
            Text("Year Highlights", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HighlightCard("Avg Savings Rate", "${"%.1f".format(avgSavingsRate)}%",   Icons.Filled.Savings,       Primary,              modifier = Modifier.weight(1f))
                HighlightCard("Best Month",        bestMonth,                              Icons.Filled.EmojiEvents,   Color(0xFFF9A825),    modifier = Modifier.weight(1f))
                HighlightCard("Months Tracked",    "$monthsTracked",                       Icons.Filled.CalendarMonth, Color(0xFF1565C0),    modifier = Modifier.weight(1f))
            }

            // Monthly savings rate
            Text("Monthly Savings Rate", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeMonths = savingsRates.mapIndexedNotNull { i, r -> if (r > 0f) Pair(i, r) else null }
                    if (activeMonths.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No data for $selectedYear", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        }
                    } else {
                        activeMonths.forEach { (monthIdx, rate) ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(monthsFull[monthIdx], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(28.dp))
                                LinearProgressIndicator(
                                    progress = { (rate / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = when {
                                        rate >= 50f -> Primary
                                        rate >= 20f -> Color(0xFF1565C0)
                                        rate >= 0f  -> Color(0xFFFFA726)
                                        else        -> Tertiary
                                    },
                                    trackColor = SurfaceContainerHighest
                                )
                                Text("${"%.1f".format(rate)}%", style = MaterialTheme.typography.labelSmall,
                                    color = OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                            }
                        }
                    }
                }
            }

            // Top categories YTD
            Text("Top Spending Categories (YTD)", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (topCategories.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No expense data for $selectedYear", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        }
                    } else {
                        topCategories.forEach { (catName, amount) ->
                            val cat   = catState.categories.find { it.name.equals(catName, ignoreCase = true) }
                            val color = cat?.let { runCatching { colorHexToColor(it.colorHex) }.getOrNull() } ?: Primary
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                Text(catName, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                                LinearProgressIndicator(
                                    progress = { (amount.toFloat() / maxCatAmount).coerceIn(0f, 1f) },
                                    modifier = Modifier.width(80.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = color, trackColor = SurfaceContainerHighest
                                )
                                Text("${"$%,.0f".format(amount)}", style = MaterialTheme.typography.labelSmall,
                                    color = OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnualStat(label: String, value: String, color: Color, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HighlightCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    LedgerCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun AnnualChart(income: List<Float>, expenses: List<Float>, labels: List<String>) {
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    var canvasWidth by remember { mutableStateOf(0f) }
    val incColor = Color(0xFF00513F)
    val expColor = Color(0xFF920009)
    val n = income.size

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
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

            val incPath = Path(); incPath.moveTo(xOf(0), yOf(income[0]))
            income.forEachIndexed { i, v -> if (i > 0 && v > 0f) incPath.lineTo(xOf(i), yOf(v)) }
            val expPath = Path(); expPath.moveTo(xOf(0), yOf(expenses[0]))
            expenses.forEachIndexed { i, v -> if (i > 0 && v > 0f) expPath.lineTo(xOf(i), yOf(v)) }

            drawPath(incPath, color = incColor, style = Stroke(width = 2.dp.toPx()))
            drawPath(expPath, color = expColor, style = Stroke(width = 2.dp.toPx()))

            income.forEachIndexed { i, v ->
                if (v > 0f) drawCircle(incColor, radius = 3.dp.toPx(), center = Offset(xOf(i), yOf(v)))
            }
            expenses.forEachIndexed { i, v ->
                if (v > 0f) drawCircle(expColor, radius = 3.dp.toPx(), center = Offset(xOf(i), yOf(v)))
            }

            selectedIdx?.let { idx ->
                val ix = xOf(idx)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(ix, 0f), Offset(ix, h), strokeWidth = 1.dp.toPx())
                if (income[idx] > 0f)   drawCircle(incColor, radius = 5.dp.toPx(), center = Offset(ix, yOf(income[idx])))
                if (expenses[idx] > 0f) drawCircle(expColor, radius = 5.dp.toPx(), center = Offset(ix, yOf(expenses[idx])))
            }
        }

        selectedIdx?.let { idx ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(labels[idx], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(32.dp))
                if (income[idx] > 0f)
                    Text("+${"$%,.0f".format(income[idx])}", style = MaterialTheme.typography.labelSmall, color = incColor, fontWeight = FontWeight.SemiBold)
                if (expenses[idx] > 0f)
                    Text("-${"$%,.0f".format(expenses[idx])}", style = MaterialTheme.typography.labelSmall, color = expColor, fontWeight = FontWeight.SemiBold)
                if (income[idx] > 0f && expenses[idx] > 0f) {
                    val net = income[idx] - expenses[idx]
                    Text("net ${"$%,.0f".format(net)}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        } ?: Text(
            "Hold & drag to inspect",
            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
