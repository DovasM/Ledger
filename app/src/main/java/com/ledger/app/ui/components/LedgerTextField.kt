package com.ledger.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        placeholder = if (placeholder.isNotEmpty()) ({
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }) else null,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = SurfaceContainerHighest,
            focusedContainerColor = SurfaceContainerHighest,
            unfocusedBorderColor = OutlineVariant.copy(alpha = 0.15f),
            focusedBorderColor = Primary,
            unfocusedLabelColor = OnSurfaceVariant,
            focusedLabelColor = Primary,
            cursorColor = Primary,
        ),
        modifier = modifier,
    )
}
