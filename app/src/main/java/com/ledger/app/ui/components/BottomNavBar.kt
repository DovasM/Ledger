package com.ledger.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.SurfaceContainerLowest

data class NavItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val bottomNavItems = listOf(
    NavItem("Home",     Screen.Dashboard.route,          Icons.Filled.Home),
    NavItem("Activity", Screen.Activity.route,           Icons.Filled.Timeline),
    NavItem("Budget",   Screen.Budgets.route,            Icons.Filled.PieChart),
    NavItem("Invest",   Screen.InvestmentPortfolio.route, Icons.Filled.TrendingUp),
    NavItem("Stats",    Screen.MonthlyStatistics.route,  Icons.Filled.BarChart),
)

@Composable
fun LedgerBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = SurfaceContainerLowest) {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute == item.route) return@NavigationBarItem
                    navController.navigate(item.route) {
                        popUpTo(Screen.Dashboard.route) {
                            saveState = item.route != Screen.Dashboard.route
                            inclusive = item.route == Screen.Dashboard.route
                        }
                        launchSingleTop = true
                        restoreState = item.route != Screen.Dashboard.route
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
