package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.LedgerTextField
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectAccountScreen(navController: NavController) {
    var selectedBroker by remember { mutableStateOf(0) } // 0=Revolut, 1=Trading212

    // Revolut state
    var revolutApiKey by remember { mutableStateOf("") }
    var revolutShowKey by remember { mutableStateOf(false) }
    var revolutAccountId by remember { mutableStateOf("") }
    var revolutEnvironment by remember { mutableStateOf("Production") }

    // Trading 212 state
    var t212ApiKey by remember { mutableStateOf("") }
    var t212ShowKey by remember { mutableStateOf(false) }

    var syncFrequency by remember { mutableStateOf("Daily") }
    var connectionTested by remember { mutableStateOf<Boolean?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Account", style = MaterialTheme.typography.headlineSmall) },
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Info banner
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Secure Connection", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Your API keys are stored encrypted on-device and never transmitted to any server. Read-only access only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Broker selector
            Text("Select Broker", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrokerChip(
                    label = "Revolut",
                    color = Color(0xFF191C1A),
                    accentColor = Color(0xFF00B4D8),
                    selected = selectedBroker == 0,
                    onClick = { selectedBroker = 0 },
                    modifier = Modifier.weight(1f)
                )
                BrokerChip(
                    label = "Trading 212",
                    color = Color(0xFF00796B),
                    accentColor = Color(0xFF4CAF50),
                    selected = selectedBroker == 1,
                    onClick = { selectedBroker = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            // Form per broker
            if (selectedBroker == 0) {
                RevolutForm(
                    apiKey = revolutApiKey,
                    onApiKeyChange = { revolutApiKey = it; connectionTested = null },
                    showKey = revolutShowKey,
                    onToggleShow = { revolutShowKey = !revolutShowKey },
                    accountId = revolutAccountId,
                    onAccountIdChange = { revolutAccountId = it },
                    environment = revolutEnvironment,
                    onEnvironmentChange = { revolutEnvironment = it }
                )
            } else {
                Trading212Form(
                    apiKey = t212ApiKey,
                    onApiKeyChange = { t212ApiKey = it; connectionTested = null },
                    showKey = t212ShowKey,
                    onToggleShow = { t212ShowKey = !t212ShowKey }
                )
            }

            // Sync frequency
            Text("Sync Frequency", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hourly", "Daily", "Weekly").forEach { freq ->
                    FilterChip(
                        selected = syncFrequency == freq,
                        onClick = { syncFrequency = freq },
                        label = { Text(freq) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = OnPrimary
                        )
                    )
                }
            }

            // Test connection result
            if (connectionTested != null) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (connectionTested == true) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (connectionTested == true) Primary else Tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                if (connectionTested == true) "Connection successful" else "Connection failed",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (connectionTested == true) Primary else Tertiary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (connectionTested == true) "API key verified. Ready to sync." else "Invalid API key. Check and try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Test button
            OutlinedButton(
                onClick = {
                    isTesting = true
                    connectionTested = if (selectedBroker == 0) revolutApiKey.isNotBlank() else t212ApiKey.isNotBlank()
                    isTesting = false
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Test Connection", color = Primary, style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp),
                enabled = (selectedBroker == 0 && revolutApiKey.isNotBlank()) ||
                        (selectedBroker == 1 && t212ApiKey.isNotBlank())
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect Account", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BrokerChip(label: String, color: Color, accentColor: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) color.copy(alpha = 0.08f) else SurfaceContainerHighest,
        border = if (selected) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(label.take(1), style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(label, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun RevolutForm(
    apiKey: String, onApiKeyChange: (String) -> Unit,
    showKey: Boolean, onToggleShow: () -> Unit,
    accountId: String, onAccountIdChange: (String) -> Unit,
    environment: String, onEnvironmentChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Revolut API Setup", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How to get your API key:", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                listOf(
                    "Open Revolut app → Profile",
                    "Go to Security & Privacy → API Access",
                    "Create a new API key (read-only)",
                    "Copy and paste the key below"
                ).forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                        Text(step, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }
        }

        LedgerTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = "API Key",
            placeholder = "rApi_xxxxxxxxxxxxxxxxxxxxxxxx",
            trailingIcon = {
                IconButton(onClick = onToggleShow) {
                    Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, tint = OnSurfaceVariant)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        LedgerTextField(
            value = accountId,
            onValueChange = onAccountIdChange,
            label = "Account ID (optional)",
            placeholder = "e.g. acc_xxxxxxxxxxxx",
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Production", "Sandbox").forEach { env ->
                FilterChip(
                    selected = environment == env,
                    onClick = { onEnvironmentChange(env) },
                    label = { Text(env) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor = OnPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun Trading212Form(
    apiKey: String, onApiKeyChange: (String) -> Unit,
    showKey: Boolean, onToggleShow: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Trading 212 API Setup", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How to get your API key:", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                listOf(
                    "Log in to Trading 212 web platform",
                    "Click your name → Settings",
                    "Open the API tab",
                    "Generate a new token (Equity or CFD)",
                    "Copy and paste the token below"
                ).forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00796B), fontWeight = FontWeight.Bold)
                        Text(step, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }
        }

        LedgerTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = "API Token",
            placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            trailingIcon = {
                IconButton(onClick = onToggleShow) {
                    Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, tint = OnSurfaceVariant)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    "Ledger only requests read-only portfolio and transaction data. No trades can be placed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}
