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
import androidx.compose.runtime.Composable
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

private data class GoalData(
    val name: String,
    val current: Float,
    val target: Float,
    val deadline: String,
    val icon: ImageVector,
    val color: Color,
    val monthlyDeposit: Float
)

private val goals = listOf(
    GoalData("Emergency Fund", 6500f, 10000f, "Oct 2026", Icons.Filled.HealthAndSafety, Color(0xFF00513F), 500f),
    GoalData("Vacation", 1200f, 3000f, "Jul 2026", Icons.Filled.BeachAccess, Color(0xFF1565C0), 300f),
    GoalData("New Laptop", 800f, 1500f, "Jun 2026", Icons.Filled.Laptop, Color(0xFF6A1B9A), 200f),
    GoalData("Car Down Payment", 4200f, 15000f, "Dec 2027", Icons.Filled.DirectionsCar, Color(0xFFE65100), 450f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(navController: NavController) {
    val totalSaved = goals.sumOf { it.current.toDouble() }.toFloat()
    val totalTarget = goals.sumOf { it.target.toDouble() }.toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings Goals", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddGoal.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add goal")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddTransaction.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Add transaction")
            }
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Aggregated wealth card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AGGREGATED WEALTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        "$${"%.2f".format(totalSaved)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "of $${"%.0f".format(totalTarget)} total target",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { totalSaved / totalTarget },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Primary,
                        trackColor = SurfaceContainerHighest
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${"%.0f".format(totalSaved / totalTarget * 100)}% saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary
                        )
                        Text(
                            "${goals.size} active goals",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            Text("Active Goals", style = MaterialTheme.typography.titleMedium, color = OnSurface)

            goals.forEach { goal ->
                GoalCard(goal, navController)
            }

            // Expanded add goal button (semi-modal style card)
            Surface(
                onClick = { navController.navigate(Screen.AddGoal.route) },
                shape = RoundedCornerShape(12.dp),
                color = Primary.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("New Savings Goal", style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                        Text("Set a target, choose an icon, and track progress", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun GoalCard(goal: GoalData, navController: NavController) {
    val progress = goal.current / goal.target
    LedgerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { navController.navigate(Screen.GoalDetails.createRoute(1L)) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(goal.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(goal.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text("Due ${goal.deadline}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "$${"%.0f".format(goal.current)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "of $${"%.0f".format(goal.target)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = goal.color,
                    trackColor = SurfaceContainerHighest
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${"%.0f".format(progress * 100)}% complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                    Text(
                        "+$${"%.0f".format(goal.monthlyDeposit)}/mo",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary
                    )
                }
            }
        }
    }
}
