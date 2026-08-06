package com.ledger.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.util.DayState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

// Habit reinforcement, borrowed from language apps rather than finance ones — no competitor puts a
// streak on the home screen. The week grid makes today's remaining chance visible, which is the
// part that actually drives the behaviour.
class StreakWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetSnapshotRepository()
            .snapshot
            .first()

        provideContent { Content(snapshot) }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot) {
        val context = LocalContext.current
        val streak = snapshot.streakCurrent
        val hot = streak >= 7

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.surface)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(
                    actionStartActivity(widgetRouteIntent(context, Screen.SpendingStreaks.route))
                ),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                when {
                    !snapshot.hasData -> "📊"
                    hot               -> "🔥"
                    streak > 0        -> "✅"
                    else              -> "📊"
                },
                style = TextStyle(fontSize = 22.sp)
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "$streak",
                style = TextStyle(
                    color = if (hot) WidgetColors.flame else WidgetColors.primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                if (snapshot.hasAllowance) "day streak" else "days logged",
                style = TextStyle(
                    color = WidgetColors.onSurfaceMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(GlanceModifier.height(10.dp))
            WeekGrid(snapshot.weekGrid, hot)
        }
    }

    @Composable
    private fun WeekGrid(week: List<DayState>, hot: Boolean) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            week.forEachIndexed { index, state ->
                if (index > 0) Spacer(GlanceModifier.width(5.dp))
                Box(
                    modifier = GlanceModifier
                        .size(12.dp)
                        .cornerRadius(6.dp)
                        .background(state.color(hot)),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }

    private fun DayState.color(hot: Boolean): ColorProvider = when (this) {
        DayState.Good   -> if (hot) WidgetColors.flame else WidgetColors.primary
        DayState.Over   -> WidgetColors.alert
        DayState.Empty  -> WidgetColors.track
        DayState.Future -> WidgetColors.surfaceRaised
    }
}

class StreakWidgetReceiver : LedgerWidgetReceiver() {
    override val glanceAppWidget = StreakWidget()
}
