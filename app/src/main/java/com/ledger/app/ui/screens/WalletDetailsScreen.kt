package com.ledger.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.TransactionViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailsScreen(
    navController: NavController,
    walletId: String,
    walletViewModel: WalletViewModel = hiltViewModel(),
    transactionViewModel: TransactionViewModel = hiltViewModel()
) {
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val wallet = walletState.wallets.find { it.id == walletId }

    var selectedPeriod by remember { mutableStateOf("1M") }
    val periods = listOf("1W", "1M", "3M", "6M", "1Y")

    LaunchedEffect(walletId) {
        transactionViewModel.loadForWallet(walletId)
    }

    val monthIncome = txState.transactions.filter { it.isIncome }.sumOf { it.amount }
    val monthExpenses = txState.transactions.filter { !it.isIncome }.sumOf { it.amount }
    val recentTxns = txState.transactions.take(5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(wallet?.name ?: "Wallet", style = MaterialTheme.typography.headlineSmall) },
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
                                "${"$%,.2f".format(wallet?.balance ?: 0.0)}",
                                style = MaterialTheme.typography.displaySmall,
                                color = OnSurface, fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        periods.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                            )
                        }
                    }
                    WalletBalanceChart()
                }
            }

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This Period", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WalletStatCol("Income", "+${"$%,.2f".format(monthIncome)}", Primary)
                        VerticalDivider(modifier = Modifier.height(40.dp), color = OutlineVariant.copy(alpha = 0.3f))
                        WalletStatCol("Expenses", "-${"$%,.2f".format(monthExpenses)}", Tertiary)
                        VerticalDivider(modifier = Modifier.height(40.dp), color = OutlineVariant.copy(alpha = 0.3f))
                        WalletStatCol("Net", "${"$%,.2f".format(monthIncome - monthExpenses)}", OnSurface)
                    }
                }
            }

            if (wallet?.description?.isNotBlank() == true) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Wallet Details", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                        WalletDetailRow("Description", wallet.description)
                        WalletDetailRow("Total transactions", txState.transactions.size.toString())
                    }
                }
            }

            Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            if (recentTxns.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        recentTxns.forEach { tx ->
                            TransactionRow(
                                tx.title,
                                tx.category,
                                (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                                isIncome = tx.isIncome
                            )
                        }
                    }
                }
            }

            TextButton(onClick = { navController.navigate(Screen.Transactions.route) }, modifier = Modifier.fillMaxWidth()) {
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
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width; val h = size.height
        val min = points.min(); val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        fun xOf(i: Int) = w * i / (points.size - 1)
        fun yOf(v: Float) = h - (h * (v - min) / range) * 0.8f - h * 0.1f
        val path = Path().apply {
            moveTo(xOf(0), yOf(points[0]))
            points.forEachIndexed { i, v -> if (i > 0) lineTo(xOf(i), yOf(v)) }
        }
        drawPath(
            Path().apply { addPath(path); lineTo(xOf(points.size - 1), h); lineTo(xOf(0), h); close() },
            brush = Brush.verticalGradient(listOf(Color(0xFF00513F).copy(alpha = 0.18f), Color.Transparent))
        )
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
