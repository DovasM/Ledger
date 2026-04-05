package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class CalEvent(val day: Int, val label: String, val amount: String, val type: Int) // 0=expense,1=income,2=recurring
private val events = listOf(
    CalEvent(1, "Salary",           "+\$5,200", 1),
    CalEvent(1, "Rent",             "-\$1,200", 2),
    CalEvent(3, "Groceries",        "-\$67",    0),
    CalEvent(5, "Netflix",          "-\$15.99", 2),
    CalEvent(7, "Spotify",          "-\$9.99",  2),
    CalEvent(10,"Dinner out",       "-\$48",    0),
    CalEvent(12,"Pharmacy",         "-\$25",    0),
    CalEvent(14,"Freelance income", "+\$800",   1),
    CalEvent(15,"Car insurance",    "-\$120",   2),
    CalEvent(18,"Amazon",           "-\$35",    0),
    CalEvent(20,"Electricity bill", "-\$88",    2),
    CalEvent(25,"Transport pass",   "-\$90",    2),
    CalEvent(28,"Gym",              "-\$45",    2),
    CalEvent(30,"Savings transfer", "-\$500",   2),
)

private val eventColors = listOf(Tertiary, Primary, Color(0xFF1565C0))
private val eventTypeLabels = listOf("Expense", "Income", "Recurring")
private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialCalendarScreen(navController: NavController) {
    var selectedMonth by remember { mutableStateOf(3) } // April = index 3
    var selectedYear by remember { mutableStateOf(2026) }
    var selectedDay by remember { mutableStateOf(5) }

    val months = listOf("January","February","March","April","May","June","July","August","September","October","November","December")

    // April 2026: starts on Wednesday (index 2 in Mon-first), 30 days
    val firstDayOffset = 2
    val daysInMonth = 30

    val dayEvents = events.groupBy { it.day }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Calendar", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month navigator
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { if (selectedMonth > 0) selectedMonth-- else { selectedMonth = 11; selectedYear-- } }) {
                            Icon(Icons.Filled.ChevronLeft, null, tint = OnSurfaceVariant)
                        }
                        Text("${months[selectedMonth]} $selectedYear", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { if (selectedMonth < 11) selectedMonth++ else { selectedMonth = 0; selectedYear++ } }) {
                            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant)
                        }
                    }

                    // Day-of-week headers
                    Row(modifier = Modifier.fillMaxWidth()) {
                        dayNames.forEach { d ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(d, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center, fontSize = 10.sp)
                            }
                        }
                    }

                    // Calendar grid
                    val totalCells = firstDayOffset + daysInMonth
                    val rows = (totalCells + 6) / 7
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val cellIndex = row * 7 + col
                                    val day = cellIndex - firstDayOffset + 1
                                    Box(modifier = Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.TopCenter) {
                                        if (day in 1..daysInMonth) {
                                            val isSelected = day == selectedDay
                                            val hasEvents = dayEvents.containsKey(day)
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) Primary else Color.Transparent)
                                                    .padding(4.dp)
                                            ) {
                                                Surface(
                                                    onClick = { selectedDay = day },
                                                    color = Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        "$day", style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) OnPrimary else OnSurface,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(4.dp)
                                                    )
                                                }
                                                if (hasEvents) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        dayEvents[day]!!.take(3).forEach { e ->
                                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(
                                                                if (isSelected) OnPrimary.copy(alpha = 0.8f) else eventColors[e.type]
                                                            ))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Legend
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        eventColors.forEachIndexed { i, color ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                Text(eventTypeLabels[i], style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Selected day events
            val todayEvents = dayEvents[selectedDay]
            if (todayEvents != null) {
                Text("${months[selectedMonth]} $selectedDay", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        todayEvents.forEachIndexed { idx, event ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(eventColors[event.type]))
                                Text(event.label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
                                Text(event.amount, style = MaterialTheme.typography.bodyMedium,
                                    color = if (event.type == 1) Primary else OnSurface,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            if (idx < todayEvents.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        }
                    }
                }
            } else {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No events on ${months[selectedMonth]} $selectedDay", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            // Month summary
            val monthIncome = events.filter { it.type == 1 }.sumOf { it.amount.replace("+\$","").replace(",","").toDoubleOrNull() ?: 0.0 }
            val monthExpense = events.filter { it.type != 1 }.sumOf { it.amount.replace("-\$","").replace(",","").toDoubleOrNull() ?: 0.0 }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("INCOME", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("+${"$%,.0f".format(monthIncome)}", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("EXPENSES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("-${"$%,.0f".format(monthExpense)}", style = MaterialTheme.typography.titleMedium, color = Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("NET", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        val net = monthIncome - monthExpense
                        Text("${"$%,.0f".format(net)}", style = MaterialTheme.typography.titleMedium, color = if (net >= 0) Primary else Tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
