package com.ledger.app.ui.screens

import com.ledger.app.ui.util.rememberMoneyFormatter

import com.ledger.app.ui.util.asAmountInput
import com.ledger.app.ui.util.toCents
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
    val money = rememberMoneyFormatter()
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
    var isOverall by remember(existingBudget) { mutableStateOf(isEdit && existingBudget?.categoryId == null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var amount by remember(existingBudget) { mutableStateOf(existingBudget?.limitAmountCents?.asAmountInput() ?: "") }
    var selectedPeriod by remember(existingBudget) { mutableStateOf(existingBudget?.period?.replaceFirstChar { it.uppercase() } ?: "Monthly") }
    // Was a dead toggle: the switch existed but nothing stored it. Now wired to Budget.carryOver.
    var rollover by remember(existingBudget) { mutableStateOf(existingBudget?.carryOver ?: false) }
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
                        Text(
                            if (isOverall) "Everything" else selectedCategory?.name ?: "Select category",
                            style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold
                        )
                        Text(selectedPeriod, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Text(
                        if (amount.isBlank()) "—" else money.ofUnits(amount.toDoubleOrNull() ?: 0.0),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                        color = color, fontWeight = FontWeight.Bold
                    )
                }
            }

            // Rust rejects a second overall budget; without this the refusal was invisible and the
            // screen just appeared to do nothing.
            budgetState.error?.let { message ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = Tertiary)
                    }
                }
            }

            // An overall budget (no category) is the only way to say "at most X in total" — the
            // daily allowance comes from it. Category budgets pace one domain and are never summed
            // into a total, because that produced a number nobody chose.
            if (!isEdit) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Scope", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = isOverall,
                                onClick = { isOverall = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text("Overall") }
                            SegmentedButton(
                                selected = !isOverall,
                                onClick = { isOverall = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text("Category") }
                        }
                        Text(
                            if (isOverall) "Caps everything you spend, and drives the daily allowance shown on the widget."
                            else "Caps one category. Shown as pacing, but it does not set the daily allowance.",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Category — only for new category-scoped budgets
            if (!isEdit && !isOverall) {
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
                    val categoryId = if (isOverall) null else selectedCategory?.id
                    val limitAmount = amount.toDoubleOrNull()
                    val canSave = isAmountValid && limitAmount != null && (isEdit || isOverall || categoryId != null)
                    if (canSave) {
                        val period = selectedPeriod.lowercase()
                        val threshold = alertAt.toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: 80.0
                        if (isEdit && budgetId != null) {
                            // A budget can now be scoped to a wallet as well as a category; this
                            // screen still only edits category budgets, so carry the rest through
                            // untouched rather than blanking them.
                            budgetViewModel.updateBudget(
                                budgetId,
                                existingBudget?.categoryId,
                                existingBudget?.walletId,
                                limitAmount!!.toCents(), period, threshold,
                                rollover
                            ) { navController.popBackStack() }
                        } else {
                            budgetViewModel.createBudget(categoryId, null, limitAmount!!.toCents(), period, threshold, rollover) {
                                navController.popBackStack()
                            }
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
