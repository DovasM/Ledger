package com.ledger.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.GoalImageStore
import com.ledger.app.ui.viewmodel.GoalViewModel

private val editGoalIcons = listOf(
    Icons.Filled.Home, Icons.Filled.Flight, Icons.Filled.DirectionsCar,
    Icons.Filled.Laptop, Icons.Filled.School, Icons.Filled.HealthAndSafety,
    Icons.Filled.Savings, Icons.Filled.BeachAccess, Icons.Filled.Diamond,
    Icons.Filled.FamilyRestroom, Icons.Filled.Celebration, Icons.Filled.BusinessCenter,
)

private val editGoalColors = listOf(
    Color(0xFF00513F), Color(0xFF1565C0), Color(0xFF6A1B9A),
    Color(0xFF920009), Color(0xFFE65100), Color(0xFF00838F),
    Color(0xFF558B2F), Color(0xFFF9A825), Color(0xFF4E342E),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGoalScreen(
    navController: NavController,
    goalId: String,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goal = state.goals.find { it.id == goalId }

    var name by remember(goal) { mutableStateOf(goal?.name ?: "") }
    var targetAmount by remember(goal) { mutableStateOf(goal?.targetAmount?.let { "%.2f".format(java.util.Locale.US, it) } ?: "") }
    var selectedDeadline by remember(goal) { mutableStateOf(goal?.deadline ?: "") }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showContribDialog by remember { mutableStateOf(false) }
    var contribAmount by remember { mutableStateOf("") }
    var showCalc by remember { mutableStateOf(false) }

    // Image
    var pendingImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val existingBitmap = remember(goal?.name) {
        goal?.name?.let { GoalImageStore.loadBitmap(context, it)?.asImageBitmap() }
    }
    val pendingBitmap = remember(pendingImageUri) {
        pendingImageUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }
    }
    val displayBitmap = pendingBitmap?.asImageBitmap() ?: existingBitmap
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingImageUri = uri
    }

    val isNameValid = name.isNotBlank()
    val isAmountValid = targetAmount.toDoubleOrNull()?.let { it > 0 } ?: false

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = targetAmount, accentColor = Primary,
            onDismiss = { showCalc = false },
            onConfirm = { result -> targetAmount = result; showCalc = false }
        )
    }

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
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                // Current progress card
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
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Primary, trackColor = SurfaceContainerHighest
                        )
                        Text(
                            "${"%.0f".format(progress * 100)}% complete",
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                        )
                    }
                }

                // Goal name
                LedgerTextField(
                    value = name, onValueChange = { name = it },
                    label = "Goal Name", modifier = Modifier.fillMaxWidth()
                )

                // Target amount
                LedgerAmountField(
                    amount = targetAmount,
                    onAmountChange = { targetAmount = it },
                    onCalculatorOpen = { showCalc = true },
                    showError = false
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
                        if (selectedDeadline.isNotBlank()) {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { selectedDeadline = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Photo
                Text("Photo", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerHighest)
                            .clickable { imageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (displayBitmap != null) {
                            Image(
                                bitmap = displayBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(Icons.Filled.AddPhotoAlternate, null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (displayBitmap != null) "Photo set — tap to change" else "Tap to add a photo",
                            style = MaterialTheme.typography.bodyMedium, color = OnSurface
                        )
                        if (displayBitmap != null) {
                            TextButton(onClick = {
                                pendingImageUri = null
                                GoalImageStore.delete(context, goal.name)
                            }, contentPadding = PaddingValues(0.dp)) {
                                Text("Remove photo", color = Tertiary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Icon picker
                Text("Goal Icon", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.heightIn(max = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(editGoalIcons.indices.toList()) { i ->
                        val sel = i == selectedIconIndex
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(if (sel) editGoalColors[selectedColorIndex] else SurfaceContainerHighest)
                                .clickable { selectedIconIndex = i },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(editGoalIcons[i], null,
                                tint = if (sel) Color.White else OnSurfaceVariant,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                }

                // Color picker
                Text("Color", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    editGoalColors.forEachIndexed { i, color ->
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                                .border(if (i == selectedColorIndex) 3.dp else 0.dp, OnSurface, CircleShape)
                                .clickable { selectedColorIndex = i }
                        )
                    }
                }

                // Add contribution
                OutlinedButton(
                    onClick = { showContribDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Contribution")
                }

                // Save
                Button(
                    onClick = {
                        val amount = targetAmount.toDoubleOrNull()
                        if (isNameValid && isAmountValid && amount != null) {
                            val capturedUri = pendingImageUri
                            val capturedName = name
                            viewModel.updateGoal(
                                id = goalId,
                                name = capturedName,
                                targetAmount = amount,
                                deadline = selectedDeadline.ifBlank { null }
                            ) {
                                if (capturedUri != null) GoalImageStore.save(context, capturedName, capturedUri)
                                navController.popBackStack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(6.dp),
                    enabled = isNameValid && isAmountValid
                ) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Changes", style = MaterialTheme.typography.labelLarge)
                }

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
