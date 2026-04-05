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
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

private data class PriceAlert(
    val symbol: String, val name: String, val currentPrice: String,
    val targetPrice: String, val direction: String, // "above" or "below"
    val color: Color, var active: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertsScreen(navController: NavController) {
    val alerts = remember {
        mutableStateListOf(
            PriceAlert("BTC",  "Bitcoin",    "\$43,200",  "\$45,000",  "above", Color(0xFFFF9800)),
            PriceAlert("BTC",  "Bitcoin",    "\$43,200",  "\$40,000",  "below", Color(0xFFFF9800)),
            PriceAlert("ETH",  "Ethereum",   "\$2,340",   "\$2,500",   "above", Color(0xFF3F51B5)),
            PriceAlert("AAPL", "Apple Inc.", "\$189.40",  "\$195.00",  "above", Color(0xFF00513F), active = false),
            PriceAlert("SPY",  "S&P 500 ETF","\$478.20",  "\$470.00",  "below", Color(0xFF2196F3)),
        )
    }
    var showAddSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = SurfaceContainerLowest, tonalElevation = 0.dp) {
            AddAlertSheet { showAddSheet = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Alerts", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) { Icon(Icons.Filled.Add, "Add alert") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Info banner
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text("You'll receive a push notification when any asset reaches your target price.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }

            // Active alerts
            val active = alerts.filter { it.active }
            val inactive = alerts.filter { !it.active }

            if (active.isNotEmpty()) {
                Text("Active (${active.size})", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                active.forEach { alert ->
                    AlertCard(alert, onToggle = { idx ->
                        val i = alerts.indexOf(alert); if (i >= 0) alerts[i] = alerts[i].copy(active = !alerts[i].active)
                    }, onDelete = { alerts.remove(alert) })
                }
            }

            if (inactive.isNotEmpty()) {
                Text("Inactive (${inactive.size})", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                inactive.forEach { alert ->
                    AlertCard(alert, onToggle = { _ ->
                        val i = alerts.indexOf(alert); if (i >= 0) alerts[i] = alerts[i].copy(active = !alerts[i].active)
                    }, onDelete = { alerts.remove(alert) })
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
private fun AlertCard(alert: PriceAlert, onToggle: (PriceAlert) -> Unit, onDelete: (PriceAlert) -> Unit) {
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(alert.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(alert.symbol.take(2), style = MaterialTheme.typography.labelSmall, color = alert.color, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(alert.symbol, style = MaterialTheme.typography.titleSmall, color = if (alert.active) OnSurface else OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (alert.direction == "above") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        null, tint = if (alert.direction == "above") Primary else Tertiary, modifier = Modifier.size(12.dp))
                    Text("${alert.direction} ${alert.targetPrice}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Text("· Now: ${alert.currentPrice}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }
            Switch(checked = alert.active, onCheckedChange = { onToggle(alert) },
                colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary), modifier = Modifier.size(40.dp, 24.dp))
            IconButton(onClick = { onDelete(alert) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlertSheet(onDismiss: () -> Unit) {
    val symbols = listOf("BTC", "ETH", "AAPL", "MSFT", "SPY", "QQQ", "GOOGL")
    var selectedSymbol by remember { mutableStateOf("BTC") }
    var symbolMenuExpanded by remember { mutableStateOf(false) }
    var targetPrice by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("above") }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("New Price Alert", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

        ExposedDropdownMenuBox(expanded = symbolMenuExpanded, onExpandedChange = { symbolMenuExpanded = it }) {
            LedgerTextField(value = selectedSymbol, onValueChange = {}, label = "Asset",
                leadingIcon = { Icon(Icons.Filled.ShowChart, null, tint = OnSurfaceVariant) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = symbolMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor())
            ExposedDropdownMenu(expanded = symbolMenuExpanded, onDismissRequest = { symbolMenuExpanded = false }) {
                symbols.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { selectedSymbol = s; symbolMenuExpanded = false }) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = direction == "above", onClick = { direction = "above" },
                label = { Text("Price goes above") }, modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary),
                leadingIcon = { Icon(Icons.Filled.ArrowUpward, null, modifier = Modifier.size(14.dp)) })
            FilterChip(selected = direction == "below", onClick = { direction = "below" },
                label = { Text("Price drops below") }, modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary),
                leadingIcon = { Icon(Icons.Filled.ArrowDownward, null, modifier = Modifier.size(14.dp)) })
        }

        OutlinedTextField(
            value = targetPrice, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) targetPrice = v },
            label = { Text("Target Price") }, prefix = { Text("\$") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary), shape = RoundedCornerShape(8.dp)
        )

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(6.dp)) {
            Icon(Icons.Filled.NotificationsActive, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Set Alert", style = MaterialTheme.typography.labelLarge)
        }
    }
}
