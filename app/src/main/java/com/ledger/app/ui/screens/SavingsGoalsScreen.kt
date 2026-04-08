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
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.GoalViewModel
import uniffi.ledger.SavingsGoal

private val goalIconList = listOf(
    Icons.Filled.Home, Icons.Filled.Flight, Icons.Filled.DirectionsCar,
    Icons.Filled.Laptop, Icons.Filled.School, Icons.Filled.HealthAndSafety,
    Icons.Filled.Savings, Icons.Filled.BeachAccess, Icons.Filled.Diamond,
    Icons.Filled.FamilyRestroom, Icons.Filled.Celebration, Icons.Filled.BusinessCenter,
)

private val goalColorList = listOf(
    Color(0xFF00513F), Color(0xFF1565C0), Color(0xFF6A1B9A),
    Color(0xFF920009), Color(0xFFE65100), Color(0xFF00838F),
    Color(0xFF558B2F), Color(0xFFF9A825), Color(0xFF4E342E),
)

private fun goalColor(goal: SavingsGoal) = goalColorList[goal.id.hashCode().let { if (it < 0) -it else it } % goalColorList.size]
private fun goalIcon(goal: SavingsGoal)  = goalIconList [goal.id.hashCode().let { if (it < 0) -it else it } % goalIconList.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    navController: NavController,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.load() }
    val goals = state.goals

    val totalSaved  = goals.sumOf { it.currentAmount }
    val totalTarget = goals.sumOf { it.targetAmount }

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
                onClick = { navController.navigate(Screen.AddGoal.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add goal")
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
            if (totalTarget > 0) {
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
                            progress = { (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Primary,
                            trackColor = SurfaceContainerHighest
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${"%.0f".format(totalSaved / totalTarget * 100)}% saved",
                                style = MaterialTheme.typography.labelSmall, color = Primary
                            )
                            Text(
                                "${goals.size} active goal${if (goals.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text("Active Goals", style = MaterialTheme.typography.titleMedium, color = OnSurface)

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (goals.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No goals yet. Create one below.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                goals.forEach { goal ->
                    GoalCard(goal, navController)
                }
            }

            // Add goal CTA
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
private fun GoalCard(goal: SavingsGoal, navController: NavController) {
    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val color = goalColor(goal)
    val icon  = goalIcon(goal)

    LedgerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { navController.navigate(Screen.GoalDetails.createRoute(goal.id)) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    if (goal.deadline != null) {
                        Text("Due ${goal.deadline}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "$${"%.0f".format(goal.currentAmount)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "of $${"%.0f".format(goal.targetAmount)}",
                        style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = color, trackColor = SurfaceContainerHighest
                )
                Text(
                    "${"%.0f".format(progress * 100)}% complete",
                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                )
            }
        }
    }
}
