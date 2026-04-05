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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBudgetScreen(navController: NavController, categoryName: String? = null) {
    val isEdit = categoryName != null
    val categories = listOf("Housing", "Food & Dining", "Transport", "Entertainment", "Health", "Shopping", "Other")
    var selectedCategory by remember { mutableStateOf(categoryName ?: categories[0]) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(if (isEdit) "400" else "") }
    var rollover by remember { mutableStateOf(false) }
    var alertAt by remember { mutableStateOf("80") }
    val periods = listOf("Monthly", "Weekly", "Yearly")
    var selectedPeriod by remember { mutableStateOf("Monthly") }
    var showCalc by remember { mutableStateOf(false) }

    val categoryColors = mapOf(
        "Housing" to Color(0xFF00513F), "Food & Dining" to Color(0xFF1565C0),
        "Transport" to Color(0xFF6A1B9A), "Entertainment" to Color(0xFFE65100),
        "Health" to Color(0xFF920009), "Shopping" to Color(0xFF00838F), "Other" to Color(0xFF607D8B)
    )
    val categoryIcons = mapOf(
        "Housing" to Icons.Filled.Home, "Food & Dining" to Icons.Filled.Restaurant,
        "Transport" to Icons.Filled.DirectionsCar, "Entertainment" to Icons.Filled.Movie,
        "Health" to Icons.Filled.HealthAndSafety, "Shopping" to Icons.Filled.ShoppingCart,
        "Other" to Icons.Filled.Category
    )
    val color = categoryColors[selectedCategory] ?: Primary
    val icon = categoryIcons[selectedCategory] ?: Icons.Filled.Category

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = amount,
            accentColor = color,
            onDismiss = { showCalc = false },
            onConfirm = { result -> amount = result; showCalc = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Budget" else "New Budget", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            // Preview card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedCategory, style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(selectedPeriod, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Text(
                        if (amount.isBlank()) "\$—" else "\$$amount",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                        color = color, fontWeight = FontWeight.Bold
                    )
                }
            }

            // Category (only for new budgets)
            if (!isEdit) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Category", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                            LedgerTextField(
                                value = selectedCategory, onValueChange = {},
                                label = "Select Category",
                                leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; categoryMenuExpanded = false })
                                }
                            }
                        }
                    }
                }
            }

            // Limit amount — tap to open calculator
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Limit", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Surface(
                        onClick = { showCalc = true },
                        color = SurfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (amount.isBlank()) "Set amount…" else "\$$amount",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (amount.isBlank()) OnSurfaceVariant else color,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Filled.Calculate, contentDescription = "Open calculator", tint = color, modifier = Modifier.size(22.dp))
                        }
                    }
                    Text("Period", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        periods.forEach { p ->
                            FilterChip(
                                selected = selectedPeriod == p, onClick = { selectedPeriod = p },
                                label = { Text(p, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = Color.White)
                            )
                        }
                    }
                }
            }

            // Options
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Options", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rollover unused", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text("Carry unspent amount to next period", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                        Switch(
                            checked = rollover, onCheckedChange = { rollover = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = color)
                        )
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    Text("Alert me at", style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("70", "80", "90", "100").forEach { pct ->
                            FilterChip(
                                selected = alertAt == pct, onClick = { alertAt = pct },
                                label = { Text("$pct%", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = Color.White)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isEdit) "Save Changes" else "Create Budget", style = MaterialTheme.typography.labelLarge)
            }

            if (isEdit) {
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Tertiary.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Budget")
                }
            }
        }
    }
}
