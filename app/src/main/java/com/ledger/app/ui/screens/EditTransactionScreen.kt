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
fun EditTransactionScreen(navController: NavController, transactionId: Long) {
    var amount by remember { mutableStateOf("1200.00") }
    var title by remember { mutableStateOf("Rent") }
    var category by remember { mutableStateOf("Housing") }
    var note by remember { mutableStateOf("Monthly rent payment") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction") },
            text = { Text("This action cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LedgerTextField(
                value = amount,
                onValueChange = { amount = it },
                label = "Amount",
                leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = OnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth()
            )
            LedgerTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
                modifier = Modifier.fillMaxWidth()
            )
            LedgerTextField(
                value = category,
                onValueChange = { category = it },
                label = "Category",
                modifier = Modifier.fillMaxWidth()
            )
            LedgerTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Save Changes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
