package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.*
import uniffi.ledger.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class TxFilter { ALL, INCOME, EXPENSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    navController: NavController,
    txViewModel: TransactionViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    goalViewModel: GoalViewModel = hiltViewModel(),
    debtViewModel: DebtViewModel = hiltViewModel(),
    recurringViewModel: RecurringViewModel = hiltViewModel()
) {
    val txState        by txViewModel.state.collectAsStateWithLifecycle()
    val catState       by categoryViewModel.state.collectAsStateWithLifecycle()
    val walletState    by walletViewModel.state.collectAsStateWithLifecycle()
    val goalState      by goalViewModel.state.collectAsStateWithLifecycle()
    val debtState      by debtViewModel.state.collectAsStateWithLifecycle()
    val recurringState by recurringViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { txViewModel.loadAll() }

    var query        by remember { mutableStateOf("") }
    var typeFilter   by remember { mutableStateOf(TxFilter.ALL) }
    var sheetTx      by remember { mutableStateOf<Transaction?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val shortFmt = DateTimeFormatter.ofPattern("MMM d")

    // ── Transaction results ────────────────────────────────────────────────────
    val filteredTxs = remember(txState.transactions, query, typeFilter) {
        if (query.length < 2) emptyList()
        else txState.transactions.filter { tx ->
            val matchesQuery = tx.title.contains(query, ignoreCase = true) ||
                tx.category.contains(query, ignoreCase = true) ||
                (!tx.note.isNullOrBlank() && tx.note!!.contains(query, ignoreCase = true)) ||
                "%.2f".format(tx.amount).contains(query)
            val matchesType = when (typeFilter) {
                TxFilter.ALL     -> true
                TxFilter.INCOME  -> tx.isIncome
                TxFilter.EXPENSE -> !tx.isIncome
            }
            matchesQuery && matchesType
        }.sortedByDescending { it.createdAt }
    }

    // ── Quick-jump results ─────────────────────────────────────────────────────
    data class QuickResult(val label: String, val sub: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

    val quickResults: List<QuickResult> = remember(query, walletState.wallets, goalState.goals, debtState.debts, recurringState.recurring) {
        if (query.length < 2) return@remember emptyList()
        val q = query.trim()
        buildList {
            walletState.wallets.filter { it.name.contains(q, ignoreCase = true) }.take(2).forEach { w ->
                add(QuickResult(w.name, "${"$%,.2f".format(w.balance)} · Wallet", Icons.Filled.AccountBalanceWallet, Primary) {
                    navController.navigate(Screen.WalletDetails.createRoute(w.id))
                })
            }
            goalState.goals.filter { it.name.contains(q, ignoreCase = true) }.take(2).forEach { g ->
                add(QuickResult(g.name, "${"$%,.0f".format(g.currentAmount)} / ${"$%,.0f".format(g.targetAmount)} · Goal", Icons.Filled.Flag, Color(0xFF1565C0)) {
                    navController.navigate(Screen.GoalDetails.createRoute(g.id))
                })
            }
            debtState.debts.filter { it.name.contains(q, ignoreCase = true) }.take(2).forEach { d ->
                add(QuickResult(d.name, "${"$%,.2f".format(d.remainingAmount)} remaining · Debt", Icons.Filled.CreditCard, Tertiary) {
                    navController.navigate(Screen.EditDebt.createRoute(d.id))
                })
            }
            recurringState.recurring.filter { it.title.contains(q, ignoreCase = true) }.take(2).forEach { r ->
                add(QuickResult(r.title, "${"$%,.2f".format(r.amount)} · ${r.frequency.replaceFirstChar { it.uppercase() }}", Icons.Filled.Repeat, Color(0xFF6A1B9A)) {
                    navController.navigate(Screen.RecurringTransactions.route)
                })
            }
        }.take(4)
    }

    // ── Bottom sheet ───────────────────────────────────────────────────────────
    if (sheetTx != null) {
        val tx = sheetTx!!
        val txCategory = catState.categories.find { it.name == tx.category }
        val txIcon  = txCategory?.let { iconNameToVector(it.iconName) } ?: Icons.Filled.Category
        val txColor = txCategory?.let { colorHexToColor(it.colorHex) } ?: Primary
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            SearchTxSheet(
                tx = tx, icon = txIcon, color = txColor,
                displayDate = runCatching { LocalDate.parse(tx.createdAt.take(10)).format(dateFmt) }.getOrElse { tx.createdAt.take(10) },
                onDismiss = { sheetTx = null },
                onEdit = { sheetTx = null; navController.navigate(Screen.EditTransaction.createRoute(tx.id)) },
                onDelete = { txViewModel.deleteTransaction(tx.id); sheetTx = null }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; typeFilter = TxFilter.ALL },
                        placeholder = { Text("Search transactions, wallets, goals…", color = OnSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                        trailingIcon = if (query.isNotEmpty()) ({
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant) }
                        }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = OutlineVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ── Idle state ─────────────────────────────────────────────────────
            if (query.length < 2) {
                if (catState.categories.isNotEmpty()) {
                    item {
                        Text("Categories", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    item {
                        LedgerCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                catState.categories.forEachIndexed { idx, cat ->
                                    val catIcon  = iconNameToVector(cat.iconName)
                                    val catColor = colorHexToColor(cat.colorHex)
                                    Surface(
                                        onClick = { query = cat.name },
                                        color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(32.dp).clip(CircleShape).background(catColor.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(catIcon, null, tint = catColor, modifier = Modifier.size(16.dp)) }
                                            Text(cat.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (idx < catState.categories.lastIndex)
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.12f))
                                }
                            }
                        }
                    }
                } else if (txState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }
                }

            // ── Active search ──────────────────────────────────────────────────
            } else {
                // Type filter chips
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TxFilter.ALL to "All", TxFilter.INCOME to "Income", TxFilter.EXPENSE to "Expenses").forEach { (filter, label) ->
                            FilterChip(
                                selected = typeFilter == filter,
                                onClick = { typeFilter = filter },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = OnPrimary
                                )
                            )
                        }
                    }
                }

                // Quick-jump results
                if (quickResults.isNotEmpty()) {
                    item {
                        Text("Jump to", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    }
                    item {
                        LedgerCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                quickResults.forEachIndexed { idx, result ->
                                    Surface(onClick = result.onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(36.dp).clip(CircleShape).background(result.color.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) { Icon(result.icon, null, tint = result.color, modifier = Modifier.size(18.dp)) }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(result.label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                                Text(result.sub, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                            }
                                            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (idx < quickResults.lastIndex)
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.12f))
                                }
                            }
                        }
                    }
                }

                // Transaction results header
                item {
                    Text(
                        if (txState.isLoading) "Searching…"
                        else "${filteredTxs.size} transaction${if (filteredTxs.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                if (txState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }
                } else if (filteredTxs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.SearchOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
                            Text("No transactions for \"$query\"", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text("Try a different keyword or filter", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                } else {
                    item {
                        LedgerCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                filteredTxs.forEachIndexed { idx, tx ->
                                    val txCategory = catState.categories.find { it.name == tx.category }
                                    val txColor = txCategory?.let { colorHexToColor(it.colorHex) } ?: if (tx.isIncome) Primary else Tertiary
                                    val displayDate = runCatching { LocalDate.parse(tx.createdAt.take(10)).format(shortFmt) }.getOrElse { tx.createdAt.take(10) }

                                    Surface(
                                        onClick = { sheetTx = tx },
                                        color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(38.dp).clip(CircleShape).background(txColor.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val catIcon = txCategory?.let { iconNameToVector(it.iconName) } ?: Icons.Filled.Category
                                                Icon(catIcon, null, tint = txColor, modifier = Modifier.size(18.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(tx.title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                                Text("${tx.category} · $displayDate", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                            }
                                            Text(
                                                "${if (tx.isIncome) "+" else "-"}${"$%,.2f".format(tx.amount)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (tx.isIncome) Primary else Tertiary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (idx < filteredTxs.lastIndex)
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTxSheet(
    tx: Transaction,
    icon: ImageVector,
    color: Color,
    displayDate: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("\"${tx.title}\" will be permanently removed.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = Tertiary) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(
                    "${if (tx.isIncome) "+" else "-"}${"$%,.2f".format(tx.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (tx.isIncome) Primary else Tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(26.dp)) }
        }
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchDetailRow(Icons.Filled.CalendarToday, "Date", displayDate)
            SearchDetailRow(Icons.Filled.Category, "Category", tx.category)
            SearchDetailRow(
                if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type", if (tx.isIncome) "Income" else "Expense"
            )
            if (!tx.note.isNullOrBlank())
                SearchDetailRow(Icons.Filled.Notes, "Note", tx.note!!)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary)
            ) {
                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit")
            }
        }
    }
}

@Composable
private fun SearchDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
