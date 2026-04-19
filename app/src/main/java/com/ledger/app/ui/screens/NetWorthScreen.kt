package com.ledger.app.ui.screens

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.LedgerFloatingCard
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.buildNetWorthCsv
import com.ledger.app.ui.util.shareCsv
import com.ledger.app.ui.viewmodel.DebtViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    navController: NavController,
    walletViewModel: WalletViewModel = hiltViewModel(),
    debtViewModel: DebtViewModel = hiltViewModel(),
    txViewModel: TransactionViewModel = hiltViewModel()
) {
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val debtState   by debtViewModel.state.collectAsStateWithLifecycle()
    val txState     by txViewModel.state.collectAsStateWithLifecycle()
    val context     = LocalContext.current

    LaunchedEffect(Unit) { txViewModel.loadAll(1000u) }

    val today            = LocalDate.now()
    val totalAssets      = walletState.wallets.sumOf { it.balance }
    val totalLiabilities = debtState.debts.sumOf { it.remainingAmount }
    val currentNW        = totalAssets - totalLiabilities

    // Trajectory: reconstruct the last 6 months of net worth by going backward from today.
    // nw[month M] = nw[month M+1] - net_transactions[month M+1]
    val historyData = remember(txState.transactions, totalAssets, totalLiabilities) {
        // monthlyNets[0] = net for current month, [1] = 1 month ago, ..., [5] = 5 months ago
        val monthlyNets = (0..5).map { monthsBack ->
            val d = today.minusMonths(monthsBack.toLong())
            txState.transactions.filter {
                runCatching {
                    val txDate = LocalDate.parse(it.createdAt.take(10))
                    txDate.year == d.year && txDate.monthValue == d.monthValue
                }.getOrElse { false }
            }.sumOf { if (it.isIncome) it.amount else -it.amount }
        }
        // Build trajectory: nwPoints[5] = current, nwPoints[4] = last month, etc.
        val nwPoints = DoubleArray(6)
        nwPoints[5] = currentNW
        for (i in 4 downTo 0) {
            nwPoints[i] = nwPoints[i + 1] - monthlyNets[4 - i]
        }
        nwPoints
    }

    val historyLabels = (5 downTo 0).map { monthsBack ->
        today.minusMonths(monthsBack.toLong()).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    val prevNW     = historyData[4]
    val change     = currentNW - prevNW
    val changePct  = if (prevNW != 0.0) change / Math.abs(prevNW) * 100 else 0.0

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
                actions = {
                    IconButton(onClick = {
                        val csv = buildNetWorthCsv(totalAssets, totalLiabilities, walletState.wallets, debtState.debts)
                        val fileName = "net_worth_${LocalDate.now()}.csv"
                        context.startActivity(Intent.createChooser(shareCsv(context, fileName, csv), "Export CSV"))
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "Export CSV", tint = Primary)
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
            // Hero card with chart
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NET WORTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        "${"$%,.2f".format(currentNW)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = OnSurface, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (change >= 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = if (change >= 0) Primary else Tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "${"$%,.2f".format(Math.abs(change))} (${"%.1f".format(Math.abs(changePct))}%) vs last month",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (change >= 0) Primary else Tertiary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val chartColor = if (change >= 0) Primary else Tertiary
                    val pts = historyData.map { it.toFloat() }
                    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                        val minV  = pts.min(); val maxV = pts.max()
                        val range = (maxV - minV).coerceAtLeast(1f)
                        val step  = size.width / (pts.size - 1)
                        val path  = Path()
                        pts.forEachIndexed { i, v ->
                            val x = i * step
                            val y = size.height - ((v - minV) / range) * size.height * 0.9f - size.height * 0.05f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, chartColor, style = Stroke(width = 2.5.dp.toPx()))
                        pts.forEachIndexed { i, v ->
                            val x = i * step
                            val y = size.height - ((v - minV) / range) * size.height * 0.9f - size.height * 0.05f
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

            // Assets vs Liabilities summary chips
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ASSETS", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.2f".format(totalAssets)}", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                        Text("${walletState.wallets.size} wallet${if (walletState.wallets.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("LIABILITIES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.2f".format(totalLiabilities)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                        Text("${debtState.debts.size} debt${if (debtState.debts.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
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
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    if (selectedSection == 0) {
                        // Wallets as assets
                        if (walletState.wallets.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No wallets yet.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                            }
                        } else {
                            walletState.wallets.forEachIndexed { idx, wallet ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(wallet.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                        if (wallet.description.isNotBlank())
                                            Text(wallet.description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                    Text(
                                        "${"$%,.2f".format(wallet.balance)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Primary, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (idx < walletState.wallets.lastIndex)
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    } else {
                        // Debts as liabilities
                        if (debtState.debts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No debts tracked.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                            }
                        } else {
                            debtState.debts.forEachIndexed { idx, debt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(debt.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                        Text("${debt.debtType} · ${debt.apr}% APR", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                    Text(
                                        "${"$%,.2f".format(debt.remainingAmount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Tertiary, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (idx < debtState.debts.lastIndex)
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        }
    }
}
