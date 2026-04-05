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

private data class DividendTx(val symbol: String, val name: String, val amount: String, val date: String, val yield_: String, val color: Color)
private data class DividendCalendar(val symbol: String, val exDate: String, val payDate: String, val estimated: String, val color: Color)

private val dividendHistory = listOf(
    DividendTx("AAPL", "Apple Inc.",     "\$3.20",  "Mar 15, 2026", "1.7%",  Color(0xFF00513F)),
    DividendTx("SPY",  "S&P 500 ETF",   "\$5.40",  "Mar 22, 2026", "1.4%",  Color(0xFF2196F3)),
    DividendTx("MSFT", "Microsoft",     "\$4.10",  "Mar 8, 2026",  "0.9%",  Color(0xFF2196F3)),
    DividendTx("AAPL", "Apple Inc.",     "\$3.20",  "Dec 15, 2025", "1.7%",  Color(0xFF00513F)),
    DividendTx("SPY",  "S&P 500 ETF",   "\$5.40",  "Dec 22, 2025", "1.4%",  Color(0xFF2196F3)),
)

private val upcomingDividends = listOf(
    DividendCalendar("AAPL", "Jun 6, 2026",  "Jun 15, 2026", "~\$3.20", Color(0xFF00513F)),
    DividendCalendar("SPY",  "Jun 18, 2026", "Jun 25, 2026", "~\$5.40", Color(0xFF2196F3)),
    DividendCalendar("MSFT", "Jun 2, 2026",  "Jun 10, 2026", "~\$4.10", Color(0xFF2196F3)),
)

private val monthlyDividends = listOf(0f, 0f, 12.7f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 12.7f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DividendsScreen(navController: NavController) {
    val totalYtd = dividendHistory.filter { it.date.contains("2026") }
        .sumOf { it.amount.replace("\$","").toDoubleOrNull() ?: 0.0 }.toFloat()
    val annualizedRate = totalYtd * 3f // Q1 * 4 (simplified)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dividends", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
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
            // YTD summary
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("DIVIDEND INCOME 2026", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("+\$${"%.2f".format(totalYtd)}", style = MaterialTheme.typography.displaySmall, color = Primary, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("YTD Received", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("\$${"%.2f".format(totalYtd)}", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Annualized est.", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("~\$${"%.0f".format(annualizedRate)}", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Next payout", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("Jun 15", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    DividendChart(monthlyDividends)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("J","F","M","A","M","J","J","A","S","O","N","D").forEach { m ->
                            Text(m, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            // Upcoming
            Text("Upcoming Dividends", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upcomingDividends.forEachIndexed { idx, div ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(div.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(div.symbol.take(2), style = MaterialTheme.typography.labelSmall, color = div.color, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(div.symbol, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Text("Ex: ${div.exDate} · Pay: ${div.payDate}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = div.color.copy(alpha = 0.10f)) {
                                Text(div.estimated, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium, color = div.color, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (idx < upcomingDividends.size - 1) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }

            // History
            Text("History", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    dividendHistory.forEachIndexed { idx, div ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(div.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(div.symbol.take(2), style = MaterialTheme.typography.labelSmall, color = div.color, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(div.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Medium)
                                Text(div.date, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+${div.amount}", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                                Text("Yield: ${div.yield_}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                        }
                        if (idx < dividendHistory.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DividendChart(monthly: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        val w = size.width; val h = size.height
        val barW = w / monthly.size * 0.5f
        val maxVal = monthly.max().takeIf { it > 0f } ?: 1f
        monthly.forEachIndexed { i, v ->
            val x = w * i / monthly.size + barW * 0.5f
            val barH = (h * 0.8f * v / maxVal).coerceAtLeast(0f)
            if (v > 0f) {
                drawRoundRect(
                    color = Color(0xFF00513F), topLeft = Offset(x, h - barH),
                    size = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}
