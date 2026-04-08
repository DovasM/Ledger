package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.TagViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel
import uniffi.ledger.Tag
import uniffi.ledger.Transaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    navController: NavController,
    viewModel: TransactionViewModel = hiltViewModel(),
    tagViewModel: TagViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.loadAll() }
    var searchQuery by remember { mutableStateOf("") }
    var sheetTx by remember { mutableStateOf<Transaction?>(null) }
    var sheetTxTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var longPressTx by remember { mutableStateOf<Transaction?>(null) }

    if (longPressTx != null) {
        val tx = longPressTx!!
        LedgerActionDialog(
            title = tx.title,
            subtitle = (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
            onDismiss = { longPressTx = null },
            onEdit = { longPressTx = null; navController.navigate(Screen.EditTransaction.createRoute(tx.id)) },
            onDelete = { viewModel.deleteTransaction(tx.id) {}; longPressTx = null }
        )
    }

    LaunchedEffect(sheetTx?.id) {
        val id = sheetTx?.id
        sheetTxTags = if (id != null) tagViewModel.loadTagsForTransaction(id) else emptyList()
    }

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(
            onDismissRequest = { sheetTx = null },
            containerColor = SurfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            TxDetailSheet(tx, tags = sheetTxTags, onDismiss = { sheetTx = null },
                onEdit = { navController.navigate(Screen.EditTransaction.createRoute(tx.id)); sheetTx = null })
        }
    }

    val filtered = if (searchQuery.isBlank()) state.transactions
    else state.transactions.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.category.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddTransaction.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        bottomBar = { LedgerBottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddTransaction.route) },
                containerColor = Primary, contentColor = OnPrimary) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            LedgerTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                label = "Search transactions",
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("All Transactions", style = MaterialTheme.typography.titleMedium, color = OnSurfaceVariant)
                Text("${filtered.size} · Tap · Hold to edit/delete", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (filtered.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isBlank()) "No transactions yet." else "No results for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        filtered.forEachIndexed { idx, tx ->
                            TransactionRow(
                                tx.title,
                                tx.category,
                                (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                                isIncome = tx.isIncome,
                                onClick = { sheetTx = tx },
                                onLongClick = { longPressTx = tx },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            if (idx < filtered.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TxDetailSheet(tx: Transaction, tags: List<Tag>, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val accentColor = if (tx.isIncome) Primary else Tertiary
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(
                    (if (tx.isIncome) "+" else "-") + "${"$%,.2f".format(tx.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TxDetailRow(Icons.Filled.CalendarToday, "Date", tx.createdAt.take(10))
            TxDetailRow(Icons.Filled.Category, "Category", tx.category.ifBlank { "—" })
            TxDetailRow(if (tx.isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                "Type", if (tx.isIncome) "Income" else "Expense")
            if (!tx.note.isNullOrBlank()) {
                TxDetailRow(Icons.Filled.Notes, "Note", tx.note!!)
            }
        }
        if (tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Label, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("Tags", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("#${tag.name}", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = accentColor.copy(alpha = 0.1f),
                                labelColor = accentColor
                            )
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
            Button(onClick = onEdit, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Edit") }
        }
    }
}

@Composable
private fun TxDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
