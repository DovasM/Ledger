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

private data class ForecastEvent(val date: String, val title: String, val amount: String, val isIncome: Boolean)

private val forecastEvents = listOf(
    ForecastEvent("May 1",  "Salary",        "+\$5,200.00", true),
    ForecastEvent("May 1",  "Rent",          "-\$1,200.00", false),
    ForecastEvent("May 1",  "Netflix",       "-\$15.99",    false),
    ForecastEvent("May 1",  "Bus Pass",      "-\$45.00",    false),
    ForecastEvent("May 4",  "Spotify",       "-\$9.99",     false),
    ForecastEvent("May 5",  "Gym",           "-\$40.00",    false),
    ForecastEvent("May 5",  "Internet",      "-\$45.00",    false),
    ForecastEvent("May 15", "Groceries est.", "-\$130.00",  false),
    ForecastEvent("May 18", "Freelance",     "+\$800.00",   true),
    ForecastEvent("May 28", "Electricity",   "-\$85.00",    false),
)

private val forecastBalances = listOf(
    24530f, 23264f, 23249f, 23204f, 23164f, 23159f, 23074f, 23029f,
    22899f, 23699f, 23614f, 23529f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowForecastScreen(navController: NavController) {
    val currentBalance = 24530.80f
    val projectedBalance = 23529.82f
    val monthlyIn = 6000f
    val monthlyOut = 1570f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cash Flow Forecast", style = MaterialTheme.typography.headlineSmall) },
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
            // Current vs projected
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("MAY 2026 PROJECTION", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Today", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("\$${"%.2f".format(currentBalance)}", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.ArrowForward, null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("End of May", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("\$${"%.2f".format(projectedBalance)}", style = MaterialTheme.typography.titleLarge,
                                color = if (projectedBalance > currentBalance) Primary else Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    ForecastChart(forecastBalances)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Apr 30", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("May 31", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }

            // Monthly summary
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.ArrowDownward, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text("Expected In", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("+\$${"%.0f".format(monthlyIn)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.ArrowUpward, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                        Text("Expected Out", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("-\$${"%.0f".format(monthlyOut)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Savings, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text("Net", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("+\$${"%.0f".format(monthlyIn - monthlyOut)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Upcoming events
            Text("Scheduled Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    forecastEvents.forEachIndexed { idx, event ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (event.isIncome) Primary.copy(alpha = 0.10f) else SurfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(event.date, style = MaterialTheme.typography.labelSmall, color = if (event.isIncome) Primary else OnSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Text(event.title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
                            Text(event.amount, style = MaterialTheme.typography.titleSmall,
                                color = if (event.isIncome) Primary else Tertiary, fontWeight = FontWeight.SemiBold)
                        }
                        if (idx < forecastEvents.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }

            // Insight
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Lightbulb, null, tint = Color(0xFFF9A825), modifier = Modifier.size(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Insight", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("Your recurring expenses total \$${"%,.2f".format(monthlyOut)}/month. " +
                            "At your current income, you save \$${"%.0f".format((monthlyIn - monthlyOut) / monthlyIn * 100)}% each month automatically.",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastChart(points: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width; val h = size.height
        val min = points.min(); val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.8f - h * 0.1f
        // Dashed line from midpoint to show forecast vs actual
        val splitIdx = 3
        val path = Path(); path.moveTo(xOf(0), yOf(points[0]))
        for (i in 1..splitIdx) path.lineTo(xOf(i), yOf(points[i]))
        val forecastPath = Path(); forecastPath.moveTo(xOf(splitIdx), yOf(points[splitIdx]))
        for (i in (splitIdx + 1) until points.size) forecastPath.lineTo(xOf(i), yOf(points[i]))
        val fill = Path().apply {
            addPath(path); addPath(forecastPath)
            lineTo(xOf(points.size - 1), h); lineTo(xOf(0), h); close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(Color(0xFF00513F).copy(alpha = 0.12f), Color.Transparent)))
        drawPath(path, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))
        drawPath(forecastPath, color = Color(0xFF00513F).copy(alpha = 0.5f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
        drawCircle(color = Color(0xFF00513F), radius = 4.dp.toPx(), center = Offset(xOf(splitIdx), yOf(points[splitIdx])))
    }
}
