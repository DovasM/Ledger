package com.ledger.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var selectedPhotoIndex by remember { mutableStateOf(0) }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDeadline by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showCalc by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val isNameValid = name.isNotBlank()
    val isAmountValid = targetAmount.toDoubleOrNull()?.let { it > 0 } ?: false

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> photoUri = uri }

    val goalIcons = listOf(
        Icons.Filled.Home, Icons.Filled.Flight, Icons.Filled.DirectionsCar,
        Icons.Filled.Laptop, Icons.Filled.School, Icons.Filled.HealthAndSafety,
        Icons.Filled.Savings, Icons.Filled.BeachAccess, Icons.Filled.Diamond,
        Icons.Filled.FamilyRestroom, Icons.Filled.Celebration, Icons.Filled.BusinessCenter,
    )

    val goalColors = listOf(
        Color(0xFF00513F), Color(0xFF1565C0), Color(0xFF6A1B9A),
        Color(0xFF920009), Color(0xFFE65100), Color(0xFF00838F),
        Color(0xFF558B2F), Color(0xFFF9A825), Color(0xFF4E342E),
    )

    // Aggregated wealth mock data
    val totalWealth = 24530.80f

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = targetAmount, accentColor = Primary,
            onDismiss = { showCalc = false },
            onConfirm = { result -> targetAmount = result; showCalc = false }
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
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Goal", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
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
            // Photo upload
            Text("Goal Photo", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (photoUri != null) goalColors[selectedColorIndex].copy(alpha = 0.2f) else SurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (photoUri != null) Icons.Filled.CheckCircle else Icons.Filled.Image,
                            contentDescription = null,
                            tint = if (photoUri != null) Primary else OnSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (photoUri != null) "Photo selected" else "Optional cover photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (photoUri != null) OnSurface else OnSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (photoUri != null) "Change" else "Upload Photo", color = Primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Aggregated wealth banner
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AGGREGATED WEALTH", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(
                            "$${"%.2f".format(totalWealth)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                }
            }

            // Goal name
            LedgerTextField(
                value = name,
                onValueChange = { name = it },
                label = "Goal Name",
                placeholder = "e.g. Emergency Fund, Vacation",
                modifier = Modifier.fillMaxWidth(),
                isError = showErrors && !isNameValid,
                supportingText = if (showErrors && !isNameValid) "Goal name is required" else null
            )

            // Target amount — tap to open calculator
            Surface(onClick = { showCalc = true }, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Target Amount", style = MaterialTheme.typography.labelMedium,
                        color = if (showErrors && !isAmountValid) Error else OnSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        Text("$", style = MaterialTheme.typography.headlineMedium,
                            color = if (showErrors && !isAmountValid) Error else OnSurfaceVariant,
                            fontWeight = FontWeight.Light)
                        Text(
                            if (targetAmount.isBlank()) "0" else targetAmount,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp),
                            color = if (showErrors && !isAmountValid) Error else if (targetAmount.isBlank()) OutlineVariant else OnSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Calculate, null,
                            tint = if (showErrors && !isAmountValid) Error else OnSurfaceVariant,
                            modifier = Modifier.size(14.dp))
                        Text(
                            if (showErrors && !isAmountValid) "Target amount must be greater than 0" else "Tap to enter target amount",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showErrors && !isAmountValid) Error else OnSurfaceVariant
                        )
                    }
                }
            }

            // Deadline picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Target Date", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedDeadline.isBlank()) "Pick a deadline" else selectedDeadline,
                        color = if (selectedDeadline.isBlank()) OnSurfaceVariant else OnSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Goal icon picker
            Text("Goal Icon", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(goalIcons.indices.toList()) { i ->
                    val selected = i == selectedPhotoIndex
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selected) goalColors[selectedColorIndex] else SurfaceContainerHighest)
                            .clickable { selectedPhotoIndex = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            goalIcons[i], contentDescription = null,
                            tint = if (selected) Color.White else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Color picker
            Text("Color", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                goalColors.forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (i == selectedColorIndex) 3.dp else 0.dp, OnSurface, CircleShape)
                            .clickable { selectedColorIndex = i }
                    )
                }
            }

            // Preview
            Text("Preview", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(goalColors[selectedColorIndex]),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(goalIcons[selectedPhotoIndex], contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (name.isBlank()) "Goal Name" else name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (name.isBlank()) OnSurfaceVariant else OnSurface
                        )
                        Text(
                            buildString {
                                if (targetAmount.isNotBlank()) append("Target: $$targetAmount")
                                if (selectedDeadline.isNotBlank()) {
                                    if (targetAmount.isNotBlank()) append(" · ")
                                    append("Due $selectedDeadline")
                                }
                                if (targetAmount.isBlank() && selectedDeadline.isBlank()) append("Set amount and deadline")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showErrors = true; if (isNameValid && isAmountValid) navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create Goal", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
