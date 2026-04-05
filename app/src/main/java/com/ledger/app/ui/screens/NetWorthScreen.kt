package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class NWItem(val name: String, val value: Double, val subtitle: String)

private val assets = listOf(
    NWItem("Checking Account",  4_530.80, "Bank of America"),
    NWItem("Savings Account",   12_000.00, "Bank of America"),
    NWItem("Investment Portfolio", 3_560.00, "Interactive Brokers · Coinbase"),
    NWItem("Emergency Fund",    8_000.00, "High-yield savings"),
    NWItem("Cash",              200.00, "On hand"),
)
private val liabilities = listOf(
    NWItem("Credit Card",       1_240.00, "Chase Sapphire"),
    NWItem("Student Loan",      8_500.00, "Federal — 4.5% APR"),
    NWItem("Car Loan",          4_200.00, "Toyota Financial — 3.9% APR"),
)

private val netWorthHistory = listOf(12_000f, 13_400f, 13_100f, 14_200f, 14_800f, 15_500f, 14_350.80f)
private val historyLabels = listOf("Oct", "Nov", "Dec", "Jan", "Feb", "Mar", "Apr")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(navController: NavController) {
    val totalAssets = assets.sumOf { it.value }
    val totalLiabilities = liabilities.sumOf { it.value }
    val netWorth = totalAssets - totalLiabilities
    val prevNetWorth = 15_500.0
    val change = netWorth - prevNetWorth
    val changePct = change / prevNetWorth * 100

    var selectedSection by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Net Worth", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            // Hero card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NET WORTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        "$${"%,.2f".format(netWorth)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = OnSurface, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (change >= 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null, tint = if (change >= 0) Primary else Tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "${"$%,.2f".format(Math.abs(change))} (${"%.1f".format(Math.abs(changePct))}%) vs last month",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (change >= 0) Primary else Tertiary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Chart
                    val chartColor = if (change >= 0) Primary else Tertiary
                    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                        val pts = netWorthHistory
                        val min = pts.min(); val max = pts.max(); val range = (max - min).coerceAtLeast(1f)
                        val step = size.width / (pts.size - 1)
                        val path = Path()
                        pts.forEachIndexed { i, v ->
                            val x = i * step
                            val y = size.height - ((v - min) / range) * size.height * 0.9f - size.height * 0.05f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, chartColor, style = Stroke(width = 2.5.dp.toPx()))
                        pts.forEachIndexed { i, v ->
                            val x = i * step
                            val y = size.height - ((v - min) / range) * size.height * 0.9f - size.height * 0.05f
                            drawCircle(chartColor, 4.dp.toPx(), Offset(x, y))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        historyLabels.forEach { lbl ->
                            Text(lbl, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            // Assets vs Liabilities summary
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ASSETS", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.2f".format(totalAssets)}", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                        Text("${assets.size} items", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("LIABILITIES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.2f".format(totalLiabilities)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                        Text("${liabilities.size} items", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }

            // Section toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Assets", "Liabilities").forEachIndexed { i, label ->
                    FilterChip(
                        selected = selectedSection == i, onClick = { selectedSection = i },
                        label = { Text(label) }, modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (i == 0) Primary else Tertiary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Items list
            val items = if (selectedSection == 0) assets else liabilities
            val itemColor = if (selectedSection == 0) Primary else Tertiary
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    items.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Text(
                                "${"$%,.2f".format(item.value)}",
                                style = MaterialTheme.typography.titleMedium, color = itemColor, fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (idx < items.lastIndex)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}
