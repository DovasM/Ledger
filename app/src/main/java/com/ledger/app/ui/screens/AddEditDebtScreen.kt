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
    var calcTarget by remember { mutableStateOf("total") }
    var showErrors by remember { mutableStateOf(false) }

    val totalVal = totalAmount.toDoubleOrNull()
    val remainingVal = remaining.toDoubleOrNull()
    val aprVal = apr.toDoubleOrNull()
    val paymentVal = monthlyPayment.toDoubleOrNull()

    val isNameValid = name.isNotBlank()
    val isTotalValid = totalVal != null && totalVal > 0
    val isRemainingValid = remainingVal != null && remainingVal >= 0 && (totalVal == null || remainingVal <= totalVal)
    val isAprValid = aprVal != null && aprVal >= 0
    val isPaymentValid = paymentVal != null && paymentVal > 0
    val isFormValid = isNameValid && isTotalValid && isRemainingValid && isAprValid && isPaymentValid

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
            LedgerTextField(
                value = name, onValueChange = { name = it },
                label = "Debt name", placeholder = "e.g. Chase Sapphire",
                modifier = Modifier.fillMaxWidth(),
                isError = showErrors && !isNameValid,
                supportingText = if (showErrors && !isNameValid) "Name is required" else null
            )

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
                    AmountRow("Total amount", totalAmount, Primary,
                        errorText = if (showErrors && !isTotalValid) "Must be greater than 0" else null
                    ) { calcTarget = "total"; showCalc = true }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    AmountRow("Remaining balance", remaining, Tertiary,
                        errorText = if (showErrors && !isRemainingValid) "Must be 0–total amount" else null
                    ) { calcTarget = "remaining"; showCalc = true }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    AmountRow("Monthly payment", monthlyPayment, Color(0xFF1565C0),
                        errorText = if (showErrors && !isPaymentValid) "Must be greater than 0" else null
                    ) { calcTarget = "payment"; showCalc = true }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.2f))
                    AmountRow("Interest rate (APR %)", if (apr.isBlank()) "" else apr,
                        Color(0xFFE65100),
                        errorText = if (showErrors && !isAprValid) "APR must be 0 or greater" else null
                    ) { calcTarget = "apr"; showCalc = true }
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
                onClick = {
                    showErrors = true
                    if (isFormValid) navController.popBackStack()
                },
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
private fun AmountRow(label: String, value: String, color: Color, errorText: String? = null, onTap: () -> Unit) {
    val hasError = errorText != null
    val displayColor = if (hasError) Error else color
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (hasError) Error else OnSurface)
            if (hasError) Text(errorText!!, style = MaterialTheme.typography.labelSmall, color = Error)
        }
        Surface(onClick = onTap, color = if (hasError) Error.copy(alpha = 0.06f) else SurfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (value.isBlank()) "$0" else "$$value", style = MaterialTheme.typography.titleSmall, color = displayColor, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.Calculate, null, tint = displayColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}
