package com.ledger.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Shared base for the widget receivers. A freshly placed widget (or the system's periodic update)
// arrives before the app has necessarily run, so each update recomputes the snapshot. goAsync keeps
// the broadcast alive across the suspend work.
abstract class LedgerWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // goAsync() hands out the pending result once and nulls it, and GlanceAppWidgetReceiver has
        // usually claimed it already by the time super.onUpdate returns. Calling finish() on that
        // null crashed the whole app process on every widget update — the type is platform-typed,
        // so Kotlin did not force the check.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                EntryPointAccessors
                    .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                    .widgetUpdater()
                    .refresh()
            } catch (_: Exception) {
            } finally {
                pending?.finish()
            }
        }
    }
}
