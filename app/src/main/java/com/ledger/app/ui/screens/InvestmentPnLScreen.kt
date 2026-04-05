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

private data class PnLRow(val symbol: String, val name: String, val costBasis: String, val currentValue: String, val gain: String, val gainPct: String, val isUp: Boolean, val color: Color)

private val pnlData = listOf(
    PnLRow("BTC",  "Bitcoin",     "\$1,420.00", "\$1,680.00", "+\$260.00", "+18.3%", true,  Color(0xFFFF9800)),
    PnLRow("ETH",  "Ethereum",    "\$380.00",   "\$420.00",   "+\$40.00",  "+10.5%", true,  Color(0xFF3F51B5)),
    PnLRow("AAPL", "Apple Inc.",  "\$450.00",   "\$430.00",   "-\$20.00",  "-4.4%",  false, Color(0xFF00513F)),
    PnLRow("MSFT", "Microsoft",   "\$390.00",   "\$424.40",   "+\$34.40",  "+8.8%",  true,  Color(0xFF2196F3)),
    PnLRow("SPY",  "S&P 500 ETF", "\$300.00",   "\$320.00",   "+\$20.00",  "+6.7%",  true,  Color(0xFF00513F)),
    PnLRow("QQQ",  "Nasdaq ETF",  "\$99.00",    "\$107.20",   "+\$8.20",   "+8.3%",  true,  Color(0xFF2196F3)),
)

private val realizedTrades = listOf(
    Triple("SOL · Sold Mar 15", "+\$142.00", true),
    Triple("AMZN · Sold Feb 8", "-\$38.50",  false),
    Triple("META · Sold Jan 22", "+\$89.20", true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentPnLScreen(navController: NavController) {
    val totalCost    = 3039f
    val totalCurrent = 3381.6f
    val unrealized   = totalCurrent - totalCost
    val realized     = 192.70f
    val total        = unrealized + realized

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profit & Loss", style = MaterialTheme.typography.headlineSmall) },
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
            // Summary
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("TOTAL P&L", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("+\$${"%.2f".format(total)}", style = MaterialTheme.typography.displaySmall, color = Primary, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UNREALIZED", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+\$${"%.2f".format(unrealized)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                            Text("Open positions", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                        VerticalDivider(modifier = Modifier.height(48.dp).padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.3f))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("REALIZED", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+\$${"%.2f".format(realized)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                            Text("Closed trades", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }
                    PnLChart()
                }
            }

            // Per-asset unrealized P&L
            Text("Open Positions", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pnlData.forEachIndexed { idx, row ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(row.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(row.symbol.take(2), style = MaterialTheme.typography.labelSmall, color = row.color, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.symbol, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Text("Cost: ${row.costBasis}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(row.currentValue, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.gain, style = MaterialTheme.typography.labelSmall, color = if (row.isUp) Primary else Tertiary, fontWeight = FontWeight.Medium)
                                    Surface(shape = RoundedCornerShape(3.dp), color = (if (row.isUp) Primary else Tertiary).copy(alpha = 0.10f)) {
                                        Text(row.gainPct, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall, color = if (row.isUp) Primary else Tertiary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (idx < pnlData.size - 1) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }

            // Realized trades
            Text("Closed Trades", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    realizedTrades.forEachIndexed { idx, (label, amount, isUp) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isUp) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown, null,
                                tint = if (isUp) Primary else Tertiary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
                            Text(amount, style = MaterialTheme.typography.titleSmall,
                                color = if (isUp) Primary else Tertiary, fontWeight = FontWeight.SemiBold)
                        }
                        if (idx < realizedTrades.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PnLChart() {
    val points = listOf(0f, 80f, 60f, 120f, 180f, 150f, 210f, 240f, 260f, 290f, 310f, 342.6f)
    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
        val w = size.width; val h = size.height
        val min = points.min(); val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.85f - h * 0.08f
        val path = Path(); path.moveTo(xOf(0), yOf(points[0]))
        points.forEachIndexed { i, v -> if (i > 0) path.lineTo(xOf(i), yOf(v)) }
        val fill = Path().apply { addPath(path); lineTo(xOf(points.size-1), h); lineTo(xOf(0), h); close() }
        drawPath(fill, brush = Brush.verticalGradient(listOf(Color(0xFF00513F).copy(alpha = 0.18f), Color.Transparent)))
        drawPath(path, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = Color(0xFF00513F), radius = 4.dp.toPx(), center = Offset(xOf(points.size-1), yOf(points.last())))
    }
}
