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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.BudgetViewModel
import com.ledger.app.ui.viewmodel.CategoryViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditBudgetScreen(
    navController: NavController,
    budgetId: String? = null,
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val isEdit = budgetId != null
    val budgetState by budgetViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()

    val existingBudget = if (isEdit) budgetState.budgets.find { it.id == budgetId } else null

    val expenseCategories = categoryState.categories.filter { it.isExpense }

    var selectedCategoryIndex by remember(existingBudget, expenseCategories) {
        mutableStateOf(
            if (existingBudget != null) expenseCategories.indexOfFirst { it.id == existingBudget.categoryId }.coerceAtLeast(0)
            else 0
        )
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var amount by remember(existingBudget) { mutableStateOf(existingBudget?.limitAmount?.toString() ?: "") }
    var selectedPeriod by remember(existingBudget) { mutableStateOf(existingBudget?.period?.replaceFirstChar { it.uppercase() } ?: "Monthly") }
    var rollover by remember { mutableStateOf(false) }
    var alertAt by remember(existingBudget) {
        mutableStateOf(
            existingBudget?.alertThreshold?.toInt()?.toString() ?: "80"
        )
    }
    var showCalc by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectedCategory = expenseCategories.getOrNull(selectedCategoryIndex)

    val isAmountValid = amount.toDoubleOrNull()?.let { it > 0 } ?: false
    val isCategoryValid = !isEdit && selectedCategory != null
    val periods = listOf("Monthly", "Weekly", "Yearly")
    val color = selectedCategory?.let { colorHexToColor(it.colorHex) } ?: Primary
    val icon = selectedCategory?.let { iconNameToVector(it.iconName) } ?: Icons.Filled.Category

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Budget") },
            text = { Text("Delete this budget? Your transaction history will not be affected.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    budgetViewModel.deleteBudget(budgetId!!) { navController.popBackStack() }
                }) { Text("Delete", color = Tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
            }
        )
    }

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = amount, accentColor = color,
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
                Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedCategory?.name ?: "Select category", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(selectedPeriod, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Text(
                        if (amount.isBlank()) "\$—" else "\$$amount",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                        color = color, fontWeight = FontWeight.Bold
                    )
                }
            }

            // Category — only for new budgets
            if (!isEdit) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Category", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        if (expenseCategories.isEmpty()) {
                            Text(
                                "No expense categories found. Add categories in Categories management first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (showErrors) MaterialTheme.colorScheme.error else OnSurfaceVariant
                            )
                        } else {
                            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                                LedgerTextField(
                                    value = selectedCategory?.name ?: "Select category",
                                    onValueChange = {},
                                    label = "Select Category",
                                    leadingIcon = { Icon(icon, null, tint = color) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                                    expenseCategories.forEachIndexed { idx, cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name) },
                                            onClick = { selectedCategoryIndex = idx; categoryMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Limit amount
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Limit", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    val amountError = showErrors && !isAmountValid
                    LedgerTextField(
                        value = amount,
                        onValueChange = { v ->
                            if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) amount = v
                        },
                        label = "Budget limit",
                        leadingIcon = { Icon(Icons.Filled.AttachMoney, null, tint = if (amountError) Error else color) },
                        trailingIcon = {
                            IconButton(onClick = { showCalc = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Calculate, null, tint = if (amountError) Error else color, modifier = Modifier.size(20.dp))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = amountError,
                        supportingText = if (amountError) "Budget amount must be greater than 0" else null,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        Switch(checked = rollover, onCheckedChange = { rollover = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = color))
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    Text("Alert me at", style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.padding(top = 8.dp))
                    val presets = listOf("70", "80", "90", "100")
                    val isCustom = alertAt !in presets
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        presets.forEach { pct ->
                            FilterChip(selected = alertAt == pct, onClick = { alertAt = pct },
                                label = { Text("$pct%", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = Color.White))
                        }
                        FilterChip(
                            selected = isCustom,
                            onClick = { if (!isCustom) alertAt = "" },
                            label = { Text("Custom", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = Color.White)
                        )
                    }
                    if (isCustom || alertAt.isEmpty()) {
                        LedgerTextField(
                            value = alertAt,
                            onValueChange = { v ->
                                if (v.all { it.isDigit() } && (v.toIntOrNull() ?: 0) <= 100) alertAt = v
                            },
                            label = "Alert at % (1–100)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Button(
                onClick = {
                    showErrors = true
                    val categoryId = selectedCategory?.id
                    val limitAmount = amount.toDoubleOrNull()
                    val canSave = isAmountValid && limitAmount != null && (isEdit || categoryId != null)
                    if (canSave) {
                        val period = selectedPeriod.lowercase()
                        val threshold = alertAt.toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: 80.0
                        if (isEdit && budgetId != null) {
                            budgetViewModel.updateBudget(budgetId, limitAmount!!, period, threshold) { navController.popBackStack() }
                        } else if (categoryId != null) {
                            budgetViewModel.createBudget(categoryId, limitAmount!!, period, threshold) { navController.popBackStack() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isEdit) "Save Changes" else "Create Budget", style = MaterialTheme.typography.labelLarge)
            }

            if (isEdit) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Tertiary.copy(alpha = 0.5f)))
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Budget")
                }
            }
        }
    }
}
