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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

private data class Budget(
    val category: String,
    val icon: ImageVector,
    val color: Color,
    val limit: Float,
    val spent: Float
) {
    val pct get() = (spent / limit).coerceIn(0f, 1f)
    val remaining get() = (limit - spent).coerceAtLeast(0f)
    val isOver get() = spent > limit
    val status get() = when {
        pct >= 1f   -> "Over budget"
        pct >= 0.85f -> "Almost full"
        else         -> "On track"
    }
    val statusColor get() = when {
        pct >= 1f    -> Color(0xFF920009)
        pct >= 0.85f -> Color(0xFFE65100)
        else         -> Color(0xFF00513F)
    }
}

private val defaultBudgets = listOf(
    Budget("Housing",       Icons.Filled.Home,            Color(0xFF00513F), 1300f, 1200f),
    Budget("Food & Dining", Icons.Filled.Restaurant,      Color(0xFF1565C0), 400f,  340f),
    Budget("Transport",     Icons.Filled.DirectionsCar,   Color(0xFF6A1B9A), 200f,  180f),
    Budget("Entertainment", Icons.Filled.Movie,           Color(0xFFE65100), 150f,  120f),
    Budget("Health",        Icons.Filled.HealthAndSafety, Color(0xFF920009), 100f,  22f),
    Budget("Shopping",      Icons.Filled.ShoppingCart,    Color(0xFF00838F), 250f,  310f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(navController: NavController) {
    val budgets = remember { defaultBudgets.toMutableList() }
    val totalBudget = defaultBudgets.sumOf { it.limit.toDouble() }.toFloat()
    val totalSpent  = defaultBudgets.sumOf { it.spent.toDouble() }.toFloat()
    val overCount   = defaultBudgets.count { it.isOver }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddBudget.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add budget")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddBudget.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "Add budget") }
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
            // Monthly overview card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("APRIL 2026 OVERVIEW", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total Budget", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("\$${"%.0f".format(totalBudget)}", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Spent", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("\$${"%.0f".format(totalSpent)}", style = MaterialTheme.typography.titleLarge,
                                color = if (totalSpent > totalBudget) Tertiary else Primary, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Remaining", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("\$${"%.0f".format((totalBudget - totalSpent).coerceAtLeast(0f))}",
                                style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { (totalSpent / totalBudget).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (totalSpent > totalBudget) Tertiary else Primary,
                            trackColor = SurfaceContainerHighest
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${"%.0f".format(totalSpent / totalBudget * 100)}% used", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            if (overCount > 0) {
                                Text("$overCount over budget", style = MaterialTheme.typography.labelSmall, color = Tertiary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Alerts
            if (overCount > 0 || defaultBudgets.any { it.pct >= 0.85f }) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Alerts", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        defaultBudgets.filter { it.pct >= 0.85f }.forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (b.isOver) Icons.Filled.Warning else Icons.Filled.NotificationsActive,
                                    contentDescription = null, tint = b.statusColor, modifier = Modifier.size(18.dp))
                                Text(
                                    if (b.isOver) "${b.category} is \$${"%.0f".format(b.spent - b.limit)} over budget"
                                    else "${b.category} is at ${"%.0f".format(b.pct * 100)}% of budget",
                                    style = MaterialTheme.typography.bodySmall, color = OnSurface
                                )
                            }
                        }
                    }
                }
            }

            // Budget list
            Text("Category Budgets", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            defaultBudgets.forEach { budget ->
                BudgetCard(budget) { navController.navigate(Screen.EditBudget.createRoute(budget.category)) }
            }
        }
    }
}

@Composable
private fun BudgetCard(budget: Budget, onEdit: () -> Unit) {
    LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(budget.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(budget.icon, contentDescription = null, tint = budget.color, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(budget.category, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(budget.status, style = MaterialTheme.typography.labelSmall, color = budget.statusColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("\$${"%.0f".format(budget.spent)} / \$${"%.0f".format(budget.limit)}",
                        style = MaterialTheme.typography.labelMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    if (budget.isOver) {
                        Text("\$${"%.0f".format(budget.spent - budget.limit)} over", style = MaterialTheme.typography.labelSmall, color = Tertiary)
                    } else {
                        Text("\$${"%.0f".format(budget.remaining)} left", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }
            LinearProgressIndicator(
                progress = { budget.pct },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = budget.statusColor, trackColor = SurfaceContainerHighest
            )
        }
    }
}
