package com.ledger.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.PriceAlertViewModel
import uniffi.ledger.PriceAlert

private val alertColors = listOf(
    Color(0xFFFF9800), Color(0xFF3F51B5), Color(0xFF00513F),
    Color(0xFF2196F3), Color(0xFF920009), Color(0xFF6A1B9A),
)

private fun alertColor(alert: PriceAlert) =
    alertColors[alert.symbol.hashCode().let { if (it < 0) -it else it } % alertColors.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertsScreen(
    navController: NavController,
    viewModel: PriceAlertViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.load() }
    var showAddSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            AddAlertSheet(viewModel) { showAddSheet = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Alerts", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) { Icon(Icons.Filled.Add, "Add alert") }
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Info banner
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(
                        "You'll receive a push notification when any asset reaches your target price.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (state.alerts.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No price alerts yet. Add one below.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                val active   = state.alerts.filter { it.active }
                val inactive = state.alerts.filter { !it.active }

                if (active.isNotEmpty()) {
                    Text("Active (${active.size})", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    active.forEach { alert ->
                        AlertCard(
                            alert = alert,
                            onToggle = { viewModel.setActive(alert.id, !alert.active) },
                            onDelete = { viewModel.deleteAlert(alert.id) }
                        )
                    }
                }

                if (inactive.isNotEmpty()) {
                    Text("Inactive (${inactive.size})", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    inactive.forEach { alert ->
                        AlertCard(
                            alert = alert,
                            onToggle = { viewModel.setActive(alert.id, !alert.active) },
                            onDelete = { viewModel.deleteAlert(alert.id) }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showAddSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = Primary)
                Spacer(Modifier.width(8.dp))
                Text("New Price Alert", color = Primary)
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: PriceAlert,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val color = alertColor(alert)
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(alert.symbol.take(2), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (alert.assetName.isNotBlank()) alert.assetName else alert.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (alert.active) OnSurface else OnSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (alert.direction == "above") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        null,
                        tint = if (alert.direction == "above") Primary else Tertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "${alert.direction} \$${"%.2f".format(alert.targetPrice)}",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                    )
                }
            }
            Switch(
                checked = alert.active,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary),
                modifier = Modifier.size(40.dp, 24.dp)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlertSheet(viewModel: PriceAlertViewModel, onDismiss: () -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var assetName by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("above") }
    var showErrors by remember { mutableStateOf(false) }

    val isSymbolValid = symbol.isNotBlank()
    val isPriceValid  = targetPrice.toDoubleOrNull()?.let { it > 0 } ?: false
    val isFormValid   = isSymbolValid && isPriceValid

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("New Price Alert", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

        LedgerTextField(
            value = symbol,
            onValueChange = { symbol = it.uppercase() },
            label = "Ticker Symbol",
            placeholder = "e.g. BTC, AAPL",
            leadingIcon = { Icon(Icons.Filled.ShowChart, null, tint = OnSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            isError = showErrors && !isSymbolValid,
            supportingText = if (showErrors && !isSymbolValid) "Symbol is required" else null
        )

        LedgerTextField(
            value = assetName,
            onValueChange = { assetName = it },
            label = "Asset Name (optional)",
            placeholder = "e.g. Bitcoin, Apple Inc.",
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = direction == "above", onClick = { direction = "above" },
                label = { Text("Price goes above") }, modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary),
                leadingIcon = { Icon(Icons.Filled.ArrowUpward, null, modifier = Modifier.size(14.dp)) }
            )
            FilterChip(
                selected = direction == "below", onClick = { direction = "below" },
                label = { Text("Price drops below") }, modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary),
                leadingIcon = { Icon(Icons.Filled.ArrowDownward, null, modifier = Modifier.size(14.dp)) }
            )
        }

        LedgerTextField(
            value = targetPrice,
            onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) targetPrice = v },
            label = "Target Price (\$)",
            leadingIcon = { Icon(Icons.Filled.AttachMoney, null, tint = OnSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            isError = showErrors && !isPriceValid,
            supportingText = if (showErrors && !isPriceValid) "Enter a price greater than 0" else null
        )

        Button(
            onClick = {
                showErrors = true
                val price = targetPrice.toDoubleOrNull()
                if (isFormValid && price != null) {
                    viewModel.createAlert(
                        symbol = symbol,
                        assetName = assetName,
                        targetPrice = price,
                        direction = direction
                    ) { onDismiss() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(Icons.Filled.NotificationsActive, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Set Alert", style = MaterialTheme.typography.labelLarge)
        }
    }
}
