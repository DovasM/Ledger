package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.GoalViewModel
import uniffi.ledger.SavingsGoal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailsScreen(
    navController: NavController,
    goalId: String,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goal = state.goals.find { it.id == goalId }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Milestones")
    var showContribDialog by remember { mutableStateOf(false) }
    var contribAmount by remember { mutableStateOf("") }

    val goalColor = goal?.let { g ->
        val goalColorList = listOf(
            Color(0xFF00513F), Color(0xFF1565C0), Color(0xFF6A1B9A),
            Color(0xFF920009), Color(0xFFE65100), Color(0xFF00838F),
            Color(0xFF558B2F), Color(0xFFF9A825), Color(0xFF4E342E),
        )
        goalColorList[g.id.hashCode().let { if (it < 0) -it else it } % goalColorList.size]
    } ?: Primary

    val goalIconDisplay = goal?.let { g ->
        val goalIconList = listOf(
            Icons.Filled.Home, Icons.Filled.Flight, Icons.Filled.DirectionsCar,
            Icons.Filled.Laptop, Icons.Filled.School, Icons.Filled.HealthAndSafety,
            Icons.Filled.Savings, Icons.Filled.BeachAccess, Icons.Filled.Diamond,
            Icons.Filled.FamilyRestroom, Icons.Filled.Celebration, Icons.Filled.BusinessCenter,
        )
        goalIconList[g.id.hashCode().let { if (it < 0) -it else it } % goalIconList.size]
    } ?: Icons.Filled.Savings

    if (showContribDialog) {
        AlertDialog(
            onDismissRequest = { showContribDialog = false },
            title = { Text("Add Contribution") },
            text = {
                LedgerTextField(
                    value = contribAmount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) contribAmount = v },
                    label = "Amount",
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, null, tint = OnSurfaceVariant) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = contribAmount.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        viewModel.addContribution(goalId, amount) {
                            showContribDialog = false
                            contribAmount = ""
                        }
                    }
                }) { Text("Add", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showContribDialog = false; contribAmount = "" }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            }
        )
    }

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
        if (goal == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero card
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
                                Icon(goalIconDisplay, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Column {
                                Text(
                                    goal.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                )
                                Text(
                                    "$${"%.2f".format(goal.currentAmount)}",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = OnSurface, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "of $${"%.0f".format(goal.targetAmount)} target",
                                    style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant
                                )
                            }
                        }
                        val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = goalColor, trackColor = SurfaceContainerHighest
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${"%.0f".format(progress * 100)}% complete",
                                style = MaterialTheme.typography.labelSmall, color = Primary
                            )
                            if (goal.deadline != null) {
                                Text("Due ${goal.deadline}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
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
                    0 -> GoalOverviewTab(goal, goalColor, onAddContribution = { showContribDialog = true })
                    1 -> GoalMilestonesTab(goal, goalColor)
                }
            }
        }
    }
}

@Composable
private fun GoalOverviewTab(
    goal: SavingsGoal,
    goalColor: Color,
    onAddContribution: () -> Unit
) {
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Goal Statistics", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                GoalStatRow("Target amount", "$${"%.2f".format(goal.targetAmount)}")
                GoalStatRow("Current amount", "$${"%.2f".format(goal.currentAmount)}")
                GoalStatRow("Remaining", "$${"%.2f".format(remaining)}")
                if (goal.deadline != null) {
                    GoalStatRow("Deadline", goal.deadline!!)
                }
                GoalStatRow(
                    "Status",
                    if (goal.currentAmount >= goal.targetAmount) "Completed ✓" else "In progress"
                )
            }
        }

        Button(
            onClick = onAddContribution,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = goalColor),
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Contribution", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GoalMilestonesTab(goal: SavingsGoal, goalColor: Color) {
    val milestonePercents = listOf(0.25, 0.5, 0.75, 1.0)
    val milestoneLabels   = listOf("25% Complete", "Halfway There", "75% Complete", "Goal Reached!")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Milestones", style = MaterialTheme.typography.titleMedium, color = OnSurface)
        milestonePercents.forEachIndexed { i, pct ->
            val targetAmt = goal.targetAmount * pct
            val reached   = goal.currentAmount >= targetAmt
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp).clip(CircleShape)
                            .background(if (reached) goalColor else SurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (reached) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            null,
                            tint = if (reached) Color.White else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            milestoneLabels[i],
                            style = MaterialTheme.typography.titleSmall,
                            color = if (reached) OnSurface else OnSurfaceVariant
                        )
                        Text(
                            "$${"%.0f".format(targetAmt)}",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                    if (reached) {
                        Surface(
                            color = goalColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Reached",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = goalColor
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
