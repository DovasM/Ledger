package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.navigation.Screen
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesManagementScreen(navController: NavController) {
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
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddCategory.route) },
                containerColor = Primary, contentColor = OnPrimary
            ) {
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
            Text("Expense Categories", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CategoryRow("Housing", Icons.Filled.Home, navController)
                    CategoryRow("Food & Dining", Icons.Filled.Restaurant, navController)
                    CategoryRow("Transportation", Icons.Filled.DirectionsCar, navController)
                    CategoryRow("Entertainment", Icons.Filled.Movie, navController)
                    CategoryRow("Health", Icons.Filled.HealthAndSafety, navController)
                    CategoryRow("Shopping", Icons.Filled.ShoppingBag, navController)
                }
            }

            Text("Income Categories", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CategoryRow("Salary", Icons.Filled.Work, navController)
                    CategoryRow("Freelance", Icons.Filled.Laptop, navController)
                    CategoryRow("Investments", Icons.Filled.TrendingUp, navController)
                    CategoryRow("Other Income", Icons.Filled.Payments, navController)
                }
            }

            OutlinedButton(
                onClick = { navController.navigate(Screen.AddCategory.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Primary)
                Spacer(Modifier.width(8.dp))
                Text("Add Category", color = Primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = OnSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = { navController.navigate(Screen.EditCategory.createRoute(name)) }) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
