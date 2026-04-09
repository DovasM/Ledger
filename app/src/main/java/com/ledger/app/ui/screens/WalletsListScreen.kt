package com.ledger.app.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.TransactionViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel
import uniffi.ledger.Wallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsListScreen(
    navController: NavController,
    viewModel: WalletViewModel = hiltViewModel(),
    txViewModel: TransactionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val txState by txViewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.load(); txViewModel.loadAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallets", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddWallet.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "Add wallet") }
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
            val totalBalance = state.wallets.sumOf { it.balance }

            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("COMBINED BALANCE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        "${"$%,.2f".format(totalBalance)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    WalletDistributionBar(wallets = state.wallets)
                    Spacer(Modifier.height(4.dp))
                    LedgerTrendChart(
                        transactions = txState.transactions,
                        currentBalance = totalBalance,
                        accentColor = Primary
                    )
                }
            }

            Text("My Wallets", style = MaterialTheme.typography.headlineSmall, color = OnSurface)

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (state.wallets.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No wallets yet. Add one to get started.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                var longPressWallet by remember { mutableStateOf<uniffi.ledger.Wallet?>(null) }

                if (longPressWallet != null) {
                    val w = longPressWallet!!
                    LedgerActionDialog(
                        title = w.name,
                        subtitle = "Balance: ${"$%,.2f".format(w.balance)}",
                        onDismiss = { longPressWallet = null },
                        onEdit = { longPressWallet = null; navController.navigate(Screen.EditWallet.createRoute(w.id)) }
                    )
                }

                state.wallets.forEach { wallet ->
                    WalletCard(wallet, navController, onLongClick = { longPressWallet = wallet })
                }
            }

        }
    }
}

@Composable
private fun WalletDistributionBar(wallets: List<Wallet>) {
    if (wallets.isEmpty()) return

    val segmentColors = listOf(
        Color(0xFF00513F), Color(0xFF1565C0), Color(0xFF6A1B9A),
        Color(0xFFE65100), Color(0xFF00838F), Color(0xFF558B2F)
    )

    val total = wallets.sumOf { it.balance.coerceAtLeast(0.0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Segmented bar
        Row(
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        ) {
            if (total <= 0.0) {
                // All wallets are zero or negative — show a neutral bar
                Box(modifier = Modifier.fillMaxSize().background(OutlineVariant.copy(alpha = 0.3f)))
            } else {
                wallets.forEachIndexed { i, wallet ->
                    val fraction = (wallet.balance.coerceAtLeast(0.0) / total).toFloat()
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(fraction)
                                .fillMaxHeight()
                                .background(segmentColors[i % segmentColors.size])
                        )
                    }
                }
            }
        }

        // Legend
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            wallets.take(4).forEachIndexed { i, wallet ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(segmentColors[i % segmentColors.size])
                    )
                    Text(
                        wallet.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletCard(wallet: Wallet, navController: NavController, onLongClick: () -> Unit = {}) {
    LedgerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { navController.navigate(Screen.WalletDetails.createRoute(wallet.id)) },
        onLongClick = onLongClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.titleMedium, color = OnSurface)
                if (wallet.description.isNotBlank()) {
                    Text(wallet.description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${"$%,.2f".format(wallet.balance)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (wallet.balance >= 0) OnSurface else Tertiary,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
