package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.RecurringViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringScreen(
    navController: NavController,
    viewModel: RecurringViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val walletState   by walletViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedFrequency by remember { mutableStateOf("monthly") }
    val frequencies = listOf("daily", "weekly", "biweekly", "monthly", "yearly")
    val frequencyLabels = listOf("Daily", "Weekly", "Bi-weekly", "Monthly", "Yearly")

    var selectedWalletIndex by remember { mutableStateOf(0) }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var nextDate by remember { mutableStateOf("") }

    var showCalc by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val accentColor = if (isExpense) Tertiary else Primary
    val isAmountValid = amount.toDoubleOrNull()?.let { it > 0 } ?: false
    val isTitleValid = title.isNotBlank()
    val isNextDateValid = nextDate.isNotBlank()
    val isFormValid = isAmountValid && isTitleValid && isNextDateValid && walletState.wallets.isNotEmpty()

    val relevantCategories = categoryState.categories.filter { it.isExpense == isExpense }

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = amount, accentColor = accentColor,
            onDismiss = { showCalc = false },
            onConfirm = { result -> amount = result; showCalc = false }
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
                        nextDate = "%04d-%02d-%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        )
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
                title = { Text("Add Recurring", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.Close, null) }
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = isExpense, onClick = { isExpense = true; selectedCategoryIndex = 0 },
                    label = { Text("Expense", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary)
                )
                FilterChip(
                    selected = !isExpense, onClick = { isExpense = false; selectedCategoryIndex = 0 },
                    label = { Text("Income", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                )
            }

            LedgerAmountField(
                amount = amount,
                onAmountChange = { amount = it },
                onCalculatorOpen = { showCalc = true },
                prefix = if (isExpense) "-$" else "+$",
                accentColor = accentColor,
                showError = showErrors && !isAmountValid
            )

            LedgerTextField(
                value = title, onValueChange = { title = it },
                label = "Title", modifier = Modifier.fillMaxWidth(),
                isError = showErrors && !isTitleValid,
                supportingText = if (showErrors && !isTitleValid) "Title is required" else null
            )

            // Frequency chips
            Text("Frequency", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                frequencies.take(3).forEachIndexed { i, f ->
                    FilterChip(
                        selected = selectedFrequency == f, onClick = { selectedFrequency = f },
                        label = { Text(frequencyLabels[i], style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor, selectedLabelColor = if (isExpense) OnTertiary else OnPrimary)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                frequencies.drop(3).forEachIndexed { i, f ->
                    FilterChip(
                        selected = selectedFrequency == f, onClick = { selectedFrequency = f },
                        label = { Text(frequencyLabels[i + 3], style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor, selectedLabelColor = if (isExpense) OnTertiary else OnPrimary)
                    )
                }
            }

            // Next date picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Next Date", style = MaterialTheme.typography.titleSmall, color = if (showErrors && !isNextDateValid) Error else OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = if (showErrors && !isNextDateValid) ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp
                    ) else ButtonDefaults.outlinedButtonBorder
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = if (showErrors && !isNextDateValid) Error else accentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        nextDate.ifBlank { "Pick next occurrence date" },
                        color = if (showErrors && !isNextDateValid) Error else if (nextDate.isBlank()) OnSurfaceVariant else OnSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Category dropdown
            if (relevantCategories.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    LedgerTextField(
                        value = relevantCategories.getOrNull(selectedCategoryIndex)?.name ?: "Select Category",
                        onValueChange = {},
                        label = "Category",
                        leadingIcon = { Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        relevantCategories.forEachIndexed { idx, cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = { selectedCategoryIndex = idx; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            // Wallet dropdown
            if (walletState.wallets.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = walletMenuExpanded, onExpandedChange = { walletMenuExpanded = it }) {
                    LedgerTextField(
                        value = walletState.wallets.getOrNull(selectedWalletIndex)?.name ?: "Select Wallet",
                        onValueChange = {},
                        label = "Wallet",
                        leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, null, tint = OnSurfaceVariant) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = walletMenuExpanded, onDismissRequest = { walletMenuExpanded = false }) {
                        walletState.wallets.forEachIndexed { idx, wallet ->
                            DropdownMenuItem(
                                text = { Text(wallet.name) },
                                onClick = { selectedWalletIndex = idx; walletMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    showErrors = true
                    val amountVal = amount.toDoubleOrNull()
                    val wallet = walletState.wallets.getOrNull(selectedWalletIndex)
                    val categoryName = relevantCategories.getOrNull(selectedCategoryIndex)?.name ?: ""
                    if (isFormValid && amountVal != null && wallet != null) {
                        viewModel.createRecurring(
                            title = title,
                            amount = amountVal,
                            category = categoryName,
                            walletId = wallet.id,
                            isIncome = !isExpense,
                            frequency = selectedFrequency,
                            nextDate = nextDate
                        ) { navController.popBackStack() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Repeat, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Recurring Transaction", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
