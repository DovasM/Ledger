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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class SplitEntry(val description: String, val amount: Double, val paidBy: String, val yourShare: Double)
private data class ExpenseGroup(
    val id: Long, val name: String, val emoji: String,
    val members: List<String>, val color: Color,
    val entries: List<SplitEntry>
) {
    val total get() = entries.sumOf { it.amount }
    val yourTotal get() = entries.sumOf { it.yourShare }
    val balance get() = entries.filter { it.paidBy == "You" }.sumOf { it.amount - it.yourShare } -
            entries.filter { it.paidBy != "You" }.sumOf { it.yourShare }
}

private val groups = listOf(
    ExpenseGroup(1, "NYC Weekend Trip", "✈️", listOf("You", "Sarah", "Mike"), Color(0xFF1565C0),
        listOf(
            SplitEntry("Hotel (2 nights)", 360.0, "You", 120.0),
            SplitEntry("Dinner @ Per Se", 210.0, "Sarah", 70.0),
            SplitEntry("Museum tickets", 45.0, "Mike", 15.0),
            SplitEntry("Taxi / Uber", 78.0, "You", 26.0),
        )
    ),
    ExpenseGroup(2, "Apartment", "🏠", listOf("You", "Tom"), Color(0xFF00513F),
        listOf(
            SplitEntry("Rent", 2_400.0, "You", 1_200.0),
            SplitEntry("Internet", 60.0, "Tom", 30.0),
            SplitEntry("Groceries", 120.0, "You", 60.0),
        )
    ),
    ExpenseGroup(3, "Office Lunch", "🍕", listOf("You", "Anna", "Chris", "Lee"), Color(0xFFE65100),
        listOf(
            SplitEntry("Pizza order", 64.0, "You", 16.0),
        )
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedExpensesScreen(navController: NavController) {
    var selectedGroup by remember { mutableStateOf<Long?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = SurfaceContainerLowest, tonalElevation = 0.dp) {
            AddExpenseSheet { showAddSheet = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Expenses", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }, containerColor = Primary, contentColor = OnPrimary) {
                Icon(Icons.Filled.Add, "Add expense")
            }
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // You are owed / you owe summary
            val totalOwed = groups.sumOf { g -> g.entries.filter { it.paidBy == "You" }.sumOf { it.amount - it.yourShare } }
            val totalOwe = groups.sumOf { g -> g.entries.filter { it.paidBy != "You" }.sumOf { it.yourShare } }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerFloatingCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("YOU ARE OWED", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.2f".format(totalOwed)}", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
                LedgerFloatingCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("YOU OWE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("${"$%,.2f".format(totalOwe)}", style = MaterialTheme.typography.titleLarge, color = Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Groups", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

            groups.forEach { group ->
                val isExpanded = selectedGroup == group.id
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Surface(
                            onClick = { selectedGroup = if (isExpanded) null else group.id },
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(group.color.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) { Text(group.emoji, style = MaterialTheme.typography.titleMedium) }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(group.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                    Text("${group.members.size} people · ${"$%,.2f".format(group.total)} total", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val bal = group.entries.filter { it.paidBy == "You" }.sumOf { it.amount - it.yourShare } -
                                            group.entries.filter { it.paidBy != "You" }.sumOf { it.yourShare }
                                    Text(
                                        if (bal >= 0) "+${"$%,.2f".format(bal)}" else "-${"$%,.2f".format(-bal)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (bal >= 0) Primary else Tertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(if (bal >= 0) "owed to you" else "you owe", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                }
                                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (isExpanded) {
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.entries.forEachIndexed { idx, entry ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                            Text("Paid by ${entry.paidBy} · Your share: ${"$%,.2f".format(entry.yourShare)}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                        }
                                        Text("${"$%,.2f".format(entry.amount)}", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                    }
                                    if (idx < group.entries.lastIndex) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.1f))
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Settle Up") }
                                    Button(onClick = { showAddSheet = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = group.color)) {
                                        Text("Add Expense", color = Color.White)
                                    }
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
private fun AddExpenseSheet(onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf("You") }
    var showErrors by remember { mutableStateOf(false) }

    val isDescriptionValid = description.isNotBlank()
    val isAmountValid = amount.toDoubleOrNull()?.let { it > 0 } ?: false

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add Expense", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        LedgerTextField(value = description, onValueChange = { description = it }, label = "Description",
            modifier = Modifier.fillMaxWidth(),
            isError = showErrors && !isDescriptionValid,
            supportingText = if (showErrors && !isDescriptionValid) "Description is required" else null)
        LedgerTextField(value = amount, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
            label = "Amount", modifier = Modifier.fillMaxWidth(),
            isError = showErrors && !isAmountValid,
            supportingText = if (showErrors && !isAmountValid) "Enter an amount greater than 0" else null)
        Text("Paid by", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("You", "Sarah", "Mike", "Tom").forEach { person ->
                FilterChip(
                    selected = paidBy == person, onClick = { paidBy = person },
                    label = { Text(person, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                )
            }
        }
        Button(
            onClick = { showErrors = true; if (isDescriptionValid && isAmountValid) onDismiss() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(6.dp)
        ) {
            Text("Add Expense", style = MaterialTheme.typography.labelLarge)
        }
    }
}
