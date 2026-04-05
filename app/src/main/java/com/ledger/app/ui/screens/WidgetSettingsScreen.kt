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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class WidgetConfig(
    val id: Int, val name: String, val description: String,
    val icon: ImageVector, val color: Color,
    val size: String
)

private val widgets = listOf(
    WidgetConfig(0, "Balance Widget", "Shows total balance at a glance", Icons.Filled.AccountBalance, Primary, "Small (2×1)"),
    WidgetConfig(1, "Budget Progress", "Your top budget categories and usage", Icons.Filled.PieChart, Color(0xFF1565C0), "Medium (4×2)"),
    WidgetConfig(2, "Recent Transactions", "Last 5 transactions", Icons.Filled.List, Color(0xFF6A1B9A), "Medium (4×3)"),
    WidgetConfig(3, "Net Worth", "Total net worth with monthly change", Icons.Filled.TrendingUp, Color(0xFF00838F), "Small (2×2)"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(navController: NavController) {
    var enabledWidgets by remember { mutableStateOf(setOf(0)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widgets", style = MaterialTheme.typography.headlineSmall) },
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
            // Info card
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Widgets, null, tint = Primary, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Home Screen Widgets", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("Configure which data shows in each widget. To add a widget to your home screen, long-press an empty area and select Widgets.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            }

            Text("Available Widgets", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

            widgets.forEach { widget ->
                val isEnabled = widget.id in enabledWidgets
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(widget.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(widget.icon, null, tint = widget.color, modifier = Modifier.size(22.dp)) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(widget.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Text(widget.description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                Text(widget.size, style = MaterialTheme.typography.labelSmall, color = widget.color, fontWeight = FontWeight.Medium)
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { enabledWidgets = if (isEnabled) enabledWidgets - widget.id else enabledWidgets + widget.id },
                                colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = widget.color)
                            )
                        }

                        // Widget preview
                        if (isEnabled) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Brush.horizontalGradient(listOf(widget.color.copy(alpha = 0.15f), widget.color.copy(alpha = 0.05f)))),
                                contentAlignment = Alignment.Center
                            ) {
                                when (widget.id) {
                                    0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("TOTAL BALANCE", style = MaterialTheme.typography.labelSmall, color = widget.color.copy(alpha = 0.7f))
                                        Text("\$24,530.80", style = MaterialTheme.typography.titleLarge, color = widget.color, fontWeight = FontWeight.Bold)
                                    }
                                    1 -> Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("Housing" to 0.85f, "Food" to 0.42f, "Transport" to 0.61f).forEach { (name, pct) ->
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(name, style = MaterialTheme.typography.labelSmall, color = widget.color, modifier = Modifier.width(60.dp))
                                                LinearProgressIndicator(progress = { pct }, modifier = Modifier.weight(1f).height(4.dp), color = widget.color, trackColor = widget.color.copy(alpha = 0.2f))
                                            }
                                        }
                                    }
                                    2 -> Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        listOf("-\$67 Groceries", "+\$5,200 Salary", "-\$15.99 Netflix").forEach { tx ->
                                            Text(tx, style = MaterialTheme.typography.labelSmall, color = widget.color)
                                        }
                                    }
                                    3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("NET WORTH", style = MaterialTheme.typography.labelSmall, color = widget.color.copy(alpha = 0.7f))
                                        Text("\$14,350", style = MaterialTheme.typography.titleLarge, color = widget.color, fontWeight = FontWeight.Bold)
                                        Text("+\$850 this month", style = MaterialTheme.typography.labelSmall, color = widget.color.copy(alpha = 0.8f))
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
