package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.GoalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGoalScreen(
    navController: NavController,
    goalId: String,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goal = state.goals.find { it.id == goalId }

    var name by remember(goal) { mutableStateOf(goal?.name ?: "") }
    var selectedDeadline by remember(goal) { mutableStateOf(goal?.deadline ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showContribDialog by remember { mutableStateOf(false) }
    var contribAmount by remember { mutableStateOf("") }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Goal") },
            text = { Text("This goal and all its history will be permanently deleted.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoal(goalId) { navController.popBackStack() }
                }) { Text("Delete", color = Tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
            }
        )
    }

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
                        viewModel.addContribution(goalId, amount) { showContribDialog = false; contribAmount = "" }
                    }
                }) { Text("Add", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showContribDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.timeInMillis = millis
                        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                        selectedDeadline = "${months[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}"
                    }
                    showDatePicker = false
                }) { Text("OK", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = OnSurfaceVariant) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Goal", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Tertiary)
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
            if (goal == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                // Current progress info
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current Progress", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant)
                        Text(
                            "$${"%.2f".format(goal.currentAmount)} of $${"%.2f".format(goal.targetAmount)}",
                            style = MaterialTheme.typography.titleMedium, color = OnSurface
                        )
                        val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Primary, trackColor = SurfaceContainerHighest
                        )
                    }
                }

                LedgerTextField(
                    value = name, onValueChange = { name = it },
                    label = "Goal Name", modifier = Modifier.fillMaxWidth()
                )

                // Deadline picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target Date", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            selectedDeadline.ifBlank { "Pick a deadline (optional)" },
                            color = if (selectedDeadline.isBlank()) OnSurfaceVariant else OnSurface,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Button(
                    onClick = { showContribDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Contribution", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Goal")
                }
            }
        }
    }
}
