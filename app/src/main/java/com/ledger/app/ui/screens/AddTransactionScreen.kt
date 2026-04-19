package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
import com.ledger.app.ui.viewmodel.TagViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    tagViewModel: TagViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val walletState    by walletViewModel.state.collectAsStateWithLifecycle()
    val tagState       by tagViewModel.state.collectAsStateWithLifecycle()
    val categoryState  by categoryViewModel.state.collectAsStateWithLifecycle()

    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val titleSuggestions = remember(txState.transactions) {
        txState.transactions.map { it.title }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var titleSuggestionsVisible by remember { mutableStateOf(false) }

    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var showCalc by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // selectedTags holds tag names (without leading #)
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var customTag by remember { mutableStateOf("") }

    var selectedWalletIndex by remember { mutableStateOf(0) }
    var walletMenuExpanded by remember { mutableStateOf(false) }

    val expenseCategoryNames = categoryState.categories.filter { it.isExpense }.map { it.name }
        .ifEmpty { listOf("Housing", "Food & Dining", "Transportation", "Entertainment", "Health", "Shopping", "Other") }
    val incomeCategoryNames = categoryState.categories.filter { !it.isExpense }.map { it.name }
        .ifEmpty { listOf("Salary", "Freelance", "Investments", "Other Income") }
    var selectedCategory by remember(isExpense, expenseCategoryNames, incomeCategoryNames) {
        mutableStateOf(if (isExpense) expenseCategoryNames[0] else incomeCategoryNames[0])
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull()
    val isAmountValid = amountValue != null && amountValue > 0
    val isWalletValid = walletState.wallets.isNotEmpty()
    val isFormValid = isAmountValid && isWalletValid

    val accentColor = if (isExpense) Tertiary else Primary

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK", color = accentColor) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = amount, accentColor = accentColor,
            onDismiss = { showCalc = false },
            onConfirm = { result -> amount = result; showCalc = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction", style = MaterialTheme.typography.headlineSmall) },
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Income / Expense toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = isExpense,
                    onClick = { isExpense = true; selectedCategory = expenseCategoryNames[0] },
                    label = { Text("Expense", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary)
                )
                FilterChip(
                    selected = !isExpense,
                    onClick = { isExpense = false; selectedCategory = incomeCategoryNames[0] },
                    label = { Text("Income", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                )
            }

            // Wallet selector — real wallets from DB
            if (walletState.wallets.isEmpty()) {
                Text(
                    if (showErrors) "No wallets found — add a wallet first" else "No wallets yet — add one in Wallets",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (showErrors) MaterialTheme.colorScheme.error else OnSurfaceVariant
                )
            } else {
                ExposedDropdownMenuBox(expanded = walletMenuExpanded, onExpandedChange = { walletMenuExpanded = it }) {
                    LedgerTextField(
                        value = walletState.wallets.getOrNull(selectedWalletIndex)?.name ?: "Select wallet",
                        onValueChange = {},
                        label = "From Wallet",
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

            LedgerAmountField(
                amount = amount,
                onAmountChange = { amount = it },
                onCalculatorOpen = { showCalc = true },
                prefix = if (isExpense) "-$" else "+$",
                accentColor = accentColor,
                showError = showErrors && !isAmountValid
            )

            // Category selector — shown first, most important field
            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                LedgerTextField(
                    value = selectedCategory, onValueChange = {},
                    label = "Category",
                    leadingIcon = { Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    val cats = if (isExpense) expenseCategoryNames else incomeCategoryNames
                    cats.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; categoryMenuExpanded = false })
                    }
                }
            }

            val filteredTitleSuggestions = remember(title, titleSuggestions) {
                if (title.isBlank()) titleSuggestions.take(6)
                else titleSuggestions.filter { it.contains(title, ignoreCase = true) && !it.equals(title, ignoreCase = true) }.take(6)
            }
            ExposedDropdownMenuBox(
                expanded = titleSuggestionsVisible && filteredTitleSuggestions.isNotEmpty(),
                onExpandedChange = {}
            ) {
                LedgerTextField(
                    value = title,
                    onValueChange = { title = it; titleSuggestionsVisible = true },
                    label = "Title (optional)", placeholder = "e.g. Monthly Rent, Netflix",
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = titleSuggestionsVisible && filteredTitleSuggestions.isNotEmpty(),
                    onDismissRequest = { titleSuggestionsVisible = false }
                ) {
                    filteredTitleSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { title = suggestion; titleSuggestionsVisible = false }
                        )
                    }
                }
            }

            LedgerTextField(
                value = selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                onValueChange = {},
                label = "Date",
                leadingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = OnSurfaceVariant) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.EditCalendar, null, tint = accentColor)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            LedgerTextField(
                value = note, onValueChange = { note = it },
                label = "Note (optional)", singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            // Tags
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tags", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                // Combine DB tags with any newly typed custom tags not yet in DB
                val dbTagNames = tagState.tags.map { it.name }
                val allTagNames = (dbTagNames + selectedTags.filter { it !in dbTagNames }).distinct()
                if (allTagNames.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allTagNames.forEach { tagName ->
                            FilterChip(
                                selected = tagName in selectedTags,
                                onClick = {
                                    selectedTags = if (tagName in selectedTags) selectedTags - tagName else selectedTags + tagName
                                },
                                label = { Text("#$tagName", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.15f),
                                    selectedLabelColor = accentColor
                                )
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerTextField(
                        value = customTag, onValueChange = { customTag = it },
                        label = "Add custom tag", modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val name = customTag.trim().removePrefix("#").trim()
                            if (name.isNotEmpty()) { selectedTags = selectedTags + name; customTag = "" }
                        },
                        enabled = customTag.isNotBlank()
                    ) { Icon(Icons.Filled.Add, null, tint = if (customTag.isNotBlank()) accentColor else OnSurfaceVariant) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    showErrors = true
                    val walletId = walletState.wallets.getOrNull(selectedWalletIndex)?.id
                    if (isFormValid && walletId != null) {
                        transactionViewModel.createTransaction(
                            walletId = walletId,
                            title = title.ifBlank { selectedCategory },
                            category = selectedCategory,
                            amount = amountValue!!,
                            isIncome = !isExpense,
                            note = note.ifBlank { null },
                            createdAt = selectedDate.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            tagNames = selectedTags.toList()
                        ) { navController.popBackStack() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Done, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Transaction", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = accentColor, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
