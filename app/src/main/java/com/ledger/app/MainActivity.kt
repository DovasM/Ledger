package com.ledger.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.ledger.app.ui.navigation.LedgerNavGraph
import com.ledger.app.ui.theme.LedgerTheme
import com.ledger.app.widget.WIDGET_ROUTE_PARAM
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Route requested by a home-screen widget tap. Held in a flow rather than read straight from
    // the intent so onNewIntent (singleTop — an already-running app) reaches the same handler.
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.widgetRoute()
        setContent {
            LedgerTheme {
                val navController = rememberNavController()
                LedgerNavGraph(navController)

                val route by pendingRoute.collectAsStateWithLifecycle()
                LaunchedEffect(route) {
                    val target = route ?: return@LaunchedEffect
                    pendingRoute.value = null
                    // An unknown route would throw and take the app down on launch.
                    runCatching { navController.navigate(target) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.widgetRoute()
    }

    private fun Intent.widgetRoute(): String? = data?.getQueryParameter(WIDGET_ROUTE_PARAM)
}
