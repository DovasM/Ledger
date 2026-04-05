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
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGoalScreen(navController: NavController, goalId: Long) {
    var name by remember { mutableStateOf("Emergency Fund") }
    var targetAmount by remember { mutableStateOf("10000") }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var selectedDeadline by remember { mutableStateOf("Oct 2026") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Goal") },
            text = { Text("This goal and all its history will be permanently deleted.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { navController.popBackStack(); navController.popBackStack() }) {
                    Text("Delete", color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
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
            // Photo section
            Text("Goal Photo", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (photoUri != null) goalColors[selectedColorIndex].copy(alpha = 0.2f) else SurfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUri != null) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (photoUri != null) "Photo selected" else "No photo selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (photoUri != null) OnSurface else OnSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { imagePicker.launch("image/*") },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (photoUri != null) "Change Photo" else "Upload Photo", color = Primary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            LedgerTextField(
                value = name, onValueChange = { name = it },
                label = "Goal Name", modifier = Modifier.fillMaxWidth()
            )

            LedgerTextField(
                value = targetAmount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) targetAmount = v },
                label = "Target Amount",
                leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = OnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth()
            )

            // Deadline
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Target Date", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(selectedDeadline.ifBlank { "Pick a deadline" }, color = OnSurface, style = MaterialTheme.typography.bodyLarge)
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
                items(goalIcons.indices.toList()) { i ->
                    val selected = i == selectedIconIndex
                    Box(
                        modifier = Modifier
                            .size(48.dp).clip(CircleShape)
                            .background(if (selected) goalColors[selectedColorIndex] else SurfaceContainerHighest)
                            .clickable { selectedIconIndex = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(goalIcons[i], contentDescription = null,
                            tint = if (selected) Color.White else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Color picker
            Text("Color", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                goalColors.forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp).clip(CircleShape).background(color)
                            .border(if (i == selectedColorIndex) 3.dp else 0.dp, OnSurface, CircleShape)
                            .clickable { selectedColorIndex = i }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Changes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
