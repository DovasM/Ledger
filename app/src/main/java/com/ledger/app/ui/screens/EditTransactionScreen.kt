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
import androidx.compose.ui.graphics.SolidColor
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionScreen(
    navController: NavController,
    transactionId: String,
    viewModel: TransactionViewModel = hiltViewModel(),
    tagViewModel: TagViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tagState by tagViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()
    val tx = state.transactions.find { it.id == transactionId }

    val titleSuggestions = remember(state.transactions) {
        state.transactions.map { it.title }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var titleSuggestionsVisible by remember { mutableStateOf(false) }

    var title by remember(tx) { mutableStateOf(tx?.title ?: "") }
    var category by remember(tx) { mutableStateOf(tx?.category ?: "") }
    var note by remember(tx) { mutableStateOf(tx?.note ?: "") }
    var isIncome by remember(tx) { mutableStateOf(tx?.isIncome ?: false) }
    var selectedDate by remember(tx) {
        mutableStateOf(
            tx?.createdAt?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now()
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val expenseCategoryNames = categoryState.categories.filter { it.isExpense }.map { it.name }
        .let { list -> if (tx != null && !isIncome && tx.category.isNotBlank() && tx.category !in list) list + tx.category else list }
        .ifEmpty { listOf("Housing", "Food & Dining", "Transportation", "Entertainment", "Health", "Shopping", "Other") }
    val incomeCategoryNames = categoryState.categories.filter { !it.isExpense }.map { it.name }
        .let { list -> if (tx != null && isIncome && tx.category.isNotBlank() && tx.category !in list) list + tx.category else list }
        .ifEmpty { listOf("Salary", "Freelance", "Investments", "Other Income") }

    // Tags: set of tag names currently attached
    var selectedTagNames by remember { mutableStateOf(setOf<String>()) }
    var customTag by remember { mutableStateOf("") }
    var tagsLoaded by remember { mutableStateOf(false) }

    // Load existing tags for this transaction once
    LaunchedEffect(transactionId) {
        tagViewModel.loadAll()
        tagViewModel.loadTransactionTags(transactionId)
        val existing = tagViewModel.loadTagsForTransaction(transactionId)
        selectedTagNames = existing.map { it.name }.toSet()
        tagsLoaded = true
    }

    val accentColor = if (isIncome) Primary else Tertiary

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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction") },
            text = { Text("This transaction will be permanently deleted and the wallet balance will not be adjusted.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(transactionId) { navController.popBackStack() }
                }) { Text("Delete", color = Tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Transaction", style = MaterialTheme.typography.headlineSmall) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Income / Expense toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = !isIncome,
                    onClick = { isIncome = false },
                    label = { Text("Expense", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary)
                )
                FilterChip(
                    selected = isIncome,
                    onClick = { isIncome = true },
                    label = { Text("Income", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                )
            }

            if (tx != null) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Amount", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        Text(
                            (if (isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor
                        )
                    }
                }
            }

            // Category dropdown — most important field, shown first
            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                LedgerTextField(
                    value = category, onValueChange = {},
                    label = "Category",
                    leadingIcon = { Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    val cats = if (isIncome) incomeCategoryNames else expenseCategoryNames
                    cats.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; categoryMenuExpanded = false })
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
                    label = "Title (optional)",
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
            if (tagsLoaded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tags", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    val dbTagNames = tagState.tags.map { it.name }
                    val allTagNames = (dbTagNames + selectedTagNames.filter { it !in dbTagNames }).distinct()
                    if (allTagNames.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            allTagNames.forEach { tagName ->
                                FilterChip(
                                    selected = tagName in selectedTagNames,
                                    onClick = {
                                        selectedTagNames = if (tagName in selectedTagNames)
                                            selectedTagNames - tagName else selectedTagNames + tagName
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
                                if (name.isNotEmpty()) { selectedTagNames = selectedTagNames + name; customTag = "" }
                            },
                            enabled = customTag.isNotBlank()
                        ) { Icon(Icons.Filled.Add, null, tint = if (customTag.isNotBlank()) accentColor else OnSurfaceVariant) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    showErrors = true
                    if (tx != null) {
                        val dateStr = selectedDate.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        val effectiveTitle = title.ifBlank { category }
                        viewModel.updateTransaction(transactionId, effectiveTitle, category, tx.amount, isIncome, note.ifBlank { null }, dateStr) {}
                        // Sync tags async via TagViewModel
                        val allDbTags = tagState.tags
                        val currentTagNames = tagState.transactionTags.map { it.name }.toSet()
                        val toAdd = selectedTagNames - currentTagNames
                        val toRemove = currentTagNames - selectedTagNames
                        for (name in toAdd) {
                            val tag = allDbTags.find { it.name == name }
                            if (tag != null) {
                                tagViewModel.addTagToTransaction(transactionId, tag.id)
                            } else {
                                tagViewModel.createTag(name) { newTag ->
                                    tagViewModel.addTagToTransaction(transactionId, newTag.id)
                                }
                            }
                        }
                        for (name in toRemove) {
                            val tag = allDbTags.find { it.name == name }
                            if (tag != null) tagViewModel.removeTagFromTransaction(transactionId, tag.id)
                        }
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Changes", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Tertiary.copy(alpha = 0.5f)))
            ) {
                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete Transaction")
            }
        }
    }
}
