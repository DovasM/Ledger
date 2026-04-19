package com.ledger.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.LedgerFloatingCard
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.buildQuarterlyCsv
import com.ledger.app.ui.util.shareCsv
import com.ledger.app.ui.viewmodel.TransactionViewModel
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarterlyReportScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel()
) {
    val txState = txViewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val today   = LocalDate.now()

    LaunchedEffect(Unit) { txViewModel.loadAll() }

    val currentQuarter = (today.monthValue - 1) / 3 + 1
    var selectedYear    by rememberSaveable { mutableStateOf(today.year) }
    var selectedQuarter by rememberSaveable { mutableStateOf(currentQuarter) }

    val isAtCurrent = selectedYear > today.year || (selectedYear == today.year && selectedQuarter >= currentQuarter)

    fun prevQuarter() {
        if (selectedQuarter == 1) { selectedQuarter = 4; selectedYear-- } else selectedQuarter--
    }
    fun nextQuarter() {
        if (!isAtCurrent) {
            if (selectedQuarter == 4) { selectedQuarter = 1; selectedYear++ } else selectedQuarter++
        }
    }

    val quarterStartMonth = (selectedQuarter - 1) * 3 + 1
    val quarterMonths = quarterStartMonth..(quarterStartMonth + 2)
    val shortMonthNames = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    data class MonthStats(val month: Int, val income: Double, val expenses: Double) {
        val net get() = income - expenses
        val savingsRate: Double? get() = if (income > 0) net / income * 100 else null
    }

    val monthStats = remember(txState.transactions, selectedYear, selectedQuarter) {
        quarterMonths.map { month ->
            val txs = txState.transactions.filter {
                runCatching {
                    val d = LocalDate.parse(it.createdAt.take(10))
                    d.year == selectedYear && d.monthValue == month
                }.getOrElse { false }
            }
            MonthStats(
                month,
                txs.filter { it.isIncome }.sumOf { it.amount },
                txs.filter { !it.isIncome }.sumOf { it.amount }
            )
        }
    }

    val quarterTxs = remember(txState.transactions, selectedYear, selectedQuarter) {
        txState.transactions.filter {
            runCatching {
                val d = LocalDate.parse(it.createdAt.take(10))
                d.year == selectedYear && d.monthValue in quarterMonths
            }.getOrElse { false }
        }
    }

    val totalIncome   = monthStats.sumOf { it.income }
    val totalExpenses = monthStats.sumOf { it.expenses }
    val totalNet      = totalIncome - totalExpenses
    val quarterSavingsRate: Double? = if (totalIncome > 0) totalNet / totalIncome * 100 else null

    val categoryTotals = remember(quarterTxs) {
        quarterTxs.filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
    }

    val bestMonth  = monthStats.maxByOrNull { it.net }
    val worstMonth = monthStats.minByOrNull { it.net }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quarterly Report", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (txState.isLoading) {
                        Box(modifier = Modifier.padding(end = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        }
                    } else if (quarterTxs.isNotEmpty()) {
                        IconButton(onClick = {
                            val csv = buildQuarterlyCsv(selectedYear, selectedQuarter, quarterTxs)
                            val fileName = "quarterly_report_${selectedYear}_Q${selectedQuarter}.csv"
                            context.startActivity(Intent.createChooser(shareCsv(context, fileName, csv), "Export CSV"))
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = "Export CSV", tint = Primary)
                        }
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quarter selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { prevQuarter() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous quarter", tint = OnSurface)
                }
                Text(
                    "Q$selectedQuarter $selectedYear",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface, fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { nextQuarter() }, enabled = !isAtCurrent) {
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = "Next quarter",
                        tint = if (!isAtCurrent) OnSurface else OnSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Quarter summary hero
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+${"$%,.2f".format(totalIncome)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("-${"$%,.2f".format(totalExpenses)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("NET", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(
                                "${if (totalNet >= 0) "+" else ""}${"$%,.2f".format(totalNet)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (totalNet >= 0) Primary else Tertiary, fontWeight = FontWeight.Bold
                            )
                        }
                        quarterSavingsRate?.let { rate ->
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("SAVINGS RATE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text(
                                    "${"%.1f".format(rate)}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (rate >= 0) Primary else Tertiary, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text("${quarterTxs.size} transactions", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }

            // Monthly comparison table
            Text("Month-by-Month", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // Header row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("", modifier = Modifier.width(56.dp))
                        listOf("Income", "Expenses", "Net").forEachIndexed { i, label ->
                            Text(
                                label, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant, textAlign = TextAlign.End
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineVariant.copy(alpha = 0.2f))
                    monthStats.forEach { stats ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                shortMonthNames[stats.month],
                                modifier = Modifier.width(56.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${"$%,.0f".format(stats.income)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = Primary, textAlign = TextAlign.End
                            )
                            Text(
                                "${"$%,.0f".format(stats.expenses)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = Tertiary, textAlign = TextAlign.End
                            )
                            Text(
                                "${if (stats.net >= 0) "+" else ""}${"$%,.0f".format(stats.net)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (stats.net >= 0) Primary else Tertiary,
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End
                            )
                        }
                        if (stats.month != quarterMonths.last)
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.10f))
                    }
                    // Totals row
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("${"$%,.0f".format(totalIncome)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Text("${"$%,.0f".format(totalExpenses)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Tertiary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Text(
                            "${if (totalNet >= 0) "+" else ""}${"$%,.0f".format(totalNet)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (totalNet >= 0) Primary else Tertiary,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.End
                        )
                    }
                }
            }

            // Best / worst month
            if (quarterTxs.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    bestMonth?.let { bm ->
                        LedgerCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("BEST MONTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text(shortMonthNames[bm.month], style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                                Text("+${"$%,.2f".format(bm.net)}", style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    worstMonth?.let { wm ->
                        LedgerCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("WORST MONTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Text(shortMonthNames[wm.month], style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                                Text("${if (wm.net >= 0) "+" else ""}${"$%,.2f".format(wm.net)}", style = MaterialTheme.typography.bodyMedium, color = if (wm.net >= 0) Primary else Tertiary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Category totals
                if (categoryTotals.isNotEmpty()) {
                    Text("Spending by Category", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val maxAmt = categoryTotals.first().value
                            categoryTotals.forEach { (cat, amount) ->
                                val pct = if (totalExpenses > 0) amount / totalExpenses * 100 else 0.0
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cat, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("${"%.1f".format(pct)}%", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                            Text("${"$%,.2f".format(amount)}", style = MaterialTheme.typography.bodyMedium, color = Tertiary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { if (maxAmt > 0) (amount / maxAmt).toFloat() else 0f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = Tertiary, trackColor = Tertiary.copy(alpha = 0.10f)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions for Q$selectedQuarter $selectedYear.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}
