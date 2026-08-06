package com.ledger.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import com.ledger.app.MainActivity

// Glance cannot reuse the Material3 theme or the app's ImageVector icons, so the palette is
// re-declared here as day/night ColorProviders. Values mirror ui/theme/Color.kt.
object WidgetColors {
    val surface       = ColorProvider(day = Color(0xFFF8FAF5), night = Color(0xFF1B1F1D))
    val surfaceRaised = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF262B28))
    val onSurface     = ColorProvider(day = Color(0xFF191C1A), night = Color(0xFFEFF1EC))
    val onSurfaceMuted = ColorProvider(day = Color(0xFF3E4944), night = Color(0xFFBEC9C3))
    val primary       = ColorProvider(day = Color(0xFF00513F), night = Color(0xFF82D7BA))
    val onPrimary     = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF00311F))
    val alert         = ColorProvider(day = Color(0xFF920009), night = Color(0xFFFFB4A9))
    val flame         = ColorProvider(day = Color(0xFFE65100), night = Color(0xFFFFB77C))
    val track         = ColorProvider(day = Color(0xFFE1E3DE), night = Color(0xFF3A403C))
}

// Widgets open the app through a URI rather than intent extras: PendingIntent.filterEquals ignores
// extras, so two buttons carrying only different extras would collapse into one PendingIntent and
// every button would open the same screen. The route lives in the URI query, which is compared.
const val WIDGET_URI_SCHEME = "ledger"
const val WIDGET_URI_HOST = "open"
const val WIDGET_ROUTE_PARAM = "route"

fun widgetRouteIntent(context: Context, route: String): Intent =
    Intent(
        Intent.ACTION_VIEW,
        Uri.parse("$WIDGET_URI_SCHEME://$WIDGET_URI_HOST?$WIDGET_ROUTE_PARAM=${Uri.encode(route)}"),
        context,
        MainActivity::class.java
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
