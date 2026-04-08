package com.ledger.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.ui.theme.*

/**
 * Styled large amount input that opens a numpad by default.
 * A small calculator icon lets the user open the full LedgerCalculatorSheet instead.
 */
@Composable
fun LedgerAmountField(
    amount: String,
    onAmountChange: (String) -> Unit,
    onCalculatorOpen: () -> Unit,
    prefix: String = "$",
    accentColor: Color = Primary,
    showError: Boolean = false,
    errorMessage: String = "Amount must be greater than 0",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text(
                prefix,
                style = MaterialTheme.typography.headlineMedium,
                color = if (showError) Error else accentColor,
                fontWeight = FontWeight.Light
            )
            BasicTextField(
                value = amount,
                onValueChange = { v ->
                    if (v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1)
                        onAmountChange(v)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 60.sp,
                    color = if (showError) Error else accentColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                decorationBox = { innerTextField ->
                    if (amount.isBlank()) {
                        Text(
                            "0.00",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp),
                            color = if (showError) Error.copy(alpha = 0.4f) else OutlineVariant,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        innerTextField()
                    }
                }
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showError) {
                Icon(Icons.Filled.Error, null, tint = Error, modifier = Modifier.size(14.dp))
                Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = Error)
            } else {
                Text("Enter amount", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                IconButton(
                    onClick = onCalculatorOpen,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Calculate, null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
