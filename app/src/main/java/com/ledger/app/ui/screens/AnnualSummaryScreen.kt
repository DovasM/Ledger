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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private val monthlyIncome   = listOf(5200f, 5200f, 6000f, 6000f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
private val monthlyExpenses = listOf(1980f, 1740f, 2100f, 1840f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
private val months = listOf("J","F","M","A","M","J","J","A","S","O","N","D")
private val savingsRates = listOf(61.9f, 66.5f, 65.0f, 69.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualSummaryScreen(navController: NavController) {
    val years = listOf("2024", "2025", "2026")
    var selectedYear by remember { mutableStateOf("2026") }
    var showYearMenu by remember { mutableStateOf(false) }

    val totalIncome   = monthlyIncome.sum()
    val totalExpenses = monthlyExpenses.sum()
    val totalSavings  = totalIncome - totalExpenses
    val avgSavingsRate = savingsRates.filter { it > 0f }.average().toFloat()
    val bestMonth = months[savingsRates.indexOfFirst { it == savingsRates.filter { r -> r > 0f }.max() }]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annual Summary", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    Box {
                        TextButton(onClick = { showYearMenu = true }) {
                            Text(selectedYear, style = MaterialTheme.typography.labelLarge, color = Primary)
                            Icon(Icons.Filled.ArrowDropDown, null, tint = Primary)
                        }
                        DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                            years.forEach { y ->
                                DropdownMenuItem(text = { Text(y) }, onClick = { selectedYear = y; showYearMenu = false },
                                    trailingIcon = if (y == selectedYear) ({ Icon(Icons.Filled.Check, null, tint = Primary, modifier = Modifier.size(16.dp)) }) else null)
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
                        AnnualStat("Total Income", "+\$${"%.0f".format(totalIncome)}", Primary)
                        AnnualStat("Total Spent",  "-\$${"%.0f".format(totalExpenses)}", Tertiary, alignment = Alignment.CenterHorizontally)
                        AnnualStat("Net Saved",    "+\$${"%.0f".format(totalSavings)}", Primary, alignment = Alignment.End)
                    }
                    AnnualChart(monthlyIncome, monthlyExpenses)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        months.forEach { m -> Text(m, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant) }
                    }
                }
            }

            // Highlights
            Text("Year Highlights", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HighlightCard("Avg Savings Rate", "${"%.1f".format(avgSavingsRate)}%", Icons.Filled.Savings, Primary, modifier = Modifier.weight(1f))
                HighlightCard("Best Month", bestMonth, Icons.Filled.EmojiEvents, Color(0xFFF9A825), modifier = Modifier.weight(1f))
                HighlightCard("Months Tracked", "${monthlyIncome.count { it > 0f }}", Icons.Filled.CalendarMonth, Color(0xFF1565C0), modifier = Modifier.weight(1f))
            }

            // Savings rate chart
            Text("Monthly Savings Rate", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savingsRates.filter { it > 0f }.forEachIndexed { i, rate ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(listOf("Jan","Feb","Mar","Apr")[i], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(28.dp))
                            LinearProgressIndicator(
                                progress = { (rate / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = when { rate >= 65f -> Primary; rate >= 50f -> Color(0xFF1565C0); else -> Tertiary },
                                trackColor = SurfaceContainerHighest
                            )
                            Text("${"%.1f".format(rate)}%", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(38.dp))
                        }
                    }
                }
            }

            // Top categories YTD
            Text("Top Spending Categories (YTD)", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("Housing",       4800f, Color(0xFF00513F)),
                        Triple("Food & Dining", 1340f, Color(0xFF1565C0)),
                        Triple("Transport",     710f,  Color(0xFF6A1B9A)),
                        Triple("Entertainment", 430f,  Color(0xFFE65100)),
                        Triple("Health",        160f,  Color(0xFF920009)),
                    ).forEach { (name, amount, color) ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(name, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                            LinearProgressIndicator(
                                progress = { (amount / 7440f).coerceIn(0f, 1f) },
                                modifier = Modifier.width(80.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = color, trackColor = SurfaceContainerHighest
                            )
                            Text("\$${"%.0f".format(amount)}", style = MaterialTheme.typography.labelSmall, color = OnSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
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
private fun AnnualChart(income: List<Float>, expenses: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width; val h = size.height
        val n = income.size
        val maxVal = (income + expenses).max().takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (n - 1)
        fun yOf(v: Float) = h - (h * v / maxVal) * 0.85f - h * 0.05f

        val incPath = Path(); incPath.moveTo(xOf(0), yOf(income[0]))
        income.forEachIndexed { i, v -> if (i > 0 && v > 0f) incPath.lineTo(xOf(i), yOf(v)) }
        val expPath = Path(); expPath.moveTo(xOf(0), yOf(expenses[0]))
        expenses.forEachIndexed { i, v -> if (i > 0 && v > 0f) expPath.lineTo(xOf(i), yOf(v)) }

        drawPath(incPath, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))
        drawPath(expPath, color = Color(0xFF920009), style = Stroke(width = 2.dp.toPx()))

        income.forEachIndexed { i, v ->
            if (v > 0f) drawCircle(color = Color(0xFF00513F), radius = 3.dp.toPx(), center = Offset(xOf(i), yOf(v)))
        }
        expenses.forEachIndexed { i, v ->
            if (v > 0f) drawCircle(color = Color(0xFF920009), radius = 3.dp.toPx(), center = Offset(xOf(i), yOf(v)))
        }
    }
}
