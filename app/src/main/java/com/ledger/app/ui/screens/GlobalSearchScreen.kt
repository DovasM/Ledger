package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class SearchTx(
    val title: String, val category: String, val amount: String, val isIncome: Boolean,
    val date: String, val wallet: String, val icon: ImageVector, val color: Color, val note: String = ""
)

private val allSearchableTxs = listOf(
    SearchTx("Salary",           "Work",          "+\$5,200.00", true,  "Apr 1, 2026",  "Checking", Icons.Filled.Work,             Color(0xFF00513F), "April paycheck"),
    SearchTx("Rent",             "Housing",       "-\$1,200.00", false, "Apr 1, 2026",  "Checking", Icons.Filled.Home,             Color(0xFF00513F), "Monthly rent"),
    SearchTx("Freelance",        "Work",          "+\$800.00",   true,  "Apr 3, 2026",  "Checking", Icons.Filled.Laptop,           Color(0xFF1565C0), "Web project"),
    SearchTx("Groceries",        "Food & Dining", "-\$124.50",   false, "Apr 3, 2026",  "Checking", Icons.Filled.Restaurant,       Color(0xFF1565C0)),
    SearchTx("Netflix",          "Entertainment", "-\$15.99",    false, "Apr 1, 2026",  "Checking", Icons.Filled.Movie,            Color(0xFFE65100)),
    SearchTx("Spotify",          "Entertainment", "-\$9.99",     false, "Apr 4, 2026",  "Checking", Icons.Filled.MusicNote,        Color(0xFFE65100)),
    SearchTx("Bus pass",         "Transport",     "-\$45.00",    false, "Apr 3, 2026",  "Cash",     Icons.Filled.DirectionsBus,    Color(0xFF6A1B9A), "Monthly pass"),
    SearchTx("Coffee",           "Food & Dining", "-\$4.50",     false, "Mar 31, 2026", "Cash",     Icons.Filled.Restaurant,       Color(0xFF1565C0)),
    SearchTx("Pharmacy",         "Health",        "-\$22.00",    false, "Mar 31, 2026", "Savings",  Icons.Filled.HealthAndSafety,  Color(0xFF920009)),
    SearchTx("Restaurant",       "Food & Dining", "-\$62.00",    false, "Mar 28, 2026", "Checking", Icons.Filled.Restaurant,       Color(0xFF1565C0), "Dinner"),
    SearchTx("Salary",           "Work",          "+\$5,200.00", true,  "Mar 1, 2026",  "Checking", Icons.Filled.Work,             Color(0xFF00513F), "March paycheck"),
    SearchTx("Rent",             "Housing",       "-\$1,200.00", false, "Mar 1, 2026",  "Checking", Icons.Filled.Home,             Color(0xFF00513F), "Monthly rent"),
    SearchTx("Electricity",      "Housing",       "-\$85.00",    false, "Mar 18, 2026", "Checking", Icons.Filled.ElectricBolt,     Color(0xFF00513F)),
    SearchTx("Gym membership",   "Health",        "-\$40.00",    false, "Mar 5, 2026",  "Checking", Icons.Filled.FitnessCenter,    Color(0xFF920009)),
    SearchTx("Amazon",           "Shopping",      "-\$78.40",    false, "Mar 22, 2026", "Checking", Icons.Filled.ShoppingCart,     Color(0xFF00838F), "Books"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    var sheetTx by remember { mutableStateOf<SearchTx?>(null) }
    val focusRequester = remember { FocusRequester() }

    val filtered = if (query.length < 2) emptyList()
    else allSearchableTxs.filter {
        it.title.contains(query, ignoreCase = true) ||
        it.category.contains(query, ignoreCase = true) ||
        it.wallet.contains(query, ignoreCase = true) ||
        it.note.contains(query, ignoreCase = true) ||
        it.amount.contains(query, ignoreCase = true)
    }

    val suggestions = listOf("Rent", "Food", "Salary", "Transport", "Netflix")

    if (sheetTx != null) {
        val tx = sheetTx!!
        ModalBottomSheet(onDismissRequest = { sheetTx = null }, containerColor = SurfaceContainerLowest, tonalElevation = 0.dp) {
            SearchTxSheet(tx) { sheetTx = null }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search transactions…", color = OnSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                        trailingIcon = if (query.isNotEmpty()) ({
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant) }
                        }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = OutlineVariant,
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (query.length < 2) {
                item {
                    Text("Recent searches", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.take(3).forEach { s ->
                            AssistChip(
                                onClick = { query = s },
                                label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Filled.History, null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.drop(3).forEach { s ->
                            AssistChip(
                                onClick = { query = s },
                                label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Filled.History, null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("All categories", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(listOf("Housing","Food & Dining","Transport","Entertainment","Health","Work")) { cat ->
                    Surface(onClick = { query = cat }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                            Text(cat, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.SearchOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
                        Text("No results for \"$query\"", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        Text("Try a different keyword", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                }
            } else {
                item {
                    Text("${filtered.size} result${if (filtered.size != 1) "s" else ""}", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                }
                item {
                    LedgerCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            filtered.forEachIndexed { idx, tx ->
                                Surface(onClick = { sheetTx = tx }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(tx.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                            Icon(tx.icon, null, tint = tx.color, modifier = Modifier.size(18.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tx.title, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.Medium)
                                            Text("${tx.category} · ${tx.date}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                        }
                                        Text(tx.amount, style = MaterialTheme.typography.titleSmall,
                                            color = if (tx.isIncome) Primary else Tertiary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (idx < filtered.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = OutlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTxSheet(tx: SearchTx, onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tx.title, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
                Text(tx.amount, style = MaterialTheme.typography.titleLarge, color = if (tx.isIncome) Primary else Tertiary, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(tx.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(tx.icon, null, tint = tx.color, modifier = Modifier.size(26.dp))
            }
        }
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchDetailRow(Icons.Filled.CalendarToday, "Date", tx.date)
            SearchDetailRow(Icons.Filled.Category, "Category", tx.category)
            SearchDetailRow(Icons.Filled.AccountBalanceWallet, "Wallet", tx.wallet)
            if (tx.note.isNotBlank()) SearchDetailRow(Icons.Filled.Notes, "Note", tx.note)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", color = Primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SearchDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
