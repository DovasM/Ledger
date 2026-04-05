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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDebtScreen(navController: NavController, debtId: Long? = null) {
    val isEdit = debtId != null
    val types = listOf("Credit Card", "Personal Loan", "Auto Loan", "Mortgage", "Student Loan", "Other")
    var name by remember { mutableStateOf(if (isEdit) "Chase Sapphire" else "") }
    var selectedType by remember { mutableStateOf(if (isEdit) "Credit Card" else types[0]) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var totalAmount by remember { mutableStateOf(if (isEdit) "3000" else "") }
    var remaining by remember { mutableStateOf(if (isEdit) "1240" else "") }
    var apr by remember { mutableStateOf(if (isEdit) "24.99" else "") }
    var monthlyPayment by remember { mutableStateOf(if (isEdit) "200" else "") }
    var showCalc by remember { mutableStateOf(false) }
    var calcTarget by remember { mutableStateOf("total") } // which field the calc is for

    val monthsLeft = if (monthlyPayment.toDoubleOrNull() != null && monthlyPayment.toDouble() > 0 && remaining.toDoubleOrNull() != null)
        (remaining.toDouble() / monthlyPayment.toDouble()).toInt() else 0
    val totalInterest = if (monthsLeft > 0 && apr.toDoubleOrNull() != null && remaining.toDoubleOrNull() != null)
        remaining.toDouble() * (apr.toDouble() / 100.0 / 12.0) * monthsLeft else 0.0

    if (showCalc) {
        val current = when (calcTarget) { "total" -> totalAmount; "remaining" -> remaining; "payment" -> monthlyPayment; else -> apr }
        LedgerCalculatorSheet(
            initial = current, accentColor = Primary,
            onDismiss = { showCalc = false },
            onConfirm = { result ->
                when (calcTarget) { "total" -> totalAmount = result; "remaining" -> remaining = result; "payment" -> monthlyPayment = result; else -> apr = result }
                showCalc = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Debt" else "Add Debt", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LedgerTextField(value = name, onValueChange = { name = it }, label = "Debt name", placeholder = "e.g. Chase Sapphire", modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = typeMenuExpanded, onExpandedChange = { typeMenuExpanded = it }) {
                LedgerTextField(
                    value = selectedType, onValueChange = {},
                    label = "Type",
                    leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                    types.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { selectedType = t; typeMenuExpanded = false }) }
                }
            }

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Amounts", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    AmountRow("Total amount", totalAmount, Primary) { calcTarget = "total"; showCalc = true }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    AmountRow("Remaining balance", remaining, Tertiary) { calcTarget = "remaining"; showCalc = true }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    AmountRow("Monthly payment", monthlyPayment, Color(0xFF1565C0)) { calcTarget = "payment"; showCalc = true }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column { Text("Interest rate (APR %)", style = MaterialTheme.typography.bodyMedium, color = OnSurface) }
                        Surface(onClick = { calcTarget = "apr"; showCalc = true }, color = SurfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (apr.isBlank()) "0%" else "$apr%", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                Icon(Icons.Filled.Calculate, null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (monthsLeft > 0) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Payoff Projection", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Months to payoff", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Text("~$monthsLeft months", style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.Medium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Est. total interest", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            Text("${"$%,.2f".format(totalInterest)}", style = MaterialTheme.typography.bodySmall, color = Tertiary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isEdit) "Save Changes" else "Add Debt", style = MaterialTheme.typography.labelLarge)
            }
            if (isEdit) {
                OutlinedButton(
                    onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Tertiary.copy(alpha = 0.5f)))
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Debt")
                }
            }
        }
    }
}

@Composable
private fun AmountRow(label: String, value: String, color: Color, onTap: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
        Surface(onClick = onTap, color = SurfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (value.isBlank()) "$0" else "$$value", style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.Calculate, null, tint = color, modifier = Modifier.size(16.dp))
            }
        }
    }
}
