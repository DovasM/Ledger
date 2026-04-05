package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.theme.*

private data class NotifItem(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController) {
    val notifications = remember {
        mutableStateListOf(
            NotifItem(Icons.Filled.TrendingUp, Color(0xFF00513F), "Portfolio up 3.6%", "Your portfolio gained +\$124.50 today. AAPL is leading at +2.1%.", "2m ago", true),
            NotifItem(Icons.Filled.Savings, Color(0xFF1565C0), "Goal milestone reached", "Emergency Fund hit 65%! Keep up the \$500/mo deposits.", "1h ago", true),
            NotifItem(Icons.Filled.Warning, Color(0xFFE65100), "Budget alert — Food", "You've used 82% of your Food & Dining budget for April.", "3h ago", true),
            NotifItem(Icons.Filled.AccountBalance, Color(0xFF00513F), "Transaction posted", "Salary +\$5,200.00 posted to Checking Account.", "Apr 1", false),
            NotifItem(Icons.Filled.CreditCard, Color(0xFF920009), "Large expense detected", "Rent payment of \$1,200.00 was recorded in Housing.", "Apr 1", false),
            NotifItem(Icons.Filled.EmojiEvents, Color(0xFF6A1B9A), "Monthly goal hit!", "You saved 69% of your income in March — your best month yet.", "Mar 31", false),
            NotifItem(Icons.Filled.Update, Color(0xFF00838F), "Weekly summary ready", "Your week of Mar 24–30: +\$800 income, -\$320 expenses.", "Mar 30", false),
            NotifItem(Icons.Filled.Security, Color(0xFF00513F), "New login detected", "Ledger was opened on a new device. Tap to review.", "Mar 28", false),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { notifications.replaceAll { it.copy(unread = false) } }) {
                        Text("Mark all read", color = Primary, style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(56.dp))
                    Text("All caught up!", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text("No new notifications", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val unread = notifications.filter { it.unread }
                val read = notifications.filter { !it.unread }

                if (unread.isNotEmpty()) {
                    Text("New", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    unread.forEach { notif ->
                        NotifCard(notif) { notifications[notifications.indexOf(notif)] = notif.copy(unread = false) }
                    }
                }
                if (read.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Earlier", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    read.forEach { notif ->
                        NotifCard(notif, onClick = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifCard(notif: NotifItem, onClick: (() -> Unit)?) {
    LedgerCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(notif.iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(notif.icon, contentDescription = null, tint = notif.iconColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        notif.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                        fontWeight = if (notif.unread) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(notif.time, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                Text(notif.body, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            if (notif.unread) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(Primary).align(Alignment.Top)
                )
            }
        }
    }
}
