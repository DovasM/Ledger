package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.ShareSplit
import com.ledger.app.ui.util.asAmountInput
import com.ledger.app.ui.util.MoneyFormatter
import com.ledger.app.ui.util.rememberMoneyFormatter
import com.ledger.app.ui.util.toCentsOrNull
import com.ledger.app.ui.viewmodel.SharedExpenseViewModel
import uniffi.ledger.ExpenseGroup
import uniffi.ledger.ExpenseShare
import uniffi.ledger.GroupMember
import uniffi.ledger.ShareInput
import uniffi.ledger.SharedExpense
import uniffi.ledger.splitEqually

/**
 * Splitting costs with other people.
 *
 * The transaction behind an expense you paid is deliberately left whole — your wallet really did
 * lose all of it, and the reports go on saying so. What each person owes is tracked here beside it,
 * so the two facts never have to be reconciled into one misleading number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedExpensesScreen(
    navController: NavController,
    viewModel: SharedExpenseViewModel = hiltViewModel()
) {
    val money = rememberMoneyFormatter()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var newGroup by remember { mutableStateOf(false) }
    var openGroupId by remember { mutableStateOf<String?>(null) }
    // Adding an expense straight from the list, without having to open the group first.
    var expenseForGroupId by remember { mutableStateOf<String?>(null) }

    if (newGroup) {
        NewGroupDialog(
            onDismiss = { newGroup = false },
            onCreate = { name, emoji, members ->
                viewModel.createGroup(name, emoji, "#1565C0", members) { newGroup = false }
            }
        )
    }

    expenseForGroupId?.let { id ->
        val members = state.members[id].orEmpty()
        if (members.isEmpty()) {
            // Loading, or a group with nobody in it. Saying so beats a button that does nothing.
            AlertDialog(
                onDismissRequest = { expenseForGroupId = null },
                title = { Text("No one to split with yet") },
                text = { Text("Open the group and add someone first.") },
                confirmButton = { TextButton(onClick = { expenseForGroupId = null }) { Text("OK", color = Primary) } }
            )
        } else {
            ExpenseDialog(
                members = members,
                money = money,
                onDismiss = { expenseForGroupId = null },
                onSubmit = { description, amountCents, paidBy, shares ->
                    viewModel.addExpense(id, description, amountCents, paidBy, shares)
                    expenseForGroupId = null
                }
            )
        }
    }

    openGroupId?.let { id ->
        val group = state.groups.find { it.id == id }
        if (group != null) {
            GroupSheet(
                group = group,
                members = state.members[id].orEmpty(),
                expenses = state.expenses[id].orEmpty(),
                money = money,
                onDismiss = { openGroupId = null },
                onLoad = { viewModel.loadGroup(id) },
                shares = state.shares,
                onAddExpense = { description, amountCents, paidBy, shares ->
                    viewModel.addExpense(id, description, amountCents, paidBy, shares)
                },
                onEditExpense = { expenseId, description, amountCents, paidBy, shares ->
                    viewModel.updateExpense(expenseId, id, description, amountCents, paidBy, shares)
                },
                onLoadShares = { viewModel.loadShares(it) },
                onDeleteExpense = { viewModel.deleteExpense(it, id) },
                onAddMember = { viewModel.addMember(id, it) },
                onRename = { name, emoji -> viewModel.renameGroup(id, name, emoji) },
                onDeleteGroup = { viewModel.deleteGroup(id) { openGroupId = null } }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Expenses", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { newGroup = true },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "New group") }
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val owedToYou = state.groups.filter { it.netBalanceCents > 0 }.sumOf { it.netBalanceCents }
            val youOwe = state.groups.filter { it.netBalanceCents < 0 }.sumOf { -it.netBalanceCents }

            if (state.groups.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LedgerFloatingCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("OWED TO YOU", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(money.of(owedToYou), style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    LedgerFloatingCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("YOU OWE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Text(money.of(youOwe), style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (state.groups.isEmpty()) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Group, null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
                        Text("No groups yet", style = MaterialTheme.typography.titleSmall, color = OnSurface)
                        Text(
                            "Make a group for a trip or a flat, add who is in it, and record what each of you paid.",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                state.groups.forEach { group ->
                    GroupCard(
                        group = group,
                        money = money,
                        onOpen = { openGroupId = group.id; viewModel.loadGroup(group.id) },
                        onAddExpense = { expenseForGroupId = group.id; viewModel.loadGroup(group.id) }
                    )
                }
            }

            state.error?.let { message ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Tertiary, modifier = Modifier.size(20.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK", color = Primary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: ExpenseGroup,
    money: MoneyFormatter,
    onOpen: () -> Unit,
    onAddExpense: () -> Unit
) {
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = onOpen, color = Color.Transparent) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(group.emoji.ifBlank { "👥" }, style = MaterialTheme.typography.titleLarge)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${group.memberCount} people · ${group.expenseCount} expenses · ${money.of(group.totalCents)} total",
                                style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                            )
                        }
                        // Without this the card gives no sign that there is anything behind it.
                        Icon(Icons.Filled.ChevronRight, "Open group", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        when {
                            group.expenseCount == 0 -> "Nothing recorded yet"
                            group.netBalanceCents > 0 -> "You are owed ${money.of(group.netBalanceCents)}"
                            group.netBalanceCents < 0 -> "You owe ${money.of(-group.netBalanceCents)}"
                            else -> "Settled up"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            group.expenseCount == 0 -> OnSurfaceVariant
                            group.netBalanceCents > 0 -> Primary
                            group.netBalanceCents < 0 -> Tertiary
                            else -> OnSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // The amount goes in here. Reaching it only by opening the group left the list showing a
            // single + that makes another group, which is not what anyone is looking for.
            Button(
                onClick = onAddExpense,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add expense", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewGroupDialog(onDismiss: () -> Unit, onCreate: (String, String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("👥") }
    // One name at a time, added with the button or the keyboard's done key. Typing them into one
    // field separated by commas meant hunting for a comma on a phone keyboard for every person.
    var personName by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(listOf<String>()) }

    fun addPerson() {
        val trimmed = personName.trim()
        if (trimmed.isNotEmpty() && people.none { it.equals(trimmed, ignoreCase = true) }) {
            people = people + trimmed
        }
        personName = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerTextField(value = name, onValueChange = { name = it }, label = "Name", modifier = Modifier.fillMaxWidth())
                LedgerTextField(value = emoji, onValueChange = { emoji = it.take(2) }, label = "Emoji", modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = "Add someone",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addPerson() })
                    )
                    IconButton(onClick = { addPerson() }, enabled = personName.isNotBlank()) {
                        Icon(
                            Icons.Filled.Add, "Add this person",
                            tint = if (personName.isNotBlank()) Primary else OnSurfaceVariant
                        )
                    }
                }

                if (people.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        people.forEach { person ->
                            InputChip(
                                selected = false,
                                onClick = { people = people - person },
                                label = { Text(person, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Filled.Close, "Remove $person", modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }

                Text(
                    "You are always in the group — every balance is shown from your side.",
                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                // Anything still sitting in the field counts too, so a name typed but not added is
                // not silently thrown away.
                onClick = {
                    val trimmed = personName.trim()
                    val all = if (trimmed.isNotEmpty() && people.none { it.equals(trimmed, ignoreCase = true) })
                        people + trimmed else people
                    onCreate(name, emoji, all)
                }
            ) { Text("Create", color = Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceVariant) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(
    group: ExpenseGroup,
    members: List<GroupMember>,
    expenses: List<SharedExpense>,
    money: MoneyFormatter,
    onDismiss: () -> Unit,
    onLoad: () -> Unit,
    shares: Map<String, List<ExpenseShare>>,
    onAddExpense: (String, Long, String, List<ShareInput>) -> Unit,
    onEditExpense: (String, String, Long, String, List<ShareInput>) -> Unit,
    onLoadShares: (String) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onAddMember: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDeleteGroup: () -> Unit
) {
    LaunchedEffect(group.id) { onLoad() }
    var addExpense by remember { mutableStateOf(false) }
    // The entry being corrected. Its shares are fetched when it is picked, because the list itself
    // only carries your own share.
    var editing by remember { mutableStateOf<SharedExpense?>(null) }
    var addMember by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    editing?.let { expense ->
        ExpenseDialog(
            members = members,
            money = money,
            existing = expense,
            existingShares = shares[expense.id].orEmpty(),
            onDismiss = { editing = null },
            onSubmit = { description, amountCents, paidBy, newShares ->
                onEditExpense(expense.id, description, amountCents, paidBy, newShares)
                editing = null
            }
        )
    }

    if (addExpense && members.isEmpty()) {
        AlertDialog(
            onDismissRequest = { addExpense = false },
            title = { Text("No one to split with yet") },
            text = { Text("Add someone to the group first.") },
            confirmButton = { TextButton(onClick = { addExpense = false }) { Text("OK", color = Primary) } }
        )
    }
    if (addExpense && members.isNotEmpty()) {
        ExpenseDialog(
            members = members,
            money = money,
            onDismiss = { addExpense = false },
            onSubmit = { description, amountCents, paidBy, shares ->
                onAddExpense(description, amountCents, paidBy, shares)
                addExpense = false
            }
        )
    }

    if (addMember) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMember = false },
            title = { Text("Add someone") },
            text = { LedgerTextField(value = name, onValueChange = { name = it }, label = "Name", modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { onAddMember(name); addMember = false }) {
                    Text("Add", color = Primary)
                }
            },
            dismissButton = { TextButton(onClick = { addMember = false }) { Text("Cancel", color = OnSurfaceVariant) } }
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(group.name) }
        var emoji by remember { mutableStateOf(group.emoji) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LedgerTextField(value = name, onValueChange = { name = it }, label = "Name", modifier = Modifier.fillMaxWidth())
                    LedgerTextField(value = emoji, onValueChange = { emoji = it }, label = "Emoji", modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { onRename(name, emoji); renaming = false }) {
                    Text("Save", color = Primary)
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = OnSurfaceVariant) } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this group?") },
            text = { Text("Its expenses and everything each person owes go with it. Your own transactions stay.") },
            confirmButton = { TextButton(onClick = { onDeleteGroup(); confirmDelete = false }) { Text("Delete", color = Error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = OnSurfaceVariant) } }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(group.emoji.ifBlank { "👥" }, style = MaterialTheme.typography.headlineSmall)
                Text(group.name, style = MaterialTheme.typography.titleLarge, color = OnSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { renaming = true }) {
                    Icon(Icons.Filled.Edit, "Rename group", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, "Delete group", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            // Who is owed what. These always add up to zero — every euro someone put in is owed to
            // them by somebody else.
            Text("Balances", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
            members.forEach { member ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape).background(SurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = OnSurface)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (member.isYou) "${member.name} (you)" else member.name,
                        style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f)
                    )
                    Text(
                        when {
                            member.balanceCents > 0 -> "is owed ${money.of(member.balanceCents)}"
                            member.balanceCents < 0 -> "owes ${money.of(-member.balanceCents)}"
                            else -> "settled"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            member.balanceCents > 0 -> Primary
                            member.balanceCents < 0 -> Tertiary
                            else -> OnSurfaceVariant
                        }
                    )
                }
            }
            TextButton(onClick = { addMember = true }) { Text("Add someone", color = Primary) }

            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))

            Text("Expenses", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
            if (expenses.isEmpty()) {
                Text("Nothing recorded yet.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            } else {
                expenses.forEach { expense ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { editing = expense; onLoadShares(expense.id) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(expense.description, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                "${expense.paidByName} paid ${money.of(expense.amountCents)} · your share ${money.of(expense.yourShareCents)}",
                                style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                            )
                            Text("Tap to correct", style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                        IconButton(onClick = { onDeleteExpense(expense.id) }) {
                            Icon(Icons.Filled.Close, "Remove", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Button(
                onClick = { addExpense = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add expense", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ExpenseDialog(
    members: List<GroupMember>,
    money: MoneyFormatter,
    // Null when writing a new expense; the entry being corrected otherwise.
    existing: SharedExpense? = null,
    existingShares: List<ExpenseShare> = emptyList(),
    onDismiss: () -> Unit,
    onSubmit: (String, Long, String, List<ShareInput>) -> Unit
) {
    val editing = existing != null

    // What each member was down for last time, in the order the members are shown. Absent means they
    // were not on this expense at all, which reads as zero.
    val previous = remember(existing?.id, existingShares, members) {
        members.map { m -> existingShares.firstOrNull { it.memberId == m.id }?.shareCents ?: 0L }
    }

    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    // Never the raw cents: a field pre-filled with 3600 saves as 3600.00 and multiplies the entry by
    // a hundred without a word.
    var amount by remember(existing?.id) { mutableStateOf(existing?.amountCents?.asAmountInput().orEmpty()) }
    var paidByIndex by remember(existing?.id, members) {
        mutableStateOf(
            members.indexOfFirst { if (editing) it.id == existing?.paidByMemberId else it.isYou }.coerceAtLeast(0)
        )
    }
    var payerMenu by remember { mutableStateOf(false) }

    // An entry that was split unevenly opens on the custom side, so correcting the description does
    // not silently flatten the split back to equal shares.
    var custom by remember(existing?.id, previous) {
        mutableStateOf(editing && previous.isNotEmpty() && previous.distinct().size > 1)
    }
    var typedShares by remember(existing?.id, previous) {
        mutableStateOf(if (editing) previous.map { it.asAmountInput() } else members.map { "" })
    }

    val amountCents = amount.toCentsOrNull()

    // The even split comes from the Rust helper, so what is shown is exactly what would be stored,
    // including which person picks up the odd cent.
    val evenShares = remember(amountCents, members.size) {
        if (amountCents == null || amountCents <= 0) emptyList()
        else splitEqually(amountCents, members.size)
    }

    val customShares = typedShares.map { it.toCentsOrNull() ?: 0L }
    val shares = if (custom) customShares else evenShares
    val left = if (amountCents == null) 0L else ShareSplit.remainder(amountCents, shares)
    val balanced = amountCents != null && ShareSplit.isBalanced(amountCents, shares)

    // Switching to custom starts from the even split rather than from empty fields, so the usual
    // case — one person a little more, another a little less — is a couple of edits.
    LaunchedEffect(custom, evenShares) {
        if (custom && evenShares.isNotEmpty() && typedShares.all { it.isBlank() }) {
            typedShares = evenShares.map { it.asAmountInput() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit expense" else "Add expense") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LedgerTextField(value = description, onValueChange = { description = it }, label = "What was it", modifier = Modifier.fillMaxWidth())
                LedgerTextField(
                    value = amount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                    label = "Amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    TextButton(onClick = { payerMenu = true }) {
                        Text("Paid by ${members.getOrNull(paidByIndex)?.name ?: "—"}", color = Primary)
                    }
                    DropdownMenu(expanded = payerMenu, onDismissRequest = { payerMenu = false }) {
                        members.forEachIndexed { index, member ->
                            DropdownMenuItem(
                                text = { Text(if (member.isYou) "${member.name} (you)" else member.name) },
                                onClick = { paidByIndex = index; payerMenu = false }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !custom, onClick = { custom = false },
                        label = { Text("Split evenly", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                    )
                    FilterChip(
                        selected = custom, onClick = { custom = true },
                        label = { Text("Different amounts", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                    )
                }

                if (!custom) {
                    if (evenShares.isNotEmpty()) {
                        members.forEachIndexed { index, member ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(member.name, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                Text(money.of(evenShares[index]), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            }
                        }
                    }
                } else {
                    members.forEachIndexed { index, member ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LedgerTextField(
                                value = typedShares.getOrElse(index) { "" },
                                onValueChange = { v ->
                                    if (v.all { it.isDigit() || it == '.' || it == ',' }) {
                                        typedShares = typedShares.toMutableList().also { it[index] = v }
                                    }
                                },
                                label = member.name,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            // Rounding an uneven split by hand always leaves a few cents over; this
                            // puts them on one person instead of making you work it out.
                            if (left != 0L) {
                                TextButton(onClick = {
                                    val fixed = ShareSplit.assignRemainderTo(amountCents ?: 0L, customShares, index)
                                    typedShares = fixed.map { it.asAmountInput() }
                                }) { Text("+rest", style = MaterialTheme.typography.labelSmall, color = Primary) }
                            }
                        }
                    }

                    Text(
                        when {
                            amountCents == null -> "Enter the amount first"
                            left > 0L -> "${money.of(left)} still to assign"
                            left < 0L -> "${money.of(-left)} more than the expense"
                            else -> "Adds up"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (balanced) Primary else Tertiary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank() && balanced,
                onClick = {
                    val payer = members[paidByIndex]
                    onSubmit(
                        description,
                        amountCents!!,
                        payer.id,
                        members.mapIndexed { index, member -> ShareInput(member.id, shares[index]) }
                    )
                }
            ) { Text(if (editing) "Save" else "Add", color = Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceVariant) } }
    )
}
