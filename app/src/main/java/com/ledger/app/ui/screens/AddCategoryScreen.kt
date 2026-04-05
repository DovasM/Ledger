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
fun AddCategoryScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedIconIndex by remember { mutableStateOf(0) }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var showErrors by remember { mutableStateOf(false) }
    val isNameValid = name.isNotBlank()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Category", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
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
            // Type toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = isExpense, onClick = { isExpense = true },
                    label = { Text("Expense") }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary)
                )
                FilterChip(
                    selected = !isExpense, onClick = { isExpense = false },
                    label = { Text("Income") }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                )
            }

            LedgerTextField(
                value = name, onValueChange = { name = it },
                label = "Category Name", placeholder = "e.g. Housing, Salary",
                modifier = Modifier.fillMaxWidth(),
                isError = showErrors && !isNameValid,
                supportingText = if (showErrors && !isNameValid) "Category name is required" else null
            )

            // Icon picker
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
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors[selectedColorIndex] else SurfaceContainerHighest)
                            .border(if (selected) 0.dp else 1.dp, OutlineVariant.copy(alpha = 0.15f), CircleShape)
                            .clickable { selectedIconIndex = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icons[i], contentDescription = null,
                            tint = if (selected) Color.White else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Color picker
            Text("Color", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (i == selectedColorIndex) 3.dp else 0.dp,
                                color = OnSurface,
                                shape = CircleShape
                            )
                            .clickable { selectedColorIndex = i }
                    )
                }
            }

            // Preview
            Text("Preview", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(colors[selectedColorIndex]),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icons[selectedIconIndex], contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(
                    if (name.isBlank()) "Category Name" else name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (name.isBlank()) OnSurfaceVariant else OnSurface
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showErrors = true; if (isNameValid) navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create Category", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
