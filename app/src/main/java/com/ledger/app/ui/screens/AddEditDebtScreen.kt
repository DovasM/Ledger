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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.DebtViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDebtScreen(
    navController: NavController,
    debtId: String? = null,
    viewModel: DebtViewModel = hiltViewModel()
) {
    val isEdit = debtId != null
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existingDebt = if (isEdit) state.debts.find { it.id == debtId } else null

    val types = listOf("Credit Card", "Personal Loan", "Auto Loan", "Mortgage", "Student Loan", "Other")

    var name           by remember(existingDebt) { mutableStateOf(existingDebt?.name ?: "") }
    var selectedType   by remember(existingDebt) { mutableStateOf(existingDebt?.debtType ?: types[0]) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var totalAmount    by remember(existingDebt) { mutableStateOf(existingDebt?.totalAmount?.toString() ?: "") }
    var remaining      by remember(existingDebt) { mutableStateOf(existingDebt?.remainingAmount?.toString() ?: "") }
    var apr            by remember(existingDebt) { mutableStateOf(existingDebt?.apr?.toString() ?: "") }
    var monthlyPayment by remember(existingDebt) { mutableStateOf(existingDebt?.monthlyPayment?.toString() ?: "") }
    var showCalc       by remember { mutableStateOf(false) }
    var calcTarget     by remember { mutableStateOf("total") }
    var showErrors     by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val totalVal     = totalAmount.toDoubleOrNull()
    val remainingVal = remaining.toDoubleOrNull()
    val aprVal       = apr.toDoubleOrNull()
    val paymentVal   = monthlyPayment.toDoubleOrNull()

    val isNameValid      = name.isNotBlank()
    val isTotalValid     = totalVal != null && totalVal > 0
    val isRemainingValid = remainingVal != null && remainingVal >= 0 && (totalVal == null || remainingVal <= totalVal)
    val isAprValid       = aprVal != null && aprVal >= 0
    val isPaymentValid   = paymentVal != null && paymentVal > 0
    val isFormValid      = isNameValid && isTotalValid && isRemainingValid && isAprValid && isPaymentValid

    val monthsLeft = if (paymentVal != null && paymentVal > 0 && remainingVal != null) (remainingVal / paymentVal).toInt() else 0
    val totalInterest = if (monthsLeft > 0 && aprVal != null && remainingVal != null)
        remainingVal * (aprVal / 100.0 / 12.0) * monthsLeft else 0.0

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Debt") },
            text = { Text("Delete \"$name\"? This cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDebt(debtId!!) { navController.popBackStack() }
                }) { Text("Delete", color = Tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
            }
        )
    }

    if (showCalc) {
        val current = when (calcTarget) { "total" -> totalAmount; "remaining" -> remaining; "payment" -> monthlyPayment; else -> apr }
        LedgerCalculatorSheet(
            initial = current, accentColor = Primary,
            onDismiss = { showCalc = false },
            onConfirm = { result ->
                when (calcTarget) {
                    "total"     -> totalAmount    = result
                    "remaining" -> remaining      = result
                    "payment"   -> monthlyPayment = result
                    else        -> apr            = result
                }
                showCalc = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Debt" else "Add Debt", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                    leadingIcon = { Icon(Icons.Filled.CreditCard, null, tint = OnSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                    types.forEach { t ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { selectedType = t; typeMenuExpanded = false })
                    }
                }
            }

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Amounts", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    AmountRow("Total amount", totalAmount, { totalAmount = it }, Primary,
                        hint = "Original loan or credit limit",
                        errorText = if (showErrors && !isTotalValid) "Must be greater than 0" else null
                    ) { calcTarget = "total"; showCalc = true }
                    AmountRow("Remaining balance", remaining, { remaining = it }, Tertiary,
                        hint = "What you still owe today",
                        errorText = if (showErrors && !isRemainingValid) "Must be 0–total amount" else null
                    ) { calcTarget = "remaining"; showCalc = true }
                    AmountRow("Monthly payment", monthlyPayment, { monthlyPayment = it }, Color(0xFF1565C0),
                        hint = "Your regular monthly payment",
                        errorText = if (showErrors && !isPaymentValid) "Must be greater than 0" else null
                    ) { calcTarget = "payment"; showCalc = true }
                    AmountRow("Interest rate (APR %)", apr, { apr = it }, Color(0xFFE65100),
                        hint = "Annual percentage rate (e.g. 19.99)",
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
                    if (isFormValid) {
                        if (isEdit && debtId != null) {
                            viewModel.updateDebt(debtId, name, selectedType, totalVal!!, remainingVal!!, aprVal!!, paymentVal!!) {
                                navController.popBackStack()
                            }
                        } else {
                            viewModel.createDebt(name, selectedType, totalVal!!, remainingVal!!, aprVal!!, paymentVal!!) {
                                navController.popBackStack()
                            }
                        }
                    }
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
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
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
private fun AmountRow(
    label: String, value: String, onValueChange: (String) -> Unit,
    color: Color, hint: String? = null, errorText: String? = null, onCalcTap: () -> Unit
) {
    val hasError = errorText != null
    LedgerTextField(
        value = value,
        onValueChange = { v ->
            if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1) onValueChange(v)
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = hasError,
        supportingText = if (hasError) errorText else hint,
        trailingIcon = {
            IconButton(onClick = onCalcTap, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Calculate, null, tint = if (hasError) Error else color, modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
