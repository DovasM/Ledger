package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerTextField
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryScreen(navController: NavController, categoryName: String) {
    var name by remember { mutableStateOf(categoryName) }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val icons = listOf(
        Icons.Filled.Home, Icons.Filled.Restaurant, Icons.Filled.DirectionsCar,
        Icons.Filled.Movie, Icons.Filled.HealthAndSafety, Icons.Filled.ShoppingBag,
        Icons.Filled.Work, Icons.Filled.School, Icons.Filled.Flight,
        Icons.Filled.Pets, Icons.Filled.SportsEsports, Icons.Filled.LocalCafe,
        Icons.Filled.FitnessCenter, Icons.Filled.Brush, Icons.Filled.MusicNote,
        Icons.Filled.Payments, Icons.Filled.LocalGroceryStore, Icons.Filled.LocalHospital,
    )

    val colors = listOf(
        Color(0xFF00513F), Color(0xFF920009), Color(0xFF1565C0),
        Color(0xFFE65100), Color(0xFF6A1B9A), Color(0xFF00838F),
        Color(0xFF558B2F), Color(0xFFF9A825), Color(0xFF4E342E),
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Category") },
            text = { Text("Transactions in this category will be marked as 'Uncategorized'.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { navController.popBackStack() }) { Text("Delete", color = Tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Category", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Tertiary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LedgerTextField(value = name, onValueChange = { name = it }, label = "Category Name", modifier = Modifier.fillMaxWidth())

            Text("Icon", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(icons.indices.toList()) { i ->
                    val selected = i == selectedIconIndex
                    Box(
                        modifier = Modifier
                            .size(48.dp).clip(CircleShape)
                            .background(if (selected) colors[selectedColorIndex] else SurfaceContainerHighest)
                            .clickable { selectedIconIndex = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icons[i], contentDescription = null,
                            tint = if (selected) Color.White else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }

            Text("Color", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp).clip(CircleShape).background(color)
                            .border(if (i == selectedColorIndex) 3.dp else 0.dp, OnSurface, CircleShape)
                            .clickable { selectedColorIndex = i }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Changes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
