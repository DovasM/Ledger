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

// ── Data for allocation detail sheet ─────────────────────────────────────────
private data class AllocHolding(val symbol: String, val name: String, val value: String, val change: String, val isUp: Boolean)
private data class AllocCategory(
    val name: String, val pct: Float, val color: Color,
    val totalValue: String, val monthChange: String, val isMonthUp: Boolean,
    val holdings: List<AllocHolding>,
    val chartPoints: List<Float>
)

private val allocations = listOf(
    AllocCategory("Crypto", 59f, Color(0xFFFF9800), "\$2,100.00", "+12.4%", true,
        listOf(
            AllocHolding("BTC", "Bitcoin", "\$1,680.00", "+5.4%", true),
            AllocHolding("ETH", "Ethereum", "\$420.00", "+2.1%", true),
        ),
        listOf(1600f, 1750f, 1820f, 1700f, 1900f, 2050f, 2100f)
    ),
    AllocCategory("Stocks", 24f, Color(0xFF00513F), "\$854.40", "+1.6%", true,
        listOf(
            AllocHolding("AAPL", "Apple Inc.", "\$430.00", "-0.8%", false),
            AllocHolding("MSFT", "Microsoft", "\$424.40", "+2.9%", true),
        ),
        listOf(820f, 835f, 810f, 840f, 850f, 848f, 854f)
    ),
    AllocCategory("ETFs", 12f, Color(0xFF2196F3), "\$427.20", "+3.2%", true,
        listOf(
            AllocHolding("SPY", "S&P 500 ETF", "\$320.00", "+1.2%", true),
            AllocHolding("QQQ", "Nasdaq ETF", "\$107.20", "+2.1%", true),
        ),
        listOf(390f, 400f, 405f, 398f, 410f, 420f, 427f)
    ),
    AllocCategory("Cash", 5f, Color(0xFF9E9E9E), "\$178.40", "0.0%", true,
        listOf(
            AllocHolding("USD", "Cash Reserve", "\$178.40", "0.0%", true),
        ),
        listOf(178f, 178f, 178f, 178f, 178f, 178f, 178f)
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentPortfolioScreen(navController: NavController) {
    var selectedPeriod by remember { mutableStateOf("1M") }
    val periods = listOf("1W", "1M", "3M", "6M", "1Y", "All")
    var selectedAlloc by remember { mutableStateOf<AllocCategory?>(null) }

    if (selectedAlloc != null) {
        val alloc = selectedAlloc!!
        ModalBottomSheet(
            onDismissRequest = { selectedAlloc = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            AllocDetailSheet(alloc, navController) { selectedAlloc = null }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Investments", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
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
            // Portfolio total
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PORTFOLIO VALUE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("$3,560.00", style = MaterialTheme.typography.displaySmall, color = OnSurface, fontWeight = FontWeight.Bold)
                    Text("+$240.50 (7.2%) this month", style = MaterialTheme.typography.bodySmall, color = Primary)

                    // Period selector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        periods.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = OnPrimary
                                )
                            )
                        }
                    }

                    // Portfolio chart
                    PortfolioChart()
                }
            }

            // Connected Accounts
            Text("Connected Accounts", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    AccountRow("Interactive Brokers", "Brokerage · Stocks & ETFs", "$2,850.00", Icons.Filled.Business) {
                        navController.navigate(Screen.ConnectedAccountDetails.createRoute("Interactive Brokers"))
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    AccountRow("Coinbase", "Crypto Exchange", "$710.00", Icons.Filled.CurrencyBitcoin) {
                        navController.navigate(Screen.ConnectedAccountDetails.createRoute("Coinbase"))
                    }
                }
            }
            // Expanded connect account card
            Surface(
                onClick = { navController.navigate(Screen.ConnectAccount.route) },
                shape = RoundedCornerShape(12.dp),
                color = Primary.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Connect Broker Account", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                        Text("Supports Revolut and Trading 212 via API key", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                }
            }

            // Portfolio Allocation
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Portfolio Allocation", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                Text("Tap to explore", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    allocations.forEach { alloc ->
                        AllocationRow(alloc.name, alloc.pct, alloc.color) { selectedAlloc = alloc }
                    }
                }
            }

            // Quick actions: P&L, Dividends, Alerts
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InvestQuickAction("P&L", Icons.Filled.TrendingUp, Color(0xFF00513F), modifier = Modifier.weight(1f)) {
                    navController.navigate(Screen.InvestmentPnL.route)
                }
                InvestQuickAction("Dividends", Icons.Filled.AttachMoney, Color(0xFF1565C0), modifier = Modifier.weight(1f)) {
                    navController.navigate(Screen.Dividends.route)
                }
                InvestQuickAction("Alerts", Icons.Filled.NotificationsActive, Color(0xFFE65100), modifier = Modifier.weight(1f)) {
                    navController.navigate(Screen.PriceAlerts.route)
                }
            }

            // Holdings
            Text("Holdings", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssetRow("BTC", "Bitcoin", "\$2,100.00", "+5.4%", true, navController)
                    AssetRow("ETH", "Ethereum", "\$710.00", "+2.1%", true, navController)
                    AssetRow("AAPL", "Apple Inc.", "\$430.00", "-0.8%", false, navController)
                    AssetRow("SPY", "S&P 500 ETF", "\$320.00", "+1.2%", true, navController)
                }
            }
        }
    }
}

