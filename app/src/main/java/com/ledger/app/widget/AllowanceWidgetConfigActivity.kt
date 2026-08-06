package com.ledger.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.formatAmount
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Launched by the launcher when a Left Today widget is placed, and again on "reconfigure". Writes
// the tracked category into that instance's Glance state — see DailyAllowanceWidget.KEY_TRACKS.
@AndroidEntryPoint
class AllowanceWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Backing out must leave no widget behind, so the cancelled result is set up front.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

        setContent {
            LedgerTheme {
                var categories by remember { mutableStateOf<List<String>>(emptyList()) }
                var selected by remember { mutableStateOf(DailyAllowanceWidget.TRACK_ALL) }

                LaunchedEffect(Unit) {
                    // A widget can be placed before the app has ever run, so build the snapshot
                    // first rather than offering an empty list.
                    entryPoint.widgetUpdater().refresh()
                    val snapshot = entryPoint.widgetSnapshotRepository().snapshot.first()
                    categories = snapshot.categoryAllowances.map { it.name }
                    selected = runCatching {
                        val glanceId = GlanceAppWidgetManager(this@AllowanceWidgetConfigActivity)
                            .getGlanceIdBy(appWidgetId)
                        getAppWidgetState(
                            this@AllowanceWidgetConfigActivity,
                            PreferencesGlanceStateDefinition,
                            glanceId
                        )[DailyAllowanceWidget.KEY_TRACKS]
                    }.getOrNull() ?: DailyAllowanceWidget.TRACK_ALL
                }

                ConfigScreen(
                    categories = categories,
                    selected = selected,
                    onPick = { confirm(it) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun confirm(tracked: String) {
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@AllowanceWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@AllowanceWidgetConfigActivity, glanceId) { prefs ->
                    prefs[DailyAllowanceWidget.KEY_TRACKS] = tracked
                }
                DailyAllowanceWidget().update(this@AllowanceWidgetConfigActivity, glanceId)
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    categories: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What should this widget track?", style = MaterialTheme.typography.titleMedium) },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Place the widget again to track a second category.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )

            TrackOption(
                title = "All budgets",
                subtitle = "Everything combined, with a line for whichever budget is tightest",
                isSelected = selected == DailyAllowanceWidget.TRACK_ALL,
                onClick = { onPick(DailyAllowanceWidget.TRACK_ALL) }
            )

            if (categories.isEmpty()) {
                Text(
                    "No budgets yet — add one in Budgets and this widget can track it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            } else {
                Text(
                    "Single category",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                categories.forEach { name ->
                    TrackOption(
                        title = name,
                        subtitle = "That category's own daily allowance",
                        isSelected = selected.equals(name, ignoreCase = true),
                        onClick = { onPick(name) }
                    )
                }
            }

            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        }
    }
}

@Composable
private fun TrackOption(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Primary.copy(alpha = 0.10f) else SurfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            if (isSelected) Icon(Icons.Filled.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
        }
    }
}
