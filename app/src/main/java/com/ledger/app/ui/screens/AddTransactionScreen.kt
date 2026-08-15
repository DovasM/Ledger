package com.ledger.app.ui.screens

import com.ledger.app.ui.util.toCents
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.capitalizeFirst
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.SettingsViewModel
import com.ledger.app.ui.viewmodel.SharedExpenseViewModel
import uniffi.ledger.ShareInput
import com.ledger.app.ui.util.rememberMoneyFormatter
import uniffi.ledger.splitEqually
import com.ledger.app.ui.viewmodel.TagViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    initialCategory: String? = null,
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    tagViewModel: TagViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    sharedViewModel: SharedExpenseViewModel = hiltViewModel()
) {
    val aiEnabled by settingsViewModel.aiEnabled.collectAsStateWithLifecycle()
    val walletState    by walletViewModel.state.collectAsStateWithLifecycle()
    val tagState       by tagViewModel.state.collectAsStateWithLifecycle()
    val categoryState  by categoryViewModel.state.collectAsStateWithLifecycle()

    val money = rememberMoneyFormatter()
    val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val titleSuggestions = remember(txState.transactions) {
        txState.transactions.map { it.title }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var titleSuggestionsVisible by remember { mutableStateOf(false) }

    var shareThis by remember { mutableStateOf(false) }
    var shareGroupIndex by remember { mutableStateOf(0) }
    var shareGroupMenu by remember { mutableStateOf(false) }
    // A group's members are only fetched when that group is opened, so ask for them the moment one
    // is picked — otherwise the shares below would sit empty with nothing to explain why.
    LaunchedEffect(shareThis, shareGroupIndex, sharedState.groups) {
        if (shareThis) sharedState.groups.getOrNull(shareGroupIndex)?.let { sharedViewModel.loadGroup(it.id) }
    }

    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var showCalc by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    // The save is asynchronous and the screen only closes in its callback, so without this a second
    // tap in that window writes the transaction twice. It has already happened on a real device.
    var submitting by remember { mutableStateOf(false) }
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
    // initialCategory arrives from the home-screen quick-add widget; it only wins on the expense
    // side and only while it still matches a real category.
    var selectedCategory by remember(isExpense, expenseCategoryNames, incomeCategoryNames) {
        val fromWidget = initialCategory?.takeIf { widgetCat ->
            isExpense && expenseCategoryNames.any { it.equals(widgetCat, ignoreCase = true) }
        }
        mutableStateOf(
            fromWidget ?: if (isExpense) expenseCategoryNames[0] else incomeCategoryNames[0]
        )
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var categorySuggesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Split mode: one screen → several transactions (e.g. a receipt with cigarettes + banana).
    var splitMode by remember { mutableStateOf(false) }
    val lineItems = remember { mutableStateListOf<EditableLineItem>() }
    LaunchedEffect(splitMode) { if (splitMode && lineItems.isEmpty()) lineItems.add(EditableLineItem()) }

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
                actions = {
                    if (aiEnabled) {
                        IconButton(onClick = { navController.navigate(Screen.ReceiptScan.route) }) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = "Scan a receipt")
                        }
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

            // Split toggle — turn one entry into several categorized transactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Split into items", style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface, fontWeight = FontWeight.Medium)
                    Text("Enter a total, then add each item — one transaction per item.",
                        style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = splitMode, onCheckedChange = { splitMode = it })
            }

            if (splitMode) {
                Text("Receipt total", style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            LedgerAmountField(
                amount = amount,
                onAmountChange = { amount = it },
                onCalculatorOpen = { showCalc = true },
                prefix = if (isExpense) "-$" else "+$",
                accentColor = accentColor,
                showError = showErrors && !isAmountValid && !splitMode
            )

            if (!splitMode) {
            // Category selector — with AI suggestion (✨) that picks from the title, same as receipt scanning
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
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
                // AI suggests a category from the title (prefer existing, else invent a new one).
                if (aiEnabled) {
                    if (categorySuggesting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = accentColor)
                    } else {
                        IconButton(
                            onClick = {
                                if (title.isNotBlank() && !categorySuggesting) {
                                    scope.launch {
                                        categorySuggesting = true
                                        val cats = if (isExpense) expenseCategoryNames else incomeCategoryNames
                                        transactionViewModel.suggestCategory(title, cats)?.let { selectedCategory = it }
                                        categorySuggesting = false
                                    }
                                }
                            },
                            enabled = title.isNotBlank()
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Suggest category from title with AI",
                                tint = if (title.isNotBlank()) accentColor else OnSurfaceVariant)
                        }
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
            } else {
                // Split items — each line becomes its own transaction, reconciled against the total
                val totalValue = amount.replace(',', '.').toDoubleOrNull()
                val itemsSum = lineItems.mapNotNull { it.amount.replace(',', '.').toDoubleOrNull() }.sum()
                val categoryOptions = ((if (isExpense) expenseCategoryNames else incomeCategoryNames) + lineItems.map { it.category })
                    .filter { it.isNotBlank() }.distinct()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Items (${lineItems.size})", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = OnSurface)
                    Text("Σ %.2f".format(itemsSum), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
                if (totalValue != null && totalValue > 0) {
                    val remaining = totalValue - itemsSum
                    val matched = kotlin.math.abs(remaining) < 0.005
                    Text(
                        when {
                            matched -> "Items match the total ✓"
                            remaining > 0 -> "Remaining to add: %.2f".format(remaining)
                            else -> "Over total by %.2f".format(-remaining)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (matched) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }

                lineItems.forEachIndexed { idx, item ->
                    LineItemRow(
                        item = item,
                        categoryOptions = categoryOptions,
                        accentColor = accentColor,
                        aiEnabled = aiEnabled,
                        onDelete = { if (idx < lineItems.size) lineItems.removeAt(idx) },
                        onSuggestCategory = {
                            if (item.name.isNotBlank() && !item.suggesting) {
                                scope.launch {
                                    item.suggesting = true
                                    val cats = if (isExpense) expenseCategoryNames else incomeCategoryNames
                                    transactionViewModel.suggestCategory(item.name, cats)?.let { item.category = it }
                                    item.suggesting = false
                                }
                            }
                        }
                    )
                }

                OutlinedButton(
                    onClick = { lineItems.add(EditableLineItem(category = categoryOptions.firstOrNull() ?: "")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add item")
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

            // Splitting from here rather than from the group screen. The transaction is what you
            // came to write; that it was shared is a second fact about it, so it is asked for here
            // instead of sending you elsewhere to type the amount again.
            //
            // Only for an expense, and not alongside line-item split mode, which already writes one
            // transaction per line and would have to ask this per line to mean anything.
            val canShare = isExpense && !splitMode && sharedState.groups.isNotEmpty()
            if (canShare) {
                Spacer(Modifier.height(4.dp))
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Split this with a group", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                Text(
                                    "The whole amount still leaves this wallet — the others owe you their share",
                                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                )
                            }
                            Switch(checked = shareThis, onCheckedChange = { shareThis = it })
                        }

                        if (shareThis) {
                            val groups = sharedState.groups
                            val group = groups.getOrNull(shareGroupIndex)
                            Box {
                                TextButton(onClick = { shareGroupMenu = true }) {
                                    Text("Group: ${group?.name ?: "—"}", color = accentColor)
                                }
                                DropdownMenu(expanded = shareGroupMenu, onDismissRequest = { shareGroupMenu = false }) {
                                    groups.forEachIndexed { index, g ->
                                        DropdownMenuItem(
                                            text = { Text("${g.emoji.ifBlank { "👥" }}  ${g.name}") },
                                            onClick = { shareGroupIndex = index; shareGroupMenu = false }
                                        )
                                    }
                                }
                            }

                            val members = group?.let { sharedState.members[it.id] }.orEmpty()
                            val cents = amountValue?.toCents()
                            if (members.isEmpty()) {
                                Text(
                                    "Loading the people in this group…",
                                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                )
                            } else if (cents == null || cents <= 0) {
                                // Says what is missing rather than showing an empty list of shares.
                                Text(
                                    "Enter the amount and it will be split between ${members.size} people.",
                                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                )
                            } else {
                                val even = splitEqually(cents, members.size)
                                members.forEachIndexed { index, member ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            if (member.isYou) "${member.name} (you)" else member.name,
                                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                                        )
                                        Text(money.of(even[index]), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                                    }
                                }
                                Text(
                                    // Uneven shares live on the group screen, and saying so beats
                                    // letting somebody hunt for an editor that is not here.
                                    "Split evenly. To give people different amounts, edit it in the group afterwards.",
                                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val splitItems = lineItems.mapNotNull { li ->
                val amt = li.amount.replace(',', '.').toDoubleOrNull()
                if (amt != null && amt > 0) TransactionViewModel.LineItem(li.name, amt.toCents(), li.category) else null
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (submitting) return@Button
                    showErrors = true
                    val walletId = walletState.wallets.getOrNull(selectedWalletIndex)?.id
                    val iso = selectedDate.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    if (splitMode) {
                        if (walletId != null && splitItems.isNotEmpty()) {
                            submitting = true
                            transactionViewModel.createSplitTransactions(
                                walletId = walletId,
                                items = splitItems,
                                isIncome = !isExpense,
                                note = note.ifBlank { null },
                                occurredAt = iso,
                                tagNames = selectedTags.toList()
                            ) { navController.popBackStack() }
                        }
                    } else if (isFormValid && walletId != null) {
                        submitting = true
                        val cents = amountValue!!.toCents()
                        val shareGroup = if (canShare && shareThis) sharedState.groups.getOrNull(shareGroupIndex) else null
                        val shareMembers = shareGroup?.let { sharedState.members[it.id] }.orEmpty()
                        val shares = if (shareGroup != null && shareMembers.isNotEmpty()) {
                            splitEqually(cents, shareMembers.size).mapIndexed { index, part ->
                                ShareInput(shareMembers[index].id, part)
                            }
                        } else emptyList()

                        transactionViewModel.createTransaction(
                            walletId = walletId,
                            title = title.ifBlank { selectedCategory },
                            category = selectedCategory,
                            amountCents = cents,
                            isIncome = !isExpense,
                            note = note.ifBlank { null },
                            occurredAt = iso,
                            tagNames = selectedTags.toList(),
                            splitIntoGroupId = if (shares.isNotEmpty()) shareGroup?.id else null,
                            splitShares = shares
                        ) { navController.popBackStack() }
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Done, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (splitMode) "Save ${splitItems.size} transaction${if (splitItems.size != 1) "s" else ""}"
                    else "Save Transaction",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = accentColor, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// Compose-state-backed editable line item for split mode (mirrors ReceiptScanScreen.EditableItem).
private class EditableLineItem(name: String = "", amount: String = "", category: String = "") {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    var category by mutableStateOf(category)
    var suggesting by mutableStateOf(false)  // AI is picking this row's category
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineItemRow(
    item: EditableLineItem,
    categoryOptions: List<String>,
    accentColor: Color,
    aiEnabled: Boolean,
    onDelete: () -> Unit,
    onSuggestCategory: () -> Unit
) {
    var catExpanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LedgerTextField(
                value = item.name,
                onValueChange = { item.name = it },
                label = "Item",
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerTextField(
                    value = item.amount,
                    onValueChange = { item.amount = it },
                    label = "Price",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    LedgerTextField(
                        value = item.category,
                        onValueChange = { item.category = capitalizeFirst(it) },
                        label = "Category",
                        leadingIcon = { Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categoryOptions.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { item.category = c; catExpanded = false })
                        }
                    }
                }
                if (aiEnabled) {
                    if (item.suggesting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = accentColor)
                    } else {
                        IconButton(onClick = onSuggestCategory, enabled = item.name.isNotBlank()) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Suggest category with AI", tint = accentColor)
                        }
                    }
                }
            }
        }
    }
}
