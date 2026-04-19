package com.ledger.app.ui.screens

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
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.LedgerFloatingCard
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.buildMonthlyCsv
import com.ledger.app.ui.util.shareCsv
import com.ledger.app.ui.viewmodel.TransactionViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel()
) {
    val txState by txViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today   = LocalDate.now()

    LaunchedEffect(Unit) { txViewModel.loadAll(1000u) }

    var selectedYear  by rememberSaveable { mutableStateOf(today.year) }
    var selectedMonth by rememberSaveable { mutableStateOf(today.monthValue) }

    val isAtCurrent = YearMonth.of(selectedYear, selectedMonth) >= YearMonth.now()

    fun prevMonth() {
        if (selectedMonth == 1) { selectedMonth = 12; selectedYear-- } else selectedMonth--
    }
    fun nextMonth() {
        if (!isAtCurrent) {
            if (selectedMonth == 12) { selectedMonth = 1; selectedYear++ } else selectedMonth++
        }
    }

    val monthTxs = remember(txState.transactions, selectedYear, selectedMonth) {
        txState.transactions.filter {
            runCatching {
                val d = LocalDate.parse(it.createdAt.take(10))
                d.year == selectedYear && d.monthValue == selectedMonth
            }.getOrElse { false }
        }
    }

    val totalIncome   = monthTxs.filter { it.isIncome }.sumOf { it.amount }
    val totalExpenses = monthTxs.filter { !it.isIncome }.sumOf { it.amount }
    val net           = totalIncome - totalExpenses
    val savingsRate: Double? = if (totalIncome > 0) net / totalIncome * 100 else null

    val categoryTotals = remember(monthTxs) {
        monthTxs.filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
    }

    val grouped     = remember(monthTxs) { monthTxs.sortedByDescending { it.createdAt }.groupBy { it.createdAt.take(10) } }
    val sortedDates = remember(grouped) { grouped.keys.sortedDescending() }

    val largestExpense    = monthTxs.filter { !it.isIncome }.maxByOrNull { it.amount }
    val mostFrequentPayee = monthTxs.groupBy { it.title }.maxByOrNull { it.value.size }
    val daysWithSpend     = monthTxs.filter { !it.isIncome }.map { it.createdAt.take(10) }.distinct().size
    val avgDailySpend     = if (daysWithSpend > 0) totalExpenses / daysWithSpend else 0.0

    val monthName = YearMonth.of(selectedYear, selectedMonth).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dateFmt   = DateTimeFormatter.ofPattern("MMM d")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Report", style = MaterialTheme.typography.headlineSmall) },
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
                    } else if (monthTxs.isNotEmpty()) {
                        IconButton(onClick = {
                            val csv = buildMonthlyCsv(selectedYear, selectedMonth, monthTxs)
                            val fileName = "monthly_report_${selectedYear}_${"%02d".format(selectedMonth)}.csv"
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
            // Month selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { prevMonth() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = OnSurface)
                }
                Text(
                    "$monthName $selectedYear",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface, fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { nextMonth() }, enabled = !isAtCurrent) {
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = "Next month",
                        tint = if (!isAtCurrent) OnSurface else OnSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Summary hero
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
                                "${if (net >= 0) "+" else ""}${"$%,.2f".format(net)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (net >= 0) Primary else Tertiary, fontWeight = FontWeight.Bold
                            )
                        }
                        savingsRate?.let { rate ->
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
                    Text("${monthTxs.size} transactions", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }

            if (txState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (monthTxs.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions for $monthName $selectedYear.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                // Category breakdown
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

                // Highlights
                Text("Highlights", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LedgerCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("LARGEST EXPENSE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(largestExpense?.title ?: "—", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
                            if (largestExpense != null)
                                Text("${"$%,.2f".format(largestExpense.amount)}", style = MaterialTheme.typography.titleSmall, color = Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    LedgerCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("TOP PAYEE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(mostFrequentPayee?.key ?: "—", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
                            if (mostFrequentPayee != null)
                                Text("${mostFrequentPayee.value.size}×", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("AVG DAILY SPEND", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("${"$%,.2f".format(avgDailySpend)}", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                        }
                        Text("on $daysWithSpend active day${if (daysWithSpend != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }

                // Transaction list grouped by day
                Text("Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                sortedDates.forEach { dateStr ->
                    val dayTxs = grouped[dateStr] ?: return@forEach
                    val dayDate = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                    val dayNet = dayTxs.sumOf { if (it.isIncome) it.amount else -it.amount }

                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    dayDate?.format(dateFmt) ?: dateStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${if (dayNet >= 0) "+" else ""}${"$%,.2f".format(dayNet)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (dayNet >= 0) Primary else Tertiary
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            dayTxs.forEachIndexed { idx, tx ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(tx.title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                        Text(tx.category, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                    Text(
                                        "${if (tx.isIncome) "+" else "-"}${"$%,.2f".format(tx.amount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (tx.isIncome) Primary else Tertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (idx < dayTxs.lastIndex)
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.10f))
                            }
                        }
                    }
                }
            }
        }
    }
}
