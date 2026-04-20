package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.SettingsViewModel

private val accentColors = listOf(
    "Forest Green" to Color(0xFF00513F),
    "Ocean Blue" to Color(0xFF1565C0),
    "Deep Purple" to Color(0xFF6A1B9A),
    "Amber" to Color(0xFFE65100),
    "Teal" to Color(0xFF00838F),
    "Rose" to Color(0xFFAD1457),
    "Slate" to Color(0xFF455A64),
    "Indigo" to Color(0xFF283593),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val selectedAccent      by vm.accentIndex.collectAsStateWithLifecycle()
    val selectedDensity     by vm.densityIndex.collectAsStateWithLifecycle()
    val selectedHomeTab     by vm.homeTabIndex.collectAsStateWithLifecycle()
    val selectedNumberFormat by vm.numberFormatIndex.collectAsStateWithLifecycle()
    val darkMode            by vm.darkMode.collectAsStateWithLifecycle()

    val homeTabs = listOf("Dashboard", "Activity", "Budget", "Invest", "Stats")
    val numberFormats = listOf("1,000.00", "1.000,00", "1 000,00")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Accent Color
            AppSection(title = "Accent Color") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected: ${accentColors[selectedAccent].first}", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(accentColors[selectedAccent].second))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        accentColors.forEachIndexed { i, (_, color) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (i == selectedAccent) Modifier.border(3.dp, Color.White, CircleShape).border(5.dp, color, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(onClick = { vm.setAccentIndex(i) }, color = Color.Transparent, shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
                                if (i == selectedAccent) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text("Requires app restart to apply.", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }

            // Theme
            AppSection(title = "Theme") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.DarkMode, null, tint = if (darkMode) Primary else OnSurfaceVariant, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Dark Mode", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                            Text("Switch between light and dark theme", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                    Switch(checked = darkMode, onCheckedChange = { vm.setDarkMode(it) }, colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary))
                }
            }

            // Density
            AppSection(title = "Display Density") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Controls spacing and element sizes throughout the app.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Compact", "Comfortable", "Spacious").forEachIndexed { i, label ->
                            FilterChip(
                                selected = selectedDensity == i, onClick = { vm.setDensityIndex(i) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = OnPrimary)
                            )
                        }
                    }
                }
            }

            // Default home tab
            AppSection(title = "Default Home Tab") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Which tab opens when you launch the app.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    homeTabs.forEachIndexed { i, tab ->
                        Surface(onClick = { vm.setHomeTabIndex(i) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tab, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                if (selectedHomeTab == i) Icon(Icons.Filled.RadioButtonChecked, null, tint = Primary, modifier = Modifier.size(18.dp))
                                else Icon(Icons.Filled.RadioButtonUnchecked, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (i < homeTabs.lastIndex) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }

            // Number format
            AppSection(title = "Number Format") {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    numberFormats.forEachIndexed { i, fmt ->
                        Surface(onClick = { vm.setNumberFormatIndex(i) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(fmt, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                                    Text("e.g. ${fmt.replace("1", "1234").replace("0", "78")}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                }
                                if (selectedNumberFormat == i) Icon(Icons.Filled.RadioButtonChecked, null, tint = Primary, modifier = Modifier.size(18.dp))
                                else Icon(Icons.Filled.RadioButtonUnchecked, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (i < numberFormats.lastIndex) HorizontalDivider(color = OutlineVariant.copy(alpha = 0.15f))
                    }
                }
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Preferences", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AppSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        LedgerCard(modifier = Modifier.fillMaxWidth()) { content() }
    }
}
