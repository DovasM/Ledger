package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailsScreen(navController: NavController, symbol: String) {
    var selectedPeriod by remember { mutableStateOf("1M") }
    val periods = listOf("1D", "1W", "1M", "3M", "1Y")
    var isWatchlisted by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(symbol, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isWatchlisted = !isWatchlisted
                        snackbarMessage = if (isWatchlisted) "$symbol added to watchlist" else "$symbol removed from watchlist"
                    }) {
                        Icon(
                            if (isWatchlisted) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isWatchlisted) "Remove from watchlist" else "Add to watchlist",
                            tint = if (isWatchlisted) Color(0xFFF9A825) else OnSurfaceVariant
                        )
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
            // Price header
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bitcoin", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                            Text("\$43,200.00", style = MaterialTheme.typography.displaySmall, color = OnSurface, fontWeight = FontWeight.Bold)
                            Text("+\$2,100 (5.4%) today", style = MaterialTheme.typography.bodySmall, color = Primary)
                        }
                        Text("BTC", style = MaterialTheme.typography.headlineMedium, color = OnSurfaceVariant.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        periods.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary, selectedLabelColor = OnPrimary
                                )
                            )
                        }
                    }

                    AssetPriceChart()

                    // Period return summary
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Period return", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("+13.7%  (+\$5,200)", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // My Position
            Text("My Position", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssetStatRow("Holdings", "0.0486 BTC")
                    AssetStatRow("Current Value", "\$2,100.00")
                    AssetStatRow("Avg. Buy Price", "\$38,500.00")
                    AssetStatRow("Total Return", "+\$228.69 (12.2%)", isPositive = true)
                    AssetStatRow("Unrealized P&L", "+\$228.69", isPositive = true)
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                    AssetStatRow("% of Portfolio", "59.0%")
                    AssetStatRow("First Purchase", "Jan 15, 2025")
                    AssetStatRow("Last Purchase", "Mar 3, 2026")
                }
            }

            // Market Stats
            Text("Market Statistics", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssetStatRow("Market Cap", "\$847.2B")
                    AssetStatRow("24h Volume", "\$18.4B")
                    AssetStatRow("24h High / Low", "\$43,800 / \$42,100")
                    AssetStatRow("Circulating Supply", "19.7M BTC")
                    AssetStatRow("Max Supply", "21M BTC")
                    AssetStatRow("All-Time High", "\$73,750")
                    AssetStatRow("All-Time Low", "\$67.81")
                }
            }

            // Market Intelligence
            Text("Market Intelligence", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IntelligenceRow(Icons.Filled.TrendingUp, "Strong uptrend", "Price above 200-day MA", positive = true)
                    IntelligenceRow(Icons.Filled.BarChart, "High volume", "Volume 42% above average", positive = true)
                    IntelligenceRow(Icons.Filled.Warning, "Overbought signal", "RSI at 72 — watch for pullback", positive = false)
                    IntelligenceRow(Icons.Filled.Psychology, "Sentiment", "84% Bullish on social media", positive = true)
                }
            }
        }
    }
}

@Composable
private fun AssetPriceChart() {
    val points = listOf(38000f, 39500f, 41000f, 40200f, 42500f, 41800f, 43200f)
    val timeLabels = listOf("1W ago", "", "", "", "", "", "Now")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val w = size.width; val h = size.height
            val min = points.min(); val max = points.max()
            val range = (max - min).takeIf { it > 0f } ?: 1f

            fun xOf(i: Int) = w * i / (points.size - 1)
            fun yOf(v: Float) = h - (h * (v - min) / range) * 0.8f - h * 0.05f

            // Grid lines
            listOf(0.25f, 0.5f, 0.75f).forEach { pct ->
                val y = h * (1f - pct) * 0.8f + h * 0.05f
                drawLine(Color(0xFFBEC9C3).copy(alpha = 0.25f), Offset(0f, y), Offset(w, y), 1.dp.toPx())
            }

            val path = Path()
            path.moveTo(xOf(0), yOf(points[0]))
            points.forEachIndexed { i, v -> if (i > 0) path.lineTo(xOf(i), yOf(v)) }

            val fillPath = Path().apply {
                addPath(path); lineTo(xOf(points.size - 1), h); lineTo(xOf(0), h); close()
            }
            drawPath(fillPath, brush = Brush.verticalGradient(listOf(Color(0xFF00513F).copy(alpha = 0.2f), Color.Transparent)))
            drawPath(path, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))
            points.forEachIndexed { i, v ->
                drawCircle(Color(0xFF00513F), radius = if (i == points.size - 1) 5.dp.toPx() else 2.5.dp.toPx(), center = Offset(xOf(i), yOf(v)))
            }
        }

        // Price range labels
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("\$38,000", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("\$40,600", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("\$43,200", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AssetStatRow(label: String, value: String, isPositive: Boolean? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = when (isPositive) { true -> Primary; false -> Tertiary; null -> OnSurface },
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun IntelligenceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, positive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (positive) Primary else Tertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
    }
}
