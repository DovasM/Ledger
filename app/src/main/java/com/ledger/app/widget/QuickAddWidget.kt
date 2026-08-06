package com.ledger.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ledger.app.R
import com.ledger.app.ui.navigation.Screen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

// The friction of opening the app is why manual expense tracking gets abandoned, so this widget is
// pure action: add a transaction, scan a receipt, or jump straight into a category you use often.
class QuickAddWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(NARROW, WIDE)
    )

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
        val wide = LocalSize.current.width >= WIDE.width

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.surface)
                .cornerRadius(20.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(
                modifier = (if (wide) GlanceModifier.defaultWeight() else GlanceModifier.fillMaxWidth())
                    .height(48.dp)
                    .background(WidgetColors.primary)
                    .cornerRadius(14.dp)
                    .clickable(
                        actionStartActivity(
                            widgetRouteIntent(context, Screen.AddTransaction.createRoute())
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (wide) {
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(WidgetColors.onPrimary),
                            modifier = GlanceModifier.size(20.dp)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            "Add",
                            style = TextStyle(
                                color = WidgetColors.onPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = "Add transaction",
                        colorFilter = ColorFilter.tint(WidgetColors.onPrimary),
                        modifier = GlanceModifier.size(22.dp)
                    )
                }
            }

            // Mirrors the in-app rule: with AI switched off every receipt-scan entry point vanishes.
            if (snapshot.aiEnabled) {
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(WidgetColors.surfaceRaised)
                        .cornerRadius(14.dp)
                        .clickable(
                            actionStartActivity(
                                widgetRouteIntent(context, Screen.ReceiptScan.route)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_scan),
                        contentDescription = "Scan receipt",
                        colorFilter = ColorFilter.tint(WidgetColors.primary),
                        modifier = GlanceModifier.size(22.dp)
                    )
                }
            }

            if (wide) {
                snapshot.topCategories.take(2).forEach { shortcut ->
                    Spacer(GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(48.dp)
                            .background(WidgetColors.surfaceRaised)
                            .cornerRadius(14.dp)
                            .clickable(
                                actionStartActivity(
                                    widgetRouteIntent(
                                        context,
                                        Screen.AddTransaction.createRoute(shortcut.name)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            shortcut.name,
                            maxLines = 1,
                            style = TextStyle(
                                color = WidgetColors.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            ),
                            modifier = GlanceModifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val NARROW = DpSize(110.dp, 50.dp)
        private val WIDE = DpSize(250.dp, 50.dp)
    }
}

class QuickAddWidgetReceiver : LedgerWidgetReceiver() {
    override val glanceAppWidget = QuickAddWidget()
}
