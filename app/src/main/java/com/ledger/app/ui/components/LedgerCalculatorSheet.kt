package com.ledger.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerCalculatorSheet(
    initial: String = "",
    accentColor: Color = Primary,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var display by remember { mutableStateOf(if (initial.isNotBlank()) initial else "0") }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var storedVal by remember { mutableStateOf<Double?>(null) }
    var freshOperand by remember { mutableStateOf(false) }

    fun applyOp(a: Double, op: String, b: Double) = when (op) {
        "+" -> a + b
        "−" -> a - b
        "×" -> a * b
        "÷" -> if (b != 0.0) a / b else 0.0
        else -> b
    }

    fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

    fun onDigit(d: String) {
        if (display.length >= 12) return
        if (freshOperand) { display = d; freshOperand = false }
        else display = if (display == "0") d else display + d
    }

    fun onDecimal() {
        if (freshOperand) { display = "0."; freshOperand = false; return }
        if (!display.contains(".")) display += "."
    }

    fun onOperator(op: String) {
        val v = display.toDoubleOrNull() ?: 0.0
        if (storedVal != null && !freshOperand) {
            val result = applyOp(storedVal!!, pendingOp ?: op, v)
            display = fmt(result); storedVal = result
        } else {
            storedVal = v
        }
        pendingOp = op; freshOperand = true
    }

    fun onEquals() {
        val v = display.toDoubleOrNull() ?: 0.0
        if (storedVal != null && pendingOp != null) {
            display = fmt(applyOp(storedVal!!, pendingOp!!, v))
            storedVal = null; pendingOp = null; freshOperand = true
        }
    }

    fun onClear() { display = "0"; storedVal = null; pendingOp = null; freshOperand = false }
    fun onBackspace() { display = if (display.length <= 1) "0" else display.dropLast(1) }
    fun onPercent() { display = fmt((display.toDoubleOrNull() ?: 0.0) / 100.0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLowest,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Expression context
            Text(
                if (storedVal != null && pendingOp != null) "${fmt(storedVal!!)} $pendingOp" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
            // Main display
            Text(
                display,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp),
                color = OnSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                textAlign = TextAlign.End,
                maxLines = 1
            )
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

            val opBg = accentColor.copy(alpha = 0.13f)

            @Composable
            fun CalcBtn(
                label: String,
                bg: Color = SurfaceContainerHigh,
                fg: Color = OnSurface,
                widthWeight: Float = 1f,
                onClick: () -> Unit
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(widthWeight).height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                }
            }

            // Row 1: C  ⌫  %  ÷
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CalcBtn("C", SurfaceContainerHighest, Tertiary) { onClear() }
                Button(
                    onClick = { onBackspace() },
                    modifier = Modifier.weight(1f).height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest, contentColor = OnSurface),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) { Icon(Icons.Filled.Backspace, contentDescription = "Backspace", modifier = Modifier.size(24.dp)) }
                CalcBtn("%", SurfaceContainerHighest, OnSurfaceVariant) { onPercent() }
                CalcBtn("÷", opBg, accentColor) { onOperator("÷") }
            }
            // Row 2: 7  8  9  ×
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CalcBtn("7") { onDigit("7") }
                CalcBtn("8") { onDigit("8") }
                CalcBtn("9") { onDigit("9") }
                CalcBtn("×", opBg, accentColor) { onOperator("×") }
            }
            // Row 3: 4  5  6  −
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CalcBtn("4") { onDigit("4") }
                CalcBtn("5") { onDigit("5") }
                CalcBtn("6") { onDigit("6") }
                CalcBtn("−", opBg, accentColor) { onOperator("−") }
            }
            // Row 4: 1  2  3  +
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CalcBtn("1") { onDigit("1") }
                CalcBtn("2") { onDigit("2") }
                CalcBtn("3") { onDigit("3") }
                CalcBtn("+", opBg, accentColor) { onOperator("+") }
            }
            // Row 5: 0(wide)  .  =
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CalcBtn("0", widthWeight = 2f) { onDigit("0") }
                CalcBtn(".") { onDecimal() }
                CalcBtn("=", accentColor, Color.White) { onEquals() }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val v = display.toDoubleOrNull() ?: 0.0
                    onConfirm(fmt(v))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Use  \$$display", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
