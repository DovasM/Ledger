package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val masterEnabled           by vm.notifMaster.collectAsStateWithLifecycle()
    val budgetAlertsEnabled     by vm.notifBudget.collectAsStateWithLifecycle()
    val budgetThreshold         by vm.notifBudgetPct.collectAsStateWithLifecycle()
    val billRemindersEnabled    by vm.notifBills.collectAsStateWithLifecycle()
    val billDaysBefore          by vm.notifBillDays.collectAsStateWithLifecycle()
    val weeklySummaryEnabled    by vm.notifWeekly.collectAsStateWithLifecycle()
    val summaryDay              by vm.notifWeeklyDay.collectAsStateWithLifecycle()
    val unusualSpendingEnabled  by vm.notifUnusual.collectAsStateWithLifecycle()
    val unusualSensitivity      by vm.notifSensitivity.collectAsStateWithLifecycle()
    val investmentAlertsEnabled by vm.notifInvestment.collectAsStateWithLifecycle()
    val goalMilestonesEnabled   by vm.notifGoals.collectAsStateWithLifecycle()
    val recurringRemindersEnabled by vm.notifRecurring.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", style = MaterialTheme.typography.headlineSmall) },
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
            // Master toggle
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Notifications, null, tint = Primary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Push Notifications", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(if (masterEnabled) "Enabled" else "All notifications disabled", style = MaterialTheme.typography.bodySmall, color = if (masterEnabled) Primary else OnSurfaceVariant)
                        }
                    }
                    Switch(checked = masterEnabled, onCheckedChange = { vm.setNotifMaster(it) }, colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary))
                }
            }

            if (masterEnabled) {
                // Budget alerts
                NotifSection(title = "Budget & Spending") {
                    NotifToggleRow(Icons.Filled.PieChart, Color(0xFF00513F), "Budget Alerts", "Notify when nearing your spending limits", budgetAlertsEnabled) { vm.setNotifBudget(it) }
                    if (budgetAlertsEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Alert me when I reach", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("70%", "80%", "90%", "100%").forEach { pct ->
                                    FilterChip(
                                        selected = budgetThreshold == pct.dropLast(1),
                                        onClick = { vm.setNotifBudgetPct(pct.dropLast(1)) },
                                        label = { Text(pct, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    NotifToggleRow(Icons.Filled.TrendingUp, Color(0xFFE65100), "Unusual Spending", "Alert when spending is above normal for a category", unusualSpendingEnabled) { vm.setNotifUnusual(it) }
                    if (unusualSpendingEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Sensitivity", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Low", "Medium", "High").forEach { s ->
                                    FilterChip(
                                        selected = unusualSensitivity == s, onClick = { vm.setNotifSensitivity(s) },
                                        label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE65100), selectedLabelColor = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bills & recurring
                NotifSection(title = "Bills & Recurring") {
                    NotifToggleRow(Icons.Filled.Repeat, Color(0xFF1565C0), "Bill Reminders", "Remind before recurring bills are due", billRemindersEnabled) { vm.setNotifBills(it) }
                    if (billRemindersEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Remind me", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("1 day", "3 days", "7 days").forEach { d ->
                                    FilterChip(
                                        selected = billDaysBefore == d.split(" ")[0],
                                        onClick = { vm.setNotifBillDays(d.split(" ")[0]) },
                                        label = { Text(d, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF1565C0), selectedLabelColor = Color.White)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    NotifToggleRow(Icons.Filled.Sync, Color(0xFF1565C0), "Recurring Reminders", "Remind to log recurring transactions", recurringRemindersEnabled) { vm.setNotifRecurring(it) }
                }

                // Reports
                NotifSection(title = "Reports & Insights") {
                    NotifToggleRow(Icons.Filled.BarChart, Color(0xFF00513F), "Weekly Summary", "Receive a weekly spending summary", weeklySummaryEnabled) { vm.setNotifWeekly(it) }
                    if (weeklySummaryEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Send on", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday").forEach { d ->
                                    FilterChip(
                                        selected = summaryDay == d, onClick = { vm.setNotifWeeklyDay(d) },
                                        label = { Text(d.take(3), style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                    NotifToggleRow(Icons.Filled.EmojiEvents, Color(0xFFFF9800), "Goal Milestones", "Notify when you reach a savings goal milestone", goalMilestonesEnabled) { vm.setNotifGoals(it) }
                }

                // Investments
                NotifSection(title = "Investments") {
                    NotifToggleRow(Icons.Filled.ShowChart, Color(0xFFE65100), "Investment Alerts", "Price alerts and portfolio changes", investmentAlertsEnabled) { vm.setNotifInvestment(it) }
                }
            }
        }
    }
}

@Composable
private fun NotifSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        LedgerCard(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) { content() } }
    }
}

@Composable
private fun NotifToggleRow(icon: ImageVector, iconColor: Color, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary))
    }
}
