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
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailsScreen(navController: NavController, walletId: Long) {
    var selectedPeriod by remember { mutableStateOf("1M") }
    val periods = listOf("1W", "1M", "3M", "6M", "1Y")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checking Account", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.EditWallet.createRoute(walletId)) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit wallet")
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
            // Balance hero card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("CURRENT BALANCE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(
                                "$12,430.50",
                                style = MaterialTheme.typography.displaySmall,
                                color = OnSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text("+$540.20 (4.5%) this month", style = MaterialTheme.typography.bodySmall, color = Primary)
                        }
                        Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                    }

                    // Period selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        periods.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = OnPrimary
                                )
                            )
                        }
                    }

                    // Balance sparkline
                    WalletBalanceChart()
                }
            }

            // Summary stats
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This Month", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WalletStatCol("Income", "+$5,200", Primary)
                        VerticalDivider(modifier = Modifier.height(40.dp), color = OutlineVariant.copy(alpha = 0.3f))
                        WalletStatCol("Expenses", "-$1,840", Tertiary)
                        VerticalDivider(modifier = Modifier.height(40.dp), color = OutlineVariant.copy(alpha = 0.3f))
                        WalletStatCol("Net", "+$3,360", OnSurface)
                    }
                }
            }

            // Wallet info
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Wallet Details", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                    WalletDetailRow("Type", "Checking Account")
                    WalletDetailRow("Bank", "Bank of America")
                    WalletDetailRow("Opened", "Jan 2020")
                    WalletDetailRow("Total transactions", "247")
                }
            }

            // Recent transactions
            Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransactionRow("Salary", "Apr 1 · Work", "+$5,200.00", isIncome = true)
                    TransactionRow("Rent", "Apr 1 · Housing", "-$1,200.00", isIncome = false)
                    TransactionRow("Groceries", "Mar 31 · Food", "-$124.50", isIncome = false)
                    TransactionRow("Netflix", "Mar 30 · Entertainment", "-$15.99", isIncome = false)
                    TransactionRow("Freelance", "Mar 28 · Work", "+$800.00", isIncome = true)
                }
            }

            TextButton(
                onClick = { navController.navigate(Screen.Transactions.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("See All Transactions", color = Primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WalletBalanceChart() {
    val points = listOf(11800f, 12100f, 11900f, 12300f, 12000f, 12250f, 12430f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val w = size.width
        val h = size.height
        val min = points.min()
        val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f

        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.8f - h * 0.1f

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
            listOf(Color(0xFF00513F).copy(alpha = 0.18f), Color.Transparent)
        ))
        drawPath(path, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = Color(0xFF00513F), radius = 4.dp.toPx(), center = Offset(xOf(points.size - 1), yOf(points.last())))
    }
}

@Composable
private fun WalletStatCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun WalletDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
