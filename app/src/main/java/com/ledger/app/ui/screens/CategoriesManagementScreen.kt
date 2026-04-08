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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.colorHexToColor
import com.ledger.app.ui.util.iconNameToVector
import com.ledger.app.ui.viewmodel.CategoryViewModel
import uniffi.ledger.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesManagementScreen(
    navController: NavController,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry?.destination?.route) { viewModel.load() }

    val expenseCategories = state.categories.filter { it.isExpense }
    val incomeCategories = state.categories.filter { !it.isExpense }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.AddCategory.route) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add category")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddCategory.route) },
                containerColor = Primary, contentColor = OnPrimary) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
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
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                if (expenseCategories.isNotEmpty()) {
                    Text("Expense Categories", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            expenseCategories.forEachIndexed { idx, cat ->
                                CategoryRow(cat, navController, onDelete = { viewModel.deleteCategory(cat.id) })
                                if (idx < expenseCategories.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = OutlineVariant.copy(alpha = 0.15f))
                                }
                            }
                        }
                    }
                }

                if (incomeCategories.isNotEmpty()) {
                    Text("Income Categories", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            incomeCategories.forEachIndexed { idx, cat ->
                                CategoryRow(cat, navController, onDelete = { viewModel.deleteCategory(cat.id) })
                                if (idx < incomeCategories.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = OutlineVariant.copy(alpha = 0.15f))
                                }
                            }
                        }
                    }
                }

                if (state.categories.isEmpty()) {
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No categories yet. Add one to get started.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { navController.navigate(Screen.AddCategory.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = Primary)
                Spacer(Modifier.width(8.dp))
                Text("Add Category", color = Primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CategoryRow(cat: Category, navController: NavController, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Category") },
            text = { Text("Delete \"${cat.name}\"? Existing transactions will keep their category label.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Primary) }
            }
        )
    }

    val color = colorHexToColor(cat.colorHex)
    val icon = iconNameToVector(cat.iconName)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(cat.name, style = MaterialTheme.typography.bodyLarge, color = OnSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = { navController.navigate(Screen.EditCategory.createRoute(cat.id)) }) {
            Icon(Icons.Filled.Edit, "Edit", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(Icons.Filled.Delete, "Delete", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
