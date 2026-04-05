package com.ledger.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.SurfaceContainerHighest

// Standard interactive card — SurfaceContainerHighest, md rounding, no border
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content
        )
    }
}

// Floating card — SurfaceContainerLowest, used for totals / highlighted data
@Composable
fun LedgerFloatingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = content
        )
    }
}
