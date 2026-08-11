package com.ledger.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.util.formatCents
import com.ledger.app.ui.util.formatCentsCompact
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

// Balance is a number people already know. What they can still spend today without breaking the
// budget is the one they open an app to find — so that is what this widget leads with.
//
// Each placed instance decides what it tracks: every budget together, or one category. The choice is
// per-instance state (keyed by GlanceId), not a shared preference — a shared one would change every
// placed copy at once, which defeats the point of placing two.
class DailyAllowanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetSnapshotRepository()
            .snapshot
            .first()

        val tracked = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_TRACKS]
            ?: TRACK_ALL

        provideContent { Content(snapshot, tracked) }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot, tracked: String) {
        val context = LocalContext.current
        // A tracked category that has since been deleted or renamed falls back to the summary
        // rather than rendering an empty widget.
        val category = snapshot.categoryAllowances
            .firstOrNull { tracked != TRACK_ALL && it.name.equals(tracked, ignoreCase = true) }

        val target = when {
            category != null      -> Screen.Budgets.route
            snapshot.hasAllowance -> Screen.Budgets.route
            else                  -> Screen.WalletsList.route
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.surface)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(widgetRouteIntent(context, target))),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            when {
                !snapshot.hasData     -> EmptyState()
                category != null      -> CategoryState(snapshot, category)
                snapshot.hasAllowance -> AllowanceState(snapshot)
                else                  -> BalanceState(snapshot)
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Text(
            "Open Ledger\nto get started",
            style = TextStyle(
                color = WidgetColors.onSurfaceMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        )
    }

    @Composable
    private fun AllowanceState(snapshot: WidgetSnapshot) {
        val remaining = snapshot.remainingTodayCents
        val overspent = remaining < 0

        Label("LEFT TODAY")
        BigAmount(snapshot.money(remaining, compact = true), overspent)
        Bar(snapshot.spentTodayCents, snapshot.todayAllowanceCents, overspent)
        Spacer(GlanceModifier.height(6.dp))
        Footnote(snapshot)

        // Option C: the summary always names the budget furthest through its limit, so the widget
        // answers "what should I watch" before anything has gone wrong.
        val tight = snapshot.tightestCategory
        if (tight != null && LocalSize.current.height >= FULL.height) {
            Divider()
            Text(
                if (snapshot.tightestRemainingCents < 0)
                    "$tight over ${snapshot.money(-snapshot.tightestRemainingCents, compact = true)}"
                else "$tight ${snapshot.money(snapshot.tightestRemainingCents, compact = true)} left",
                maxLines = 1,
                style = TextStyle(
                    color = if (snapshot.tightestAlerting) WidgetColors.alert else WidgetColors.onSurface,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }

    // Option A: the whole widget is one category, so the headline figure is directly actionable.
    @Composable
    private fun CategoryState(snapshot: WidgetSnapshot, category: CategoryAllowance) {
        val remaining = category.remainingTodayCents
        val overspent = remaining < 0

        Label("${category.name.uppercase()} · TODAY")
        BigAmount(snapshot.money(remaining, compact = true), overspent)
        Bar(category.spentTodayCents, category.todayAllowanceCents, overspent)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            "of ${snapshot.money(category.todayAllowanceCents, compact = true)}",
            style = TextStyle(color = WidgetColors.onSurfaceMuted, fontSize = 11.sp)
        )
        if (LocalSize.current.height >= FULL.height) {
            Divider()
            Text(
                if (category.periodRemainingCents < 0)
                    "${snapshot.money(-category.periodRemainingCents, compact = true)} over this ${category.periodLabel}"
                else "${snapshot.money(category.periodRemainingCents, compact = true)} left this ${category.periodLabel}",
                maxLines = 1,
                style = TextStyle(
                    color = if (category.periodRemainingCents < 0) WidgetColors.alert else WidgetColors.onSurface,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }

    // No budgets configured — show what we can honestly show, and say why.
    @Composable
    private fun BalanceState(snapshot: WidgetSnapshot) {
        Label("BALANCE")
        Text(
            snapshot.money(snapshot.totalBalanceCents, compact = true),
            style = TextStyle(color = WidgetColors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .background(WidgetColors.surfaceRaised)
                .cornerRadius(10.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Set a budget",
                style = TextStyle(color = WidgetColors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            )
        }
    }

    @Composable
    private fun Label(text: String) {
        Text(
            text,
            maxLines = 1,
            style = TextStyle(color = WidgetColors.onSurfaceMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        )
        Spacer(GlanceModifier.height(4.dp))
    }

    @Composable
    private fun BigAmount(text: String, overspent: Boolean) {
        Text(
            text,
            style = TextStyle(
                color = if (overspent) WidgetColors.alert else WidgetColors.primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.height(8.dp))
    }

    @Composable
    private fun Bar(spentCents: Long, allowanceCents: Long, overspent: Boolean) {
        val used = if (allowanceCents > 0) (spentCents.toDouble() / allowanceCents).coerceIn(0.0, 1.0).toFloat() else 0f
        LinearProgressIndicator(
            progress = used,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = if (overspent) WidgetColors.alert else WidgetColors.primary,
            backgroundColor = WidgetColors.track
        )
    }

    @Composable
    private fun Divider() {
        Spacer(GlanceModifier.height(8.dp))
        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(WidgetColors.track)) {}
        Spacer(GlanceModifier.height(6.dp))
    }

    // One line, three possible jobs, most urgent first.
    @Composable
    private fun Footnote(snapshot: WidgetSnapshot) {
        val carryDelta = snapshot.todayAllowanceCents - snapshot.baseDailyCents
        val text: String
        val color: ColorProvider
        when {
            // With rollover on, today's figure moves day to day. Show the *per-day* effect rather
            // than the carried pool: a 327 surplus spread over 26 days is +12.60 today, and naming
            // the pool next to a daily figure reads as if all of it were spendable now.
            carryDelta >= 0.5 -> {
                text = "+${snapshot.money(carryDelta, compact = true)}/day carried"
                color = WidgetColors.onSurfaceMuted
            }
            carryDelta <= -0.5 -> {
                text = "${snapshot.money(carryDelta, compact = true)}/day carried"
                color = WidgetColors.alert
            }
            // Spending outside every budget no longer moves the number above, so say so rather
            // than leave the widget looking suspiciously still.
            snapshot.unbudgetedTodayCents > 0 -> {
                text = "+${snapshot.money(snapshot.unbudgetedTodayCents, compact = true)} unbudgeted"
                color = WidgetColors.onSurfaceMuted
            }
            else -> {
                text = "of ${snapshot.money(snapshot.todayAllowanceCents, compact = true)}"
                color = WidgetColors.onSurfaceMuted
            }
        }
        Text(text, maxLines = 1, style = TextStyle(color = color, fontSize = 11.sp, textAlign = TextAlign.Center))
    }

    companion object {
        // Per-instance state, written by AllowanceWidgetConfigActivity.
        val KEY_TRACKS: Preferences.Key<String> = stringPreferencesKey("tracks")
        const val TRACK_ALL = ""

        // The extra category line only fits once the launcher gives us more than the 2x2 minimum.
        private val COMPACT = DpSize(110.dp, 110.dp)
        private val FULL = DpSize(140.dp, 140.dp)
    }
}

// Honours the "hide amounts" widget preference — a home screen is a public surface.
internal fun WidgetSnapshot.money(amountCents: Long, compact: Boolean = false): String = when {
    hideAmounts -> "•••"
    compact     -> formatCentsCompact(amountCents, currency, numberFormat)
    else        -> formatCents(amountCents, currency, numberFormat)
}

class DailyAllowanceWidgetReceiver : LedgerWidgetReceiver() {
    override val glanceAppWidget = DailyAllowanceWidget()
}
