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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.RecurringViewModel
import com.ledger.app.ui.viewmodel.WalletViewModel
import uniffi.ledger.RecurringTransaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringTransactionsScreen(
    navController: NavController,
    viewModel: RecurringViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val state          by viewModel.state.collectAsStateWithLifecycle()
    val walletState    by walletViewModel.state.collectAsStateWithLifecycle()
    val categoryState  by categoryViewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.load() }

    val recurring = state.recurring
    val income    = recurring.filter { it.isIncome }.sumOf { it.amount }
    val expenses  = recurring.filter { !it.isIncome }.sumOf { it.amount }
    val net       = income - expenses

    fun walletName(id: String) = walletState.wallets.find { it.id == id }?.name ?: id

    val snackbarHostState = remember { SnackbarHostState() }
    val justApplied = state.justApplied
    LaunchedEffect(justApplied) {
        if (justApplied.isNotEmpty()) {
            val msg = if (justApplied.size == 1)
                "Posted: ${justApplied.first()}"
            else
                "${justApplied.size} recurring transactions posted"
            snackbarHostState.showSnackbar(msg)
            viewModel.clearApplied()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddRecurring.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add recurring")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddRecurring.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "Add recurring") }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Summary card
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MONTHLY RECURRING", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("+\$${"%.2f".format(income)}", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Expenses", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text("-\$${"%.2f".format(expenses)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net monthly recurring", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Text(
                            "${if (net >= 0) "+" else ""}\$${"%.2f".format(net)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (net >= 0) Primary else Tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (recurring.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No recurring transactions yet. Add one to get started.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    }
                }
            } else {
                val incomeItems  = recurring.filter { it.isIncome }
                val expenseItems = recurring.filter { !it.isIncome }

                if (incomeItems.isNotEmpty()) {
                    Text("Income", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            incomeItems.forEachIndexed { idx, tx ->
                                RecurringRow(tx, walletName(tx.walletId), categoryState.categories, viewModel)
                                if (idx < incomeItems.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }

                if (expenseItems.isNotEmpty()) {
                    Text("Expenses", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            expenseItems.forEachIndexed { idx, tx ->
                                RecurringRow(tx, walletName(tx.walletId), categoryState.categories, viewModel)
                                if (idx < expenseItems.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringRow(
    tx: RecurringTransaction,
    walletName: String,
    categories: List<uniffi.ledger.Category>,
    viewModel: RecurringViewModel
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf(tx.title) }
    var editAmount by remember { mutableStateOf(tx.amount.toString()) }
    var editFrequency by remember { mutableStateOf(tx.frequency) }
    var editNextDate by remember { mutableStateOf(tx.nextDate) }

    val accentColor = if (tx.isIncome) Primary else Tertiary
    val matchedCategory = categories.find { it.name.equals(tx.category, ignoreCase = true) }
    val icon = matchedCategory?.let { iconNameToVector(it.iconName) }
        ?: if (tx.isIncome) Icons.Filled.TrendingUp else Icons.Filled.ShoppingCart

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text(tx.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${tx.frequency.replaceFirstChar { it.uppercase() }} · Next: ${tx.nextDate}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Text("${if (tx.isIncome) "+" else "-"}\$${"%.2f".format(tx.amount)}", style = MaterialTheme.typography.bodyMedium, color = accentColor)
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsDialog = false; showDeleteDialog = true }) {
                    Text("Delete", color = Tertiary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showOptionsDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
                    TextButton(onClick = {
                        editTitle = tx.title; editAmount = tx.amount.toString()
                        editFrequency = tx.frequency; editNextDate = tx.nextDate
                        showOptionsDialog = false; showEditDialog = true
                    }) { Text("Edit", color = accentColor) }
                }
            }
        )
    }

    if (showDatePicker) {
        val initialMillis = runCatching {
            java.time.LocalDate.parse(editNextDate).toEpochDay() * 86_400_000L
        }.getOrDefault(System.currentTimeMillis())
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        editNextDate = java.time.LocalDate.ofEpochDay(it / 86_400_000L).toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showEditDialog) {
        val frequencies = listOf("daily", "weekly", "biweekly", "monthly", "yearly")
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Recurring") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LedgerTextField(
                        value = editTitle, onValueChange = { editTitle = it },
                        label = "Title", modifier = Modifier.fillMaxWidth()
                    )
                    LedgerTextField(
                        value = editAmount,
                        onValueChange = { v -> if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) editAmount = v },
                        label = "Amount",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Frequency", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        frequencies.take(3).forEach { f ->
                            FilterChip(
                                selected = editFrequency == f,
                                onClick = { editFrequency = f },
                                label = { Text(f.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor, selectedLabelColor = if (tx.isIncome) OnPrimary else OnTertiary)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        frequencies.drop(3).forEach { f ->
                            FilterChip(
                                selected = editFrequency == f,
                                onClick = { editFrequency = f },
                                label = { Text(f.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor, selectedLabelColor = if (tx.isIncome) OnPrimary else OnTertiary)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Next date: $editNextDate",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = editAmount.toDoubleOrNull()
                    if (editTitle.isNotBlank() && amt != null && amt > 0) {
                        viewModel.updateRecurring(tx.id, editTitle, amt, tx.category, editFrequency, editNextDate)
                        showEditDialog = false
                    }
                }) { Text("Save", color = accentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel", color = OnSurfaceVariant) }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recurring") },
            text = { Text("Delete \"${tx.title}\"? This will not affect past transactions.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRecurring(tx.id) { showDeleteDialog = false } }) {
                    Text("Delete", color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
            }
        )
    }

    Surface(onClick = { showOptionsDialog = true }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tx.title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tx.frequency.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("·", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text("Next: ${tx.nextDate}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                Text(walletName, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (tx.isIncome) "+" else "-"}\$${"%.2f".format(tx.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.Filled.MoreVert, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
