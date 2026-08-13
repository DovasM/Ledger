package com.ledger.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.*

// Input field style: SurfaceContainerHighest bg, Ghost Border, label above
@Composable
fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    // So a field that adds something can act on the keyboard's done key rather than forcing a
    // reach for the button beside it.
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        },
        placeholder = if (placeholder.isNotEmpty()) ({
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }) else null,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        supportingText = if (supportingText != null) ({
            Text(supportingText, style = MaterialTheme.typography.labelSmall)
        }) else null,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = SurfaceContainerHighest,
            focusedContainerColor = SurfaceContainerHighest,
            errorContainerColor = SurfaceContainerHighest,
            unfocusedBorderColor = OutlineVariant.copy(alpha = 0.15f),
            focusedBorderColor = Primary,
            errorBorderColor = Error,
            unfocusedLabelColor = OnSurfaceVariant,
            focusedLabelColor = Primary,
            errorLabelColor = Error,
            cursorColor = Primary,
            errorCursorColor = Error,
        ),
        modifier = modifier,
    )
}
