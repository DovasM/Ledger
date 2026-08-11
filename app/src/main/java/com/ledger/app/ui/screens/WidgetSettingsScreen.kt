package com.ledger.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.DayState
import com.ledger.app.ui.util.formatCentsCompact
import com.ledger.app.ui.viewmodel.SettingsViewModel
import com.ledger.app.ui.viewmodel.WidgetSettingsViewModel
import com.ledger.app.widget.DailyAllowanceWidgetReceiver
import com.ledger.app.widget.QuickAddWidgetReceiver
import com.ledger.app.widget.StreakWidgetReceiver
import com.ledger.app.widget.WidgetSnapshot

private data class WidgetEntry(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val size: String,
    val provider: Class<*>
)

private val widgetEntries = listOf(
    WidgetEntry(
        "Quick Add", "Add a transaction, scan a receipt, or start from a frequent category",
        Icons.Filled.AddCircle, Primary, "4×1", QuickAddWidgetReceiver::class.java
    ),
    WidgetEntry(
        "Left Today", "All budgets combined, or a single category — chosen per widget when you place it",
        Icons.Filled.Savings, Color(0xFF1565C0), "2×2", DailyAllowanceWidgetReceiver::class.java
    ),
    WidgetEntry(
        "Streak", "Consecutive days within your daily allowance, plus this week's grid",
        Icons.Filled.LocalFireDepartment, Color(0xFFE65100), "2×2", StreakWidgetReceiver::class.java
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    navController: NavController,
    vm: WidgetSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val pinned by vm.pinnedCategories.collectAsStateWithLifecycle()
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val rollover by settingsViewModel.allowanceRollover.collectAsStateWithLifecycle()
    val window by settingsViewModel.allowanceWindow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widgets", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Widgets, null, tint = Primary, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Home Screen Widgets", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Widgets read a cached snapshot, so they stay fast and work even when the app isn't running. The snapshot refreshes whenever you add or edit data.",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                }
            }

            // A home screen is a public surface — someone glancing over your shoulder shouldn't
            // read your balance.
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.VisibilityOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide amounts", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("Replace every figure with ••• on the home screen", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Switch(
                        checked = snapshot.hideAmounts,
                        onCheckedChange = { vm.setHideAmounts(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary)
                    )
                }
            }

            Text("Available Widgets", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)

            widgetEntries.forEach { entry ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(entry.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(entry.icon, null, tint = entry.color, modifier = Modifier.size(22.dp)) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                Text(entry.size, style = MaterialTheme.typography.labelSmall, color = entry.color, fontWeight = FontWeight.Medium)
                            }
                        }

                        WidgetPreview(entry, snapshot)

                        if (entry.provider == DailyAllowanceWidgetReceiver::class.java) {
                            AllowanceSettingsPicker(
                                rollover = rollover,
                                window = window,
                                onRollover = settingsViewModel::setAllowanceRollover,
                                onWindow = settingsViewModel::setAllowanceWindow
                            )
                        }

                        if (entry.provider == QuickAddWidgetReceiver::class.java) {
                            CategoryShortcutPicker(
                                available = expenseCategories,
                                pinned = pinned,
                                max = vm.maxPinned,
                                onToggle = vm::toggleCategory,
                                onAutomatic = vm::useAutomaticCategories
                            )
                        }

                        OutlinedButton(
                            onClick = { requestPin(context, entry.provider) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = entry.color)
                        ) {
                            Icon(Icons.Filled.AddToHomeScreen, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add to home screen")
                        }
                    }
                }
            }

            Text(
                "You can also long-press an empty spot on the home screen and pick Ledger from the widget list.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun AllowanceSettingsPicker(
    rollover: Boolean,
    window: String,
    onRollover: (Boolean) -> Unit,
    onWindow: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Carry days forward", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    if (rollover) "A cheap day leaves more for tomorrow; an expensive one leaves less"
                    else "Today never exceeds the plain daily share, but overspending still lowers it",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            Switch(
                checked = rollover,
                onCheckedChange = onRollover,
                colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary)
            )
        }
        if (rollover) {
            Text("Reset the carried balance", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("weekly" to "Weekly", "monthly" to "Monthly").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = window == value,
                        onClick = { onWindow(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                    ) { Text(label) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryShortcutPicker(
    available: List<String>,
    pinned: List<String>,
    max: Int,
    onToggle: (String) -> Unit,
    onAutomatic: () -> Unit
) {
    if (available.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Shortcut categories", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    if (pinned.isEmpty()) "Automatic — your $max most-used categories"
                    else "Pick up to $max; choosing a third replaces the oldest",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            if (pinned.isNotEmpty()) {
                TextButton(onClick = onAutomatic) { Text("Automatic") }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            available.forEach { name ->
                val selected = pinned.any { it.equals(name, ignoreCase = true) }
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(name) },
                    label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary,
                        selectedLeadingIconColor = Primary
                    )
                )
            }
        }
    }
}

// Live preview using the same snapshot the real widget renders from, so what's shown here is what
// lands on the home screen — no mock figures.
@Composable
private fun WidgetPreview(entry: WidgetEntry, snapshot: WidgetSnapshot) {
    fun money(amountCents: Long) =
        if (snapshot.hideAmounts) "•••" else formatCentsCompact(amountCents, snapshot.currency, snapshot.numberFormat)

    Box(
        modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        when (entry.provider) {
            QuickAddWidgetReceiver::class.java -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(Primary),
                    contentAlignment = Alignment.Center
                ) { Text("+ Add", style = MaterialTheme.typography.labelLarge, color = OnPrimary) }
                if (snapshot.aiEnabled) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.DocumentScanner, null, tint = Primary, modifier = Modifier.size(20.dp)) }
                }
                snapshot.topCategories.take(2).forEach { shortcut ->
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(shortcut.name, style = MaterialTheme.typography.labelSmall, color = OnSurface, maxLines = 1)
                    }
                }
            }

            DailyAllowanceWidgetReceiver::class.java -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (snapshot.hasAllowance) {
                    Text("LEFT TODAY", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(
                        money(snapshot.remainingTodayCents),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (snapshot.remainingTodayCents < 0) Tertiary else Primary,
                        fontWeight = FontWeight.Bold
                    )
                    val carryDelta = snapshot.todayAllowanceCents - snapshot.baseDailyCents
                    when {
                        carryDelta >= 0.5 ->
                            Text("+${money(carryDelta)}/day carried", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        carryDelta <= -0.5 ->
                            Text("${money(carryDelta)}/day carried", style = MaterialTheme.typography.labelSmall, color = Tertiary)
                        snapshot.unbudgetedTodayCents > 0 ->
                            Text("+${money(snapshot.unbudgetedTodayCents)} unbudgeted", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        else ->
                            Text("of ${money(snapshot.todayAllowanceCents)}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    snapshot.tightestCategory?.let { tight ->
                        Text(
                            if (snapshot.tightestRemainingCents < 0) "$tight over ${money(-snapshot.tightestRemainingCents)}"
                            else "$tight ${money(snapshot.tightestRemainingCents)} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snapshot.tightestAlerting) Tertiary else OnSurface
                        )
                    }
                } else {
                    Text("BALANCE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Text(money(snapshot.totalBalanceCents), style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                    Text("set a budget for a daily figure", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }

            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (snapshot.streakCurrent >= 7) "🔥" else "✅", fontSize = 16.sp)
                    Text(
                        "${snapshot.streakCurrent}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (snapshot.streakCurrent >= 7) Color(0xFFE65100) else Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    snapshot.weekGrid.forEach { day ->
                        Box(
                            modifier = Modifier.size(10.dp).clip(CircleShape).background(
                                when (day) {
                                    DayState.Good   -> if (snapshot.streakCurrent >= 7) Color(0xFFE65100) else Primary
                                    DayState.Over   -> Tertiary
                                    DayState.Empty  -> SurfaceContainerHighest
                                    DayState.Future -> SurfaceContainerHigh.copy(alpha = 0.5f)
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

// API 26+ lets the app offer a one-tap pin, but the launcher may refuse (many third-party ones do),
// so the fallback instruction stays visible at the bottom of the screen.
private fun requestPin(context: Context, provider: Class<*>) {
    val manager = context.getSystemService(AppWidgetManager::class.java)
    val supported = manager != null && manager.isRequestPinAppWidgetSupported
    if (supported) {
        manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    } else {
        Toast.makeText(
            context,
            "Your launcher doesn't support adding widgets from inside apps — long-press the home screen instead.",
            Toast.LENGTH_LONG
        ).show()
    }
}
