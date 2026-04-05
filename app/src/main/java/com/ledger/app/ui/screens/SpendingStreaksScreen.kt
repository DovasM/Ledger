package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class Achievement(val icon: String, val title: String, val desc: String, val unlocked: Boolean, val color: Color)
private data class WeeklyChallenge(val title: String, val desc: String, val progress: Float, val icon: ImageVector, val color: Color)

private val achievements = listOf(
    Achievement("🔥", "7-Day Streak", "Stay under budget 7 days in a row", true, Color(0xFFE65100)),
    Achievement("💰", "Saver", "Save 20% or more in a month", true, Primary),
    Achievement("🎯", "Budget Master", "Hit all budgets in a month", false, Color(0xFF1565C0)),
    Achievement("🏆", "30-Day Streak", "Stay under budget 30 days in a row", false, Color(0xFFFF9800)),
    Achievement("📊", "Analyst", "View statistics 10 times", true, Color(0xFF6A1B9A)),
    Achievement("🌱", "First Goal", "Create your first savings goal", true, Primary),
    Achievement("💳", "Debt Fighter", "Make 3 extra debt payments", false, Tertiary),
    Achievement("📅", "Consistent", "Log transactions every day for 2 weeks", false, Color(0xFF00838F)),
)

private val challenges = listOf(
    WeeklyChallenge("No Eating Out", "Avoid restaurant expenses this week", 0.71f, Icons.Filled.Restaurant, Tertiary),
    WeeklyChallenge("Save \$100", "Put aside \$100 in savings this week", 0.45f, Icons.Filled.Savings, Primary),
    WeeklyChallenge("Under Budget", "Stay under budget in all categories", 0.88f, Icons.Filled.CheckCircle, Color(0xFF1565C0)),
)

private val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")
private val streakDays = listOf(true, true, true, true, true, true, false) // this week

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingStreaksScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Streaks", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Current streak hero
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔥", fontSize = 48.sp)
                    Text("6", style = MaterialTheme.typography.displayMedium, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                    Text("day streak under budget", style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    // Week grid
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        weekDays.forEachIndexed { i, day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                        .background(if (streakDays[i]) Color(0xFFE65100) else SurfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (streakDays[i]) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    else Text(day, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                }
                                Text(day, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏆", fontSize = 20.sp)
                        Text("14", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("Best streak", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📅", fontSize = 20.sp)
                        Text("18", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("Days logged", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                LedgerCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💎", fontSize = 20.sp)
                        Text("4", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
                        Text("Achievements", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }

            // Weekly challenges
            Text("Weekly Challenges", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            challenges.forEach { c ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(c.icon, null, tint = c.color, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                Text(c.desc, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Text("${(c.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.color, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { c.progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = c.color, trackColor = c.color.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            // Achievements
            Text("Achievements", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                achievements.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { achievement ->
                            LedgerCard(modifier = Modifier.weight(1f)) {
                                Column(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                            .background(if (achievement.unlocked) achievement.color.copy(alpha = 0.15f) else SurfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (achievement.unlocked) Text(achievement.icon, fontSize = 22.sp)
                                        else Icon(Icons.Filled.Lock, null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                                    }
                                    Text(achievement.title, style = MaterialTheme.typography.labelMedium, color = if (achievement.unlocked) OnSurface else OnSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                    Text(achievement.desc, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
