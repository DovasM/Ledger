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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringScreen(navController: NavController) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedFrequency by remember { mutableStateOf("Monthly") }
    val frequencies = listOf("Daily", "Weekly", "Bi-weekly", "Monthly", "Yearly")
    var startDate by remember { mutableStateOf("May 1, 2026") }
    var selectedWallet by remember { mutableStateOf("Checking Account") }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Housing") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val wallets = listOf("Checking Account", "Savings Account", "Cash")
    val categories = listOf("Housing", "Food & Dining", "Transport", "Entertainment", "Health", "Shopping", "Work", "Other")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Recurring", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.Close, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = isExpense, onClick = { isExpense = true },
                    label = { Text("Expense", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary)
                )
                FilterChip(
                    selected = !isExpense, onClick = { isExpense = false },
                    label = { Text("Income", style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                    Text(if (isExpense) "-\$" else "+\$", style = MaterialTheme.typography.headlineMedium,
                        color = if (isExpense) Tertiary else Primary, fontWeight = FontWeight.Light)
                    Text(if (amount.isBlank()) "0.00" else amount,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp),
                        color = if (amount.isBlank()) OutlineVariant else if (isExpense) Tertiary else Primary,
                        fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amount = v },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = if (isExpense) Tertiary else Primary),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            LedgerTextField(value = title, onValueChange = { title = it }, label = "Title", modifier = Modifier.fillMaxWidth())

            Text("Frequency", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                frequencies.take(3).forEach { f ->
                    FilterChip(selected = selectedFrequency == f, onClick = { selectedFrequency = f },
                        label = { Text(f, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                frequencies.drop(3).forEach { f ->
                    FilterChip(selected = selectedFrequency == f, onClick = { selectedFrequency = f },
                        label = { Text(f, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary))
                }
            }

            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                LedgerTextField(value = selectedCategory, onValueChange = {}, label = "Category",
                    leadingIcon = { Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    categories.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; categoryMenuExpanded = false }) }
                }
            }

            ExposedDropdownMenuBox(expanded = walletMenuExpanded, onExpandedChange = { walletMenuExpanded = it }) {
                LedgerTextField(value = selectedWallet, onValueChange = {}, label = "Wallet",
                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expanded = walletMenuExpanded, onDismissRequest = { walletMenuExpanded = false }) {
                    wallets.forEach { w -> DropdownMenuItem(text = { Text(w) }, onClick = { selectedWallet = w; walletMenuExpanded = false }) }
                }
            }

            LedgerTextField(value = note, onValueChange = { note = it }, label = "Note (optional)", singleLine = false, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Recurring Transaction", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
