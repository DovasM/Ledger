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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(navController: NavController) {
    var name by remember { mutableStateOf("Alex Johnson") }
    var email by remember { mutableStateOf("alex@example.com") }
    var phone by remember { mutableStateOf("+1 555 123 4567") }
    var currency by remember { mutableStateOf("USD") }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    val currencies = listOf("USD", "EUR", "GBP", "LTL", "PLN", "SEK", "NOK", "CHF")

    var saved by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saved) {
        if (saved) {
            snackbarHostState.showSnackbar("Profile updated")
            saved = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Avatar
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(88.dp).clip(CircleShape).background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = OnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = {}) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change Photo")
                }
            }

            // Personal info
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Personal Information", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)

                    LedgerTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Full Name",
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    LedgerTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address",
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    LedgerTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone Number",
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Preferences
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("App Preferences", style = MaterialTheme.typography.titleSmall, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)

                    // Currency picker
                    Box {
                        Surface(
                            onClick = { showCurrencyMenu = true },
                            shape = MaterialTheme.shapes.medium,
                            color = SurfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Currency", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                    Text(currency, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                                }
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = OnSurfaceVariant)
                            }
                        }
                        DropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) {
                            currencies.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = { currency = c; showCurrencyMenu = false },
                                    trailingIcon = if (c == currency) ({
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                                    }) else null
                                )
                            }
                        }
                    }
                }
            }

            // Save button
            Button(
                onClick = { saved = true; navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Changes", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
