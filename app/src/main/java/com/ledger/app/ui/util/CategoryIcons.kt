package com.ledger.app.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

val categoryIconNames = listOf(
    "home", "restaurant", "directions_car", "movie", "health_and_safety", "shopping_bag",
    "work", "school", "flight", "pets", "sports_esports", "local_cafe",
    "fitness_center", "brush", "music_note", "payments", "local_grocery_store", "local_hospital"
)

val categoryIcons: List<ImageVector> = listOf(
    Icons.Filled.Home, Icons.Filled.Restaurant, Icons.Filled.DirectionsCar,
    Icons.Filled.Movie, Icons.Filled.HealthAndSafety, Icons.Filled.ShoppingBag,
    Icons.Filled.Work, Icons.Filled.School, Icons.Filled.Flight,
    Icons.Filled.Pets, Icons.Filled.SportsEsports, Icons.Filled.LocalCafe,
    Icons.Filled.FitnessCenter, Icons.Filled.Brush, Icons.Filled.MusicNote,
    Icons.Filled.Payments, Icons.Filled.LocalGroceryStore, Icons.Filled.LocalHospital
)

val categoryColors = listOf(
    Color(0xFF00513F), Color(0xFF920009), Color(0xFF1565C0),
    Color(0xFFE65100), Color(0xFF6A1B9A), Color(0xFF00838F),
    Color(0xFF558B2F), Color(0xFFF9A825), Color(0xFF4E342E)
)

val categoryColorHexes = listOf(
    "#00513F", "#920009", "#1565C0",
    "#E65100", "#6A1B9A", "#00838F",
    "#558B2F", "#F9A825", "#4E342E"
)

fun iconNameToVector(name: String): ImageVector {
    val idx = categoryIconNames.indexOf(name)
    return if (idx >= 0) categoryIcons[idx] else Icons.Filled.Label
}

fun colorHexToColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (e: Exception) {
        Color(0xFF00513F)
    }
}
