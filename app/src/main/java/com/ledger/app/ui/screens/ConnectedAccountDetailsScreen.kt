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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

private data class AccountAsset(val symbol: String, val name: String, val value: String, val change: String, val isUp: Boolean, val allocation: Float)

private val accountData = mapOf(
    "Interactive Brokers" to Triple(
        Color(0xFF00513F),
        Icons.Filled.Business,
        listOf(
            AccountAsset("AAPL", "Apple Inc.", "\$430.00", "+2.1%", true, 15f),
            AccountAsset("MSFT", "Microsoft Corp.", "\$424.40", "+2.9%", true, 15f),
            AccountAsset("SPY", "S&P 500 ETF", "\$320.00", "+1.2%", true, 11f),
            AccountAsset("QQQ", "Nasdaq-100 ETF", "\$107.20", "+0.8%", true, 4f),
            AccountAsset("GOOGL", "Alphabet Inc.", "\$890.00", "-0.4%", false, 31f),
            AccountAsset("AMZN", "Amazon.com Inc.", "\$678.40", "+1.5%", true, 24f),
        )
    ),
    "Coinbase" to Triple(
        Color(0xFF1565C0),
        Icons.Filled.CurrencyBitcoin,
        listOf(
            AccountAsset("BTC", "Bitcoin", "\$1,680.00", "+5.4%", true, 83f),
            AccountAsset("ETH", "Ethereum", "\$420.00", "+2.1%", true, 17f),
        )
    ),
)

private val chartPoints = mapOf(
    "Interactive Brokers" to listOf(2600f, 2720f, 2680f, 2790f, 2810f, 2780f, 2850f),
    "Coinbase" to listOf(580f, 640f, 610f, 680f, 695f, 705f, 710f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAccountDetailsScreen(navController: NavController, accountName: String) {
    val (color, icon, assets) = accountData[accountName] ?: Triple(Primary, Icons.Filled.Business, emptyList<AccountAsset>())
    val points = chartPoints[accountName] ?: listOf(100f, 100f)
    val totalValue = assets.sumOf { it.value.replace("\$", "").replace(",", "").toDoubleOrNull() ?: 0.0 }
    var selectedPeriod by remember { mutableStateOf("1M") }
    val periods = listOf("1W", "1M", "3M", "6M", "1Y")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(accountName, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.ConnectAccount.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Account settings", tint = OnSurface)
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Account summary card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(accountName, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text("Connected · Last sync: just now", style = MaterialTheme.typography.bodySmall, color = Primary)
                        }
                    }

                    Text(
                        "\$${"%.2f".format(totalValue)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text("+\$124.50 (4.6%) this month", style = MaterialTheme.typography.bodySmall, color = Primary)

                    // Period selector
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        periods.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Performance chart
                    AccountChart(points, color)
                }
            }

            // Holdings
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Holdings", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text("${assets.size} assets", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    assets.forEachIndexed { idx, asset ->
                        AccountHoldingRow(asset, color, navController)
                        if (idx < assets.size - 1) {
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                        }
                    }
                }
            }

            // Allocation breakdown
            Text("Allocation", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    assets.forEach { asset ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(asset.symbol, style = MaterialTheme.typography.labelSmall, color = OnSurface, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(40.dp))
                            LinearProgressIndicator(
                                progress = { asset.allocation / 100f },
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = color, trackColor = SurfaceContainerHighest
                            )
                            Text(
                                "${"%.0f".format(asset.allocation)}%",
                                style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChart(points: List<Float>, color: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
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
        drawPath(fill, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent)))
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(xOf(points.size - 1), yOf(points.last())))
    }
}

@Composable
private fun AccountHoldingRow(asset: AccountAsset, color: Color, navController: NavController) {
    Surface(
        onClick = { navController.navigate(Screen.AssetDetails.createRoute(asset.symbol)) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(asset.symbol.take(2), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(asset.symbol, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(asset.name, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(asset.value, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    asset.change,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (asset.isUp) Primary else Tertiary,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
