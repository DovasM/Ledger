package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class ImportRow(val date: String, val description: String, val amount: String, val category: String, val isExpense: Boolean)

private val previewRows = listOf(
    ImportRow("2026-03-28", "Whole Foods Market", "-\$67.45", "Food & Dining", true),
    ImportRow("2026-03-27", "Netflix", "-\$15.99", "Entertainment", true),
    ImportRow("2026-03-26", "Salary Deposit", "+\$5,200.00", "Salary", false),
    ImportRow("2026-03-25", "Shell Gas Station", "-\$52.00", "Transportation", true),
    ImportRow("2026-03-24", "Amazon", "-\$34.99", "Shopping", true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionImportScreen(navController: NavController) {
    var step by remember { mutableStateOf(0) } // 0=pick, 1=preview, 2=done
    var fileName by remember { mutableStateOf("") }
    var selectedRows by remember { mutableStateOf(previewRows.indices.toSet()) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Transactions", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Step indicator
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                listOf("Select file", "Review", "Done").forEachIndexed { i, label ->
                    val isActive = step >= i
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (isActive) Primary else SurfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (step > i) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (isActive) OnPrimary else OnSurfaceVariant)
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) Primary else OnSurfaceVariant)
                    }
                    if (i < 2) {
                        Box(modifier = Modifier.width(40.dp).height(1.dp).background(if (step > i) Primary else OutlineVariant).padding(bottom = 16.dp))
                    }
                }
            }

            when (step) {
                0 -> {
                    // File format info
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text("Supported formats", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            }
                            Text("CSV files from most banks are supported. The file should contain columns for date, description, and amount.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                            Text("Expected format:", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(SurfaceContainerHighest).padding(10.dp)) {
                                Text("date,description,amount\n2026-03-28,Groceries,-67.45\n2026-03-26,Salary,5200.00",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    color = OnSurface)
                            }
                        }
                    }

                    // Supported banks
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Works with", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            listOf("Bank of America", "Chase", "Wells Fargo", "Revolut", "Wise", "Any bank CSV export").forEach { bank ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Check, null, tint = Primary, modifier = Modifier.size(14.dp))
                                    Text(bank, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { fileName = "transactions_march_2026.csv"; step = 1 },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select CSV File", style = MaterialTheme.typography.labelLarge)
                    }
                }

                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.InsertDriveFile, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(fileName, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedRows.size} of ${previewRows.size} selected", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        TextButton(onClick = {
                            selectedRows = if (selectedRows.size == previewRows.size) emptySet() else previewRows.indices.toSet()
                        }) { Text(if (selectedRows.size == previewRows.size) "Deselect all" else "Select all") }
                    }

                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                            previewRows.forEachIndexed { idx, row ->
                                Surface(
                                    onClick = { selectedRows = if (idx in selectedRows) selectedRows - idx else selectedRows + idx },
                                    color = Color.Transparent, modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Checkbox(checked = idx in selectedRows, onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(checkedColor = Primary))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(row.description, style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text("${row.date} · ${row.category}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                        }
                                        Text(row.amount, style = MaterialTheme.typography.bodySmall, color = if (row.isExpense) Tertiary else Primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (idx < previewRows.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }

                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(6.dp),
                        enabled = selectedRows.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import ${selectedRows.size} Transactions", style = MaterialTheme.typography.labelLarge)
                    }
                }

                2 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Text("Import Successful", style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("${selectedRows.size} transactions have been imported and categorized.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                        Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(6.dp)) {
                            Text("Done", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
