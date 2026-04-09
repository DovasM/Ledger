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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerTextField
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.categoryColorHexes
import com.ledger.app.ui.util.categoryColors
import com.ledger.app.ui.util.categoryIconNames
import com.ledger.app.ui.util.categoryIcons
import com.ledger.app.ui.viewmodel.CategoryViewModel
import com.ledger.app.ui.viewmodel.TransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryScreen(
    navController: NavController,
    categoryId: String,
    viewModel: CategoryViewModel = hiltViewModel(),
    txViewModel: TransactionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val txState by txViewModel.state.collectAsStateWithLifecycle()
    val category = state.categories.find { it.id == categoryId }

    var name by remember(category) { mutableStateOf(category?.name ?: "") }
    var isExpense by remember(category) { mutableStateOf(category?.isExpense ?: true) }
    var selectedIconIndex by remember(category) {
        mutableStateOf(categoryIconNames.indexOf(category?.iconName ?: "").takeIf { it >= 0 } ?: 0)
    }
    var selectedColorIndex by remember(category) {
        mutableStateOf(categoryColorHexes.indexOfFirst { it.equals(category?.colorHex, ignoreCase = true) }.takeIf { it >= 0 } ?: 0)
    }
    var showErrors by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    val isNameValid = name.isNotBlank()

    val allSuggestions = remember(txState.transactions) {
        (txState.transactions.map { it.title } + txState.transactions.map { it.category })
            .filter { it.isNotBlank() }.distinct().sorted()
    }
    val filteredSuggestions = remember(name, allSuggestions) {
        if (name.isBlank()) allSuggestions.take(8)
        else allSuggestions.filter { it.contains(name, ignoreCase = true) && !it.equals(name, ignoreCase = true) }.take(8)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Category") },
            text = { Text("Delete \"${category?.name}\"? Existing transactions will keep their category label.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(categoryId) { navController.popBackStack() }
                }) { Text("Delete", color = Tertiary) }
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
                        Icon(Icons.Filled.Delete, "Delete", tint = Tertiary)
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = isExpense, onClick = { isExpense = true },
                    label = { Text("Expense") }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tertiary, selectedLabelColor = OnTertiary))
                FilterChip(selected = !isExpense, onClick = { isExpense = false },
                    label = { Text("Income") }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary))
            }

            ExposedDropdownMenuBox(
                expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                onExpandedChange = {}
            ) {
                LedgerTextField(
                    value = name,
                    onValueChange = { name = it; showSuggestions = true },
                    label = "Category Name",
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    isError = showErrors && !isNameValid,
                    supportingText = if (showErrors && !isNameValid) "Category name is required" else null
                )
                ExposedDropdownMenu(
                    expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                    onDismissRequest = { showSuggestions = false }
                ) {
                    filteredSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { name = suggestion; showSuggestions = false }
                        )
                    }
                }
            }

            Text("Icon", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryIcons.indices.toList()) { i ->
                    val selected = i == selectedIconIndex
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(if (selected) categoryColors[selectedColorIndex] else SurfaceContainerHighest)
                            .border(if (selected) 0.dp else 1.dp, OutlineVariant.copy(alpha = 0.15f), CircleShape)
                            .clickable { selectedIconIndex = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(categoryIcons[i], null,
                            tint = if (selected) Color.White else OnSurfaceVariant,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }

            Text("Color", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                categoryColors.forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                            .border(if (i == selectedColorIndex) 3.dp else 0.dp, OnSurface, CircleShape)
                            .clickable { selectedColorIndex = i }
                    )
                }
            }

            Text("Preview", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(categoryColors[selectedColorIndex]),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(categoryIcons[selectedIconIndex], null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(
                    if (name.isBlank()) "Category Name" else name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (name.isBlank()) OnSurfaceVariant else OnSurface
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    showErrors = true
                    if (isNameValid) {
                        viewModel.updateCategory(
                            id = categoryId,
                            name = name,
                            iconName = categoryIconNames[selectedIconIndex],
                            colorHex = categoryColorHexes[selectedColorIndex],
                            isExpense = isExpense
                        ) { navController.popBackStack() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Changes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