@Composable
private fun PortfolioChart() {
    val points = listOf(2800f, 2950f, 3100f, 2900f, 3200f, 3400f, 3560f)
    val labels = listOf("1W ago", "", "", "", "", "", "Now")
    val priceLabels = listOf("$2,800", "$3,200", "$3,560")
    val pricePositions = listOf(0f, 0.5f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Price axis labels (top)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            priceLabels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
            val w = size.width
            val h = size.height
            val min = points.min()
            val max = points.max()
            val range = (max - min).takeIf { it > 0f } ?: 1f

            fun xOf(i: Int) = w * i / (points.size - 1)
            fun yOf(v: Float) = h - (h * (v - min) / range) * 0.75f - h * 0.1f

            // Grid lines at 25%, 50%, 75%
            listOf(0.25f, 0.5f, 0.75f).forEach { pct ->
                val y = h * (1f - pct) * 0.8f + h * 0.1f
                drawLine(
                    color = Color(0xFFBEC9C3).copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val path = Path()
            path.moveTo(xOf(0), yOf(points[0]))
            points.forEachIndexed { i, v -> if (i > 0) path.lineTo(xOf(i), yOf(v)) }

            val fillPath = Path().apply {
                addPath(path)
                lineTo(xOf(points.size - 1), h)
                lineTo(xOf(0), h)
                close()
            }
            drawPath(fillPath, brush = Brush.verticalGradient(
                listOf(Color(0xFF00513F).copy(alpha = 0.2f), Color.Transparent)
            ))
            drawPath(path, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))

            // Dots at each point
            points.forEachIndexed { i, v ->
                drawCircle(
                    color = Color(0xFF00513F),
                    radius = if (i == points.size - 1) 5.dp.toPx() else 3.dp.toPx(),
                    center = Offset(xOf(i), yOf(v))
                )
            }
        }

        // % change label
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1M total return", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text("+27.1%  (+\$760)", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccountRow(name: String, subtitle: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AllocationRow(name: String, pct: Float, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
                        Text(
                            "${"%.0f".format(pct)}%",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = color, trackColor = SurfaceContainerHighest
            )
        }
    }
}

@Composable
private fun AllocDetailSheet(alloc: AllocCategory, navController: NavController, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(alloc.color))
                Text(alloc.name, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = alloc.color.copy(alpha = 0.12f)) {
                Text(
                    "${"%.0f".format(alloc.pct)}% of portfolio",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = alloc.color,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("TOTAL VALUE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text(alloc.totalValue, style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("THIS MONTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text(alloc.monthChange, style = MaterialTheme.typography.titleLarge,
                    color = if (alloc.isMonthUp) Primary else Tertiary, fontWeight = FontWeight.Bold)
            }
        }

        // Mini performance chart
        AllocMiniChart(alloc.chartPoints, alloc.color)

        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

        // Holdings in this category
        Text("Holdings", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
        alloc.holdings.forEach { holding ->
            Surface(
                onClick = { onDismiss(); navController.navigate(Screen.AssetDetails.createRoute(holding.symbol)) },
                shape = RoundedCornerShape(8.dp),
                color = SurfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(alloc.color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(holding.symbol.take(2), style = MaterialTheme.typography.labelMedium, color = alloc.color, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(holding.symbol, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(holding.name, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(holding.value, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(holding.change, style = MaterialTheme.typography.labelSmall,
                            color = if (holding.isUp) Primary else Tertiary)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", color = Primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AllocMiniChart(points: List<Float>, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.04f))
    ) {
        val w = size.width; val h = size.height
        val min = points.min(); val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.75f - h * 0.1f
        val path = Path()
        path.moveTo(xOf(0), yOf(points[0]))
        points.forEachIndexed { i, v -> if (i > 0) path.lineTo(xOf(i), yOf(v)) }
        val fill = Path().apply {
            addPath(path); lineTo(xOf(points.size - 1), h); lineTo(xOf(0), h); close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        points.forEachIndexed { i, v ->
            drawCircle(color, radius = if (i == points.size - 1) 4.dp.toPx() else 2.dp.toPx(), center = Offset(xOf(i), yOf(v)))
        }
    }
}

@Composable
private fun InvestQuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    LedgerCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AssetRow(symbol: String, name: String, value: String, change: String, isPositive: Boolean, navController: NavController) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
            Text(name, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text(change, style = MaterialTheme.typography.labelSmall, color = if (isPositive) Primary else Tertiary)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { navController.navigate(Screen.AssetDetails.createRoute(symbol)) }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Details", tint = OnSurfaceVariant)
        }
    }
}
