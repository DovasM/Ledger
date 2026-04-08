package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWalletScreen(
    navController: NavController,
    viewModel: WalletViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var selectedType by remember { mutableStateOf("Checking") }
    var showErrors by remember { mutableStateOf(false) }

    val walletIcons = listOf(
        Icons.Filled.AccountBalance, Icons.Filled.Savings, Icons.Filled.Money,
        Icons.Filled.TrendingUp, Icons.Filled.CreditCard, Icons.Filled.AccountBalanceWallet,
        Icons.Filled.Business, Icons.Filled.ShoppingCart, Icons.Filled.Home,
        Icons.Filled.Work, Icons.Filled.LocalAtm, Icons.Filled.Payments,
    )
    val walletTypes = listOf("Checking", "Savings", "Cash", "Investment", "Credit Card", "Other")

    val isNameValid = name.isNotBlank()
    val isBalanceValid = balance.toDoubleOrNull() != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Wallet", style = MaterialTheme.typography.headlineSmall) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Choose Icon", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.padding(12.dp).heightIn(max = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(walletIcons.indices.toList()) { i ->
                        val isSelected = i == selectedIconIndex
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(if (isSelected) Primary else SurfaceContainerHighest)
                                .border(if (isSelected) 0.dp else 1.dp, OutlineVariant.copy(alpha = 0.15f), CircleShape)
                                .clickable { selectedIconIndex = i },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(walletIcons[i], contentDescription = null,
                                tint = if (isSelected) OnPrimary else OnSurfaceVariant,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            Text("Wallet Type", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                walletTypes.take(3).forEach { type ->
                    FilterChip(selected = selectedType == type, onClick = { selectedType = type },
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                walletTypes.drop(3).forEach { type ->
                    FilterChip(selected = selectedType == type, onClick = { selectedType = type },
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary))
                }
            }

            LedgerTextField(value = name, onValueChange = { name = it }, label = "Wallet Name",
                placeholder = "e.g. Checking Account", modifier = Modifier.fillMaxWidth(),
                isError = showErrors && !isNameValid,
                supportingText = if (showErrors && !isNameValid) "Wallet name is required" else null)
            LedgerTextField(
                value = balance,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' || c == '-' }) balance = it },
                label = "Initial Balance",
                leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = OnSurfaceVariant) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = showErrors && !isBalanceValid,
                supportingText = if (showErrors && !isBalanceValid) "Enter a valid balance (e.g. 0 or 1500.00)" else null)
            LedgerTextField(value = description, onValueChange = { description = it },
                label = "Description (optional)", modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    showErrors = true
                    if (isNameValid && isBalanceValid) {
                        viewModel.createWallet(name, description.ifBlank { selectedType }, balance.toDouble()) {
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Create Wallet", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
