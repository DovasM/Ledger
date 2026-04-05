package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun WalletsListScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallets", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddWallet.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add wallet")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddTransaction.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "Add transaction") }
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
            // Combined Net Worth Card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("COMBINED NET WORTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("$24,530.80", style = MaterialTheme.typography.displaySmall, color = OnSurface, fontWeight = FontWeight.Bold)
                    Text("+$1,240.50 (5.3%) this month", style = MaterialTheme.typography.bodySmall, color = Primary)

                    // Sparkline graph
                    NetWorthSparkline()

                    // Breakdown mini-stats
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("Cash", "$340")
                        MiniStat("Bank", "$20,630")
                        MiniStat("Invest", "$3,560")
                    }
                }
            }

            Text("My Wallets", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

            WalletCard("Checking Account", "$12,430.50", "Bank of America", Icons.Filled.AccountBalance, navController)
            WalletCard("Savings Account", "$8,200.00", "Bank of America", Icons.Filled.Savings, navController)
            WalletCard("Cash", "$340.30", "Physical cash", Icons.Filled.Money, navController)
            WalletCard("Investment", "$3,560.00", "Brokerage account", Icons.Filled.TrendingUp, navController)

            OutlinedButton(
                onClick = { navController.navigate(Screen.AddWallet.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Primary)
                Spacer(Modifier.width(8.dp))
                Text("Add Wallet", color = Primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun NetWorthSparkline() {
    val points = listOf(18000f, 19500f, 21000f, 20000f, 22000f, 23500f, 24530f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val w = size.width
        val h = size.height
        val min = points.min()
        val max = points.max()
        val range = max - min

        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.8f - h * 0.1f

        val path = Path()
        path.moveTo(xOf(0), yOf(points[0]))
        points.forEachIndexed { i, v -> if (i > 0) path.lineTo(xOf(i), yOf(v)) }

        // Fill
        val fillPath = Path().apply {
            addPath(path)
            lineTo(xOf(points.size - 1), h)
            lineTo(xOf(0), h)
            close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(
            listOf(Color(0xFF00513F).copy(alpha = 0.15f), Color.Transparent)
        ))

        // Line
        drawPath(path, color = Color(0xFF00513F), style = Stroke(width = 2.dp.toPx()))

        // Dot at end
        drawCircle(color = Color(0xFF00513F), radius = 4.dp.toPx(), center = Offset(xOf(points.size - 1), yOf(points.last())))
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

@Composable
private fun WalletCard(
    name: String, balance: String, description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    navController: NavController
) {
    LedgerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { navController.navigate(Screen.WalletDetails.createRoute(1L)) }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(balance, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
