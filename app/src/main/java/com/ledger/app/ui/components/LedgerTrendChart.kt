package com.ledger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.*
import uniffi.ledger.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Reusable line chart showing actual balance over time.
 * Long-press + drag to inspect any point on the line.
 *
 * @param transactions    All transactions to plot (pre-filter by walletId if needed).
 * @param currentBalance  The known balance right now — used to reconstruct historical values.
 * @param periods         Period options shown as filter chips.
 * @param defaultPeriod   Initially selected period.
 * @param accentColor     Line/fill color override; defaults to Primary/Tertiary based on trend.
 * @param onPeriodChanged Called whenever the user selects a different period, with the new start date.
 */
@Composable
fun LedgerTrendChart(
    transactions: List<Transaction>,
    currentBalance: Double,
    modifier: Modifier = Modifier,
    periods: List<String> = listOf("1W", "1M", "3M", "All"),
    defaultPeriod: String = "1M",
    accentColor: Color? = null,
    onPeriodChanged: (startDate: LocalDate) -> Unit = {}
) {
    var selected by remember { mutableStateOf(defaultPeriod) }
    val today = LocalDate.now()

    val periodStart = when (selected) {
        "1W"  -> today.minusDays(6)
        "1M"  -> today.withDayOfMonth(1)
        "3M"  -> today.minusDays(89)
        "6M"  -> today.minusDays(179)
        "1Y"  -> today.minusDays(364)
        else  -> transactions
            .mapNotNull { runCatching { LocalDate.parse(it.createdAt.take(10)) }.getOrNull() }
            .minOrNull() ?: today.minusDays(29)
    }

    LaunchedEffect(periodStart) {
        onPeriodChanged(periodStart)
    }

    val days = generateSequence(periodStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()

    val netByDay = transactions
        .mapNotNull { tx ->
            runCatching { LocalDate.parse(tx.createdAt.take(10)) }.getOrNull()
                ?.let { it.format(DateTimeFormatter.ISO_LOCAL_DATE) to tx }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, txs) -> txs.sumOf { if (it.isIncome) it.amount else -it.amount } }

    val netAfterByDay = mutableMapOf<LocalDate, Double>()
    var cumulativeAfter = 0.0
    for (day in days.reversed()) {
        val nextDay = day.plusDays(1)
        val netNextDay = netByDay[nextDay.format(DateTimeFormatter.ISO_LOCAL_DATE)] ?: 0.0
        cumulativeAfter += netNextDay
        netAfterByDay[day] = cumulativeAfter
    }
    val points = days.map { day ->
        (currentBalance - (netAfterByDay[day] ?: 0.0)).toFloat()
    }

    val trend = if (points.size >= 2) points.last() - points.first() else 0f
    val lineColor = accentColor ?: if (trend >= 0f) Primary else Tertiary

    // ── Inspection state ───────────────────────────────────────────────────────
    var inspecting by remember { mutableStateOf(false) }
    var inspectFraction by remember { mutableStateOf(1f) }
    var canvasWidthPx by remember { mutableStateOf(1f) }

    val inspectIdx = if (points.isNotEmpty())
        (inspectFraction * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
    else 0
    val inspectValue = points.getOrNull(inspectIdx) ?: 0f
    val inspectDate = days.getOrNull(inspectIdx)

    Column(modifier = modifier.fillMaxWidth()) {
        // Period filter chips — equal width, full row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            periods.forEach { p ->
                val isSelected = p == selected
                Surface(
                    onClick = { selected = p },
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) lineColor.copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        p,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) lineColor else OnSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (points.size < 2) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Not enough data", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            return@Column
        }

        val minVal = points.min()
        val maxVal = points.max()

        // ── Inspect tooltip row ────────────────────────────────────────────────
        val tooltipHeight = 28.dp
        Box(modifier = Modifier.fillMaxWidth().height(tooltipHeight)) {
            if (inspecting && inspectDate != null) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lineColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            inspectDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lineColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            formatCompact(inspectValue),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = lineColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Chart ─────────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            // Max at top-left, min at bottom-left — surface background keeps them readable over the line
            androidx.compose.material3.Surface(
                modifier = Modifier.align(Alignment.TopStart),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(formatCompact(maxVal), modifier = Modifier.padding(horizontal = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            androidx.compose.material3.Surface(
                modifier = Modifier.align(Alignment.BottomStart),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(formatCompact(minVal), modifier = Modifier.padding(horizontal = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }

            val lineColorCopy = lineColor
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(points.size) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        inspecting = true
                                        inspectFraction = (pos.x / canvasWidthPx).coerceIn(0f, 1f)
                                    }
                                    PointerEventType.Move -> if (inspecting) {
                                        inspectFraction = (pos.x / canvasWidthPx).coerceIn(0f, 1f)
                                        event.changes.forEach { it.consume() }
                                    }
                                    PointerEventType.Release -> inspecting = false
                                    else -> {}
                                }
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val range = (maxVal - minVal).takeIf { it > 0f } ?: 1f
                fun xOf(i: Int) = w * i / (points.size - 1)
                fun yOf(v: Float) = h - ((v - minVal) / range) * h * 0.8f - h * 0.1f

                // Fill
                val fill = Path().apply {
                    val zeroY = yOf(0f).coerceIn(0f, h)
                    moveTo(xOf(0), zeroY)
                    lineTo(xOf(0), yOf(points[0]))
                    for (i in 1 until points.size) {
                        val cx = (xOf(i - 1) + xOf(i)) / 2f
                        cubicTo(cx, yOf(points[i - 1]), cx, yOf(points[i]), xOf(i), yOf(points[i]))
                    }
                    lineTo(xOf(points.size - 1), zeroY)
                    close()
                }
                drawPath(fill, brush = Brush.verticalGradient(
                    listOf(lineColorCopy.copy(alpha = 0.25f), Color.Transparent), startY = 0f, endY = h
                ))

                // Line
                val line = Path().apply {
                    moveTo(xOf(0), yOf(points[0]))
                    for (i in 1 until points.size) {
                        val cx = (xOf(i - 1) + xOf(i)) / 2f
                        cubicTo(cx, yOf(points[i - 1]), cx, yOf(points[i]), xOf(i), yOf(points[i]))
                    }
                }
                drawPath(line, color = lineColorCopy, style = Stroke(width = 2.dp.toPx()))

                if (inspecting) {
                    val ix = xOf(inspectIdx)
                    val iy = yOf(points[inspectIdx])

                    // Dashed vertical line
                    drawLine(
                        color = lineColorCopy.copy(alpha = 0.5f),
                        start = Offset(ix, 0f),
                        end = Offset(ix, h),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )

                    // Outer ring
                    drawCircle(color = lineColorCopy.copy(alpha = 0.2f), radius = 7.dp.toPx(), center = Offset(ix, iy))
                    // Filled dot
                    drawCircle(color = lineColorCopy, radius = 4.dp.toPx(), center = Offset(ix, iy))
                    // White centre
                    drawCircle(color = Color.White, radius = 2f.dp.toPx(), center = Offset(ix, iy))
                } else {
                    // Default end dot
                    drawCircle(
                        color = lineColorCopy,
                        radius = 3.5.dp.toPx(),
                        center = Offset(xOf(points.size - 1), yOf(points.last()))
                    )
                }
            }

        }
    }
}

private fun formatCompact(value: Float): String {
    val abs = kotlin.math.abs(value)
    val prefix = if (value < 0) "-$" else "$"
    return when {
        abs >= 1_000_000 -> "$prefix${"%.1f".format(abs / 1_000_000)}M"
        abs >= 1_000     -> "$prefix${"%.1f".format(abs / 1_000)}K"
        else             -> "$prefix${"%.0f".format(abs)}"
    }
}
