package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWalletScreen(navController: NavController, walletId: Long) {
    var name by remember { mutableStateOf("Checking Account") }
    var balance by remember { mutableStateOf("12430.50") }
    var description by remember { mutableStateOf("Bank of America") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Wallet") },
            text = { Text("All transactions in this wallet will be unlinked.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Delete", color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Primary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Wallet", style = MaterialTheme.typography.headlineSmall) },
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LedgerTextField(value = name, onValueChange = { name = it }, label = "Wallet Name", modifier = Modifier.fillMaxWidth())
            LedgerTextField(
                value = balance, onValueChange = { balance = it }, label = "Balance",
                leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = OnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth()
            )
            LedgerTextField(value = description, onValueChange = { description = it }, label = "Description", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Changes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
