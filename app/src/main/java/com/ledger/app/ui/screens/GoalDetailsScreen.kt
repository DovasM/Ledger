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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailsScreen(navController: NavController, goalId: Long) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "History", "Milestones")
    val goalColor = Color(0xFF00513F)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goal Details", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.EditGoal.createRoute(goalId)) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit goal")
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
            // Hero card with icon
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(goalColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Column {
                            Text("EMERGENCY FUND", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(
                                "$6,500",
                                style = MaterialTheme.typography.displaySmall,
                                color = OnSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text("of $10,000 target", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { 0.65f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = goalColor,
                        trackColor = SurfaceContainerHighest
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("65% complete", style = MaterialTheme.typography.labelSmall, color = Primary)
                        Text("Due Oct 2026", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceContainerLow,
                contentColor = Primary
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            when (selectedTab) {
                0 -> OverviewTab()
                1 -> HistoryTab()
                2 -> MilestonesTab()
            }
        }
    }
}

@Composable
private fun OverviewTab() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Key stats
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Goal Statistics", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                GoalStatRow("Monthly contribution", "$500.00")
                GoalStatRow("Average monthly deposit", "$483.33")
                GoalStatRow("Months remaining", "7")
                GoalStatRow("Est. completion", "Oct 2026")
                GoalStatRow("Amount remaining", "$3,500.00")
                GoalStatRow("Projected on track", "Yes ✓")
            }
        }

        // Add contribution button
        Button(
            onClick = { /* add contribution */ },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Contribution", style = MaterialTheme.typography.labelLarge)
        }

        OutlinedButton(
            onClick = { /* withdraw */ },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(Icons.Filled.Remove, contentDescription = null, tint = Tertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Withdraw Funds", style = MaterialTheme.typography.labelLarge, color = Tertiary)
        }
    }
}

@Composable
private fun HistoryTab() {
    data class HistoryEntry(val date: String, val amount: Float, val note: String)
    val history = listOf(
        HistoryEntry("Apr 1, 2026", 500f, "Monthly deposit"),
        HistoryEntry("Mar 1, 2026", 500f, "Monthly deposit"),
        HistoryEntry("Feb 3, 2026", 650f, "Tax refund bonus"),
        HistoryEntry("Jan 1, 2026", 500f, "Monthly deposit"),
        HistoryEntry("Dec 2, 2025", 500f, "Monthly deposit"),
        HistoryEntry("Nov 1, 2025", 350f, "Partial contribution"),
        HistoryEntry("Oct 1, 2025", 500f, "Monthly deposit"),
        HistoryEntry("Sep 1, 2025", 500f, "Monthly deposit"),
    )

    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Contribution History", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Spacer(Modifier.height(8.dp))
            history.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(entry.note, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Text(entry.date, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Text(
                        "+$${"%.2f".format(entry.amount)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (history.indexOf(entry) < history.size - 1) {
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun MilestonesTab() {
    data class Milestone(val label: String, val amount: Float, val target: Float, val reached: Boolean)
    val milestones = listOf(
        Milestone("Getting Started", 1000f, 1000f, true),
        Milestone("25% Complete", 2500f, 2500f, true),
        Milestone("Halfway There", 5000f, 5000f, true),
        Milestone("75% Complete", 7500f, 7500f, false),
        Milestone("Goal Reached!", 10000f, 10000f, false),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Milestones", style = MaterialTheme.typography.titleMedium, color = OnSurface)
        milestones.forEach { milestone ->
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (milestone.reached) Primary else SurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (milestone.reached) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (milestone.reached) Color.White else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            milestone.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (milestone.reached) OnSurface else OnSurfaceVariant
                        )
                        Text(
                            "$${"%.0f".format(milestone.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                    if (milestone.reached) {
                        Surface(
                            color = Primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Reached",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
