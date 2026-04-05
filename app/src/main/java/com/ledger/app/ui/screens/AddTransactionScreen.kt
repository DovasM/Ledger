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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(navController: NavController) {
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var isSplit by remember { mutableStateOf(false) }
    var splits by remember { mutableStateOf(listOf("" to "", "" to "")) }
    var showCalc by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull()
    val isAmountValid = amountValue != null && amountValue > 0
    val isTitleValid = title.isNotBlank()
    val isFormValid = isAmountValid && isTitleValid

    val suggestedTags = listOf("#personal", "#business", "#reimbursable", "#vacation", "#subscriptions", "#medical")
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var customTag by remember { mutableStateOf("") }

    val wallets = listOf("Checking Account", "Savings Account", "Cash", "Investment")
    var selectedWallet by remember { mutableStateOf(wallets[0]) }
    var walletMenuExpanded by remember { mutableStateOf(false) }

    val expenseCategories = listOf("Housing", "Food & Dining", "Transportation", "Entertainment", "Health", "Shopping", "Other")
    val incomeCategories = listOf("Salary", "Freelance", "Investments", "Other Income")
    var selectedCategory by remember { mutableStateOf(expenseCategories[0]) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val accentColor = if (isExpense) Tertiary else Primary

    if (showCalc) {
        LedgerCalculatorSheet(
            initial = amount,
            accentColor = accentColor,
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
                    onClick = { isExpense = true; selectedCategory = expenseCategories[0] },
                    label = { Text("Expense", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary)
                )
                FilterChip(
                    selected = !isExpense,
                    onClick = { isExpense = false; selectedCategory = incomeCategories[0] },
                    label = { Text("Income", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                )
            }

            // Wallet selector
            ExposedDropdownMenuBox(expanded = walletMenuExpanded, onExpandedChange = { walletMenuExpanded = it }) {
                LedgerTextField(
                    value = selectedWallet, onValueChange = {},
                    label = "From Wallet",
                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = walletMenuExpanded, onDismissRequest = { walletMenuExpanded = false }) {
                    wallets.forEach { wallet ->
                        DropdownMenuItem(text = { Text(wallet) }, onClick = { selectedWallet = wallet; walletMenuExpanded = false })
                    }
                }
            }

            // Amount display — tap to open calculator
            Surface(
                onClick = { showCalc = true },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        Text(
                            if (isExpense) "-$" else "+$",
                            style = MaterialTheme.typography.headlineMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            if (amount.isBlank()) "0.00" else amount,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp),
                            color = if (amount.isBlank()) OutlineVariant else accentColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Calculate, contentDescription = null, tint = if (showErrors && !isAmountValid) Error else OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(
                            if (showErrors && !isAmountValid) "Amount must be greater than 0" else "Tap to enter amount",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showErrors && !isAmountValid) Error else OnSurfaceVariant
                        )
                    }
                }
            }

            LedgerTextField(
                value = title, onValueChange = { title = it },
                label = "Title",
                placeholder = "e.g. Rent, Salary",
                modifier = Modifier.fillMaxWidth(),
                isError = showErrors && !isTitleValid,
                supportingText = if (showErrors && !isTitleValid) "Title is required" else null
            )

            // Category selector
            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                LedgerTextField(
                    value = selectedCategory, onValueChange = {},
                    label = "Category",
                    leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    val categories = if (isExpense) expenseCategories else incomeCategories
                    categories.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; categoryMenuExpanded = false })
                    }
                }
            }

            LedgerTextField(
                value = note, onValueChange = { note = it },
                label = "Note (optional)",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            // Tags
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tags", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (suggestedTags + selectedTags.filter { it !in suggestedTags }).forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = { selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.15f), selectedLabelColor = accentColor)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerTextField(
                        value = customTag, onValueChange = { customTag = it },
                        label = "Add custom tag",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val tag = customTag.trim().let { if (it.startsWith("#")) it else "#$it" }
                            if (tag.length > 1) { selectedTags = selectedTags + tag; customTag = "" }
                        },
                        enabled = customTag.isNotBlank()
                    ) { Icon(Icons.Filled.Add, null, tint = if (customTag.isNotBlank()) accentColor else OnSurfaceVariant) }
                }
            }

            // Split transaction toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Split transaction", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Text("Divide across multiple categories", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
                Switch(
                    checked = isSplit, onCheckedChange = { isSplit = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                )
            }

            if (isSplit) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Split Categories", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        val cats = if (isExpense) expenseCategories else incomeCategories
                        splits.forEachIndexed { idx, (cat, amt) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                var splitCatExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(expanded = splitCatExpanded, onExpandedChange = { splitCatExpanded = it }, modifier = Modifier.weight(1f)) {
                                    LedgerTextField(
                                        value = cat.ifEmpty { cats[0] }, onValueChange = {},
                                        label = "Category",
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = splitCatExpanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor()
                                    )
                                    ExposedDropdownMenu(expanded = splitCatExpanded, onDismissRequest = { splitCatExpanded = false }) {
                                        cats.forEach { c ->
                                            DropdownMenuItem(text = { Text(c) }, onClick = {
                                                splits = splits.toMutableList().also { it[idx] = c to splits[idx].second }
                                                splitCatExpanded = false
                                            })
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = amt,
                                    onValueChange = { v ->
                                        if (v.all { it.isDigit() || it == '.' })
                                            splits = splits.toMutableList().also { it[idx] = splits[idx].first to v }
                                    },
                                    label = { Text("\$") },
                                    modifier = Modifier.width(80.dp),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                        TextButton(onClick = { splits = splits + ("" to "") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Split", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    showErrors = true
                    if (isFormValid) navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Transaction", style = MaterialTheme.typography.labelLarge)
            }

            TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = accentColor, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
