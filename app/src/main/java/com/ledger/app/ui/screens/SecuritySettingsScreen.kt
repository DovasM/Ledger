package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(navController: NavController) {
    var biometricEnabled by remember { mutableStateOf(false) }
    var pinEnabled by remember { mutableStateOf(false) }
    var autoLock by remember { mutableStateOf("1 min") }
    var hideInSwitcher by remember { mutableStateOf(true) }
    var requireAuthLarge by remember { mutableStateOf(false) }
    var largeThreshold by remember { mutableStateOf("500") }
    var showPinSheet by remember { mutableStateOf(false) }
    var showCalc by remember { mutableStateOf(false) }

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = largeThreshold, accentColor = Primary,
            onDismiss = { showCalc = false },
            onConfirm = { largeThreshold = it; showCalc = false }
        )
    }

    if (showPinSheet) {
        ModalBottomSheet(onDismissRequest = { showPinSheet = false }, containerColor = SurfaceContainerLowest, tonalElevation = 0.dp) {
            PinSetupSheet { showPinSheet = false; pinEnabled = true }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App lock
            SecSection(title = "App Lock") {
                SecToggleRow(Icons.Filled.Fingerprint, Color(0xFF1565C0), "Biometric Lock", "Use fingerprint or face ID to unlock", biometricEnabled) { biometricEnabled = it }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Pin, null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PIN Lock", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(if (pinEnabled) "PIN is set" else "Set a 4-digit PIN to lock the app", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    if (pinEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showPinSheet = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("Change", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(onClick = { pinEnabled = false }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                        }
                    } else {
                        Button(onClick = { showPinSheet = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Set PIN", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            // Auto-lock timer
            SecSection(title = "Auto-Lock") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lock the app automatically after inactivity.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Immediately", "1 min", "5 min", "15 min", "Never").forEach { t ->
                            FilterChip(
                                selected = autoLock == t, onClick = { autoLock = t },
                                label = { Text(t, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                            )
                        }
                    }
                }
            }

            // Privacy
            SecSection(title = "Privacy") {
                SecToggleRow(Icons.Filled.VisibilityOff, Color(0xFF455A64), "Hide in App Switcher", "Blur the app when switching between apps", hideInSwitcher) { hideInSwitcher = it }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                SecToggleRow(Icons.Filled.Security, Color(0xFF920009), "Auth for Large Transactions", "Require biometric/PIN for large amounts", requireAuthLarge) { requireAuthLarge = it }
                if (requireAuthLarge) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Amount threshold", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Surface(onClick = { showCalc = true }, color = SurfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$$largeThreshold", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                                Icon(Icons.Filled.Calculate, null, tint = Primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Change credentials
            SecSection(title = "Account") {
                SecNavRow(Icons.Filled.Lock, Color(0xFF1565C0), "Change Password", "Update your account password") {}
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                SecNavRow(Icons.Filled.DeleteForever, Tertiary, "Delete Account", "Permanently remove all data") {}
            }
        }
    }
}

@Composable
private fun SecSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        LedgerCard(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) { content() } }
    }
}

@Composable
private fun SecToggleRow(icon: ImageVector, iconColor: Color, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary))
    }
}

@Composable
private fun SecNavRow(icon: ImageVector, iconColor: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PinSetupSheet(onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(0) } // 0=enter, 1=confirm
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(if (stage == 0) "Set PIN" else "Confirm PIN", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        Text(if (stage == 0) "Enter a 4-digit PIN" else "Re-enter your PIN to confirm", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val current = if (stage == 0) pin else confirmPin
            repeat(4) { i ->
                Box(modifier = Modifier.size(20.dp).run {
                    if (i < current.length) androidx.compose.ui.Modifier.size(20.dp)
                        .clip(CircleShape).background(Primary)
                    else androidx.compose.ui.Modifier.size(20.dp).clip(CircleShape).background(SurfaceContainerHighest)
                })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","⌫")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(72.dp))
                        } else {
                            Surface(onClick = {
                                val current = if (stage == 0) pin else confirmPin
                                if (key == "⌫") {
                                    if (stage == 0) pin = pin.dropLast(1) else confirmPin = confirmPin.dropLast(1)
                                } else if (current.length < 4) {
                                    if (stage == 0) { pin += key; if (pin.length == 4) stage = 1 }
                                    else { confirmPin += key; if (confirmPin.length == 4 && confirmPin == pin) onDone() }
                                }
                            }, shape = RoundedCornerShape(12.dp), color = SurfaceContainerHigh, modifier = Modifier.size(72.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(key, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
