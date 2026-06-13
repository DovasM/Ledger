package com.ledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.data.GemmaRepository
import com.ledger.app.data.ModelStatus
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.LedgerFloatingCard
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.GemmaModelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelScreen(
    navController: NavController,
    vm: GemmaModelViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var testInput by remember { mutableStateOf("") }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Ištrinti modelį?") },
            text = { Text("Modelio failas (~2.7 GB) bus ištrintas. Jį reikės atsisiųsti iš naujo.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; vm.deleteModel() }) {
                    Text("Ištrinti", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Atšaukti") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Modelis", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atgal")
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
            // Privacy notice
            LedgerFloatingCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = Primary,
                        modifier = Modifier.size(22.dp).padding(top = 2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Viskas vyksta jūsų telefone", style = MaterialTheme.typography.labelLarge,
                            color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Visi AI skaičiavimai atliekami lokaliai — jūsų duomenys niekur nesiunčiami ir lieka visiškai privatūs.",
                            style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant
                        )
                    }
                }
            }

            // ── Model file status ─────────────────────────────────────────────
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemma 4 E2B", style = MaterialTheme.typography.titleMedium,
                                color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text("llama.cpp · GGUF Q4_K_M · ~2.7 GB", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                        StatusChip(state.modelStatus)
                    }

                    when (val status = state.modelStatus) {
                        is ModelStatus.NotDownloaded -> {
                            state.storageInfo?.let { storage ->
                                if (!storage.hasEnoughSpace) {
                                    WarningBanner("Nepakanka vietos. Laisva: ${storage.availableBytes / 1_000_000} MB. Reikia bent 3 GB.")
                                }
                            }
                            Button(
                                onClick = { vm.startDownload() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.storageInfo?.hasEnoughSpace != false
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Atsisiųsti modelį (~2.7 GB)")
                            }
                        }

                        is ModelStatus.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { status.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${status.progressPercent}%", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    Text("${status.bytesDownloaded / 1_000_000} MB", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                }
                            }
                            OutlinedButton(onClick = { vm.cancelDownload() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Atšaukti")
                            }
                        }

                        is ModelStatus.Verifying -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Tikrinamas failo vientisumas...", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }

                        is ModelStatus.Ready -> {
                            state.integrityOk?.let { ok ->
                                if (ok) InfoBanner("Failo kontrolinė suma patikrinta ✓", color = Color(0xFF2E7D32))
                                else WarningBanner("Failo kontrolinė suma nesutampa — modelis gali būti sugadintas.")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { vm.verifyIntegrity() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !state.isVerifying
                                ) {
                                    if (state.isVerifying) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tikrinama...")
                                    } else {
                                        Text("Patikrinti")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Ištrinti")
                                }
                            }
                        }

                        is ModelStatus.UpdateAvailable -> {
                            InfoBanner("Yra nauja versija (dabartinė: ${status.currentVersion})")
                            Button(onClick = { vm.startDownload() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Atnaujinti modelį")
                            }
                        }

                        is ModelStatus.Error -> {
                            WarningBanner(status.message)
                            if (status.retryable) {
                                Button(onClick = { vm.startDownload() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Bandyti dar kartą")
                                }
                            }
                        }

                        is ModelStatus.Deleting -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Trinamas...", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Inference engine (only when file is ready) ────────────────────
            if (state.modelStatus is ModelStatus.Ready) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("AI Variklis", style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)

                        if (!state.isNativeLibraryAvailable) {
                            InfoBanner(
                                "libllama-android.so nerasta — AI variklis negalimas.\n" +
                                "Modelio failas atsisiųstas. Kai subuildinsi .so, " +
                                "variklis įsijungs automatiškai.",
                                color = Color(0xFFE65100)
                            )
                        } else when (state.inferenceState) {
                            GemmaRepository.InferenceState.NOT_LOADED -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(22.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Modelis neįkeltas į atmintį", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text("~2.7 GB RAM reikalinga", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                }
                                Button(onClick = { vm.loadModelForInference() }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Įkelti į atmintį")
                                }
                            }

                            GemmaRepository.InferenceState.LOADING -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                    Column {
                                        Text("Modelis kraunamas...", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text("Tai gali užtrukti ~5 sekundes", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                }
                            }

                            GemmaRepository.InferenceState.READY -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("AI paruoštas", style = MaterialTheme.typography.bodyMedium,
                                            color = OnSurface, fontWeight = FontWeight.Medium)
                                        Text("Gemma 4 E2B · llama.cpp · ~2.7 GB RAM", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                }
                                OutlinedButton(onClick = { vm.unloadModelFromMemory() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Iškrauti iš atminties")
                                }
                            }

                            GemmaRepository.InferenceState.ERROR -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                    Text("Nepavyko įkelti modelio", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                }
                                Button(onClick = { vm.loadModelForInference() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Bandyti dar kartą")
                                }
                            }
                        }
                    }
                }
            }

            // ── Test section (only when inference is ready) ───────────────────
            if (state.inferenceState == GemmaRepository.InferenceState.READY) {
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Testas", style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = testInput,
                            onValueChange = { testInput = it },
                            placeholder = { Text("Pvz.: Kiek išleidau maistui birželį?") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = { vm.runTestQuery(testInput) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = testInput.isNotBlank() && !state.isTestRunning
                        ) {
                            if (state.isTestRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.isTestRunning) "Generuojama..." else "Siųsti")
                        }
                        state.testResponse?.let { response ->
                            Surface(shape = MaterialTheme.shapes.small, color = SurfaceContainerLow) {
                                Text(response, modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            }
                        }
                    }
                }
            }

            // ── Storage info ──────────────────────────────────────────────────
            state.storageInfo?.let { storage ->
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saugykla", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        StorageRow("Modelio dydis", "${storage.modelSizeBytes / 1_000_000} MB")
                        StorageRow("Laisva vieta", "${storage.availableBytes / 1_000_000} MB")
                    }
                }
            }

            // ── Update check (only relevant when model is already downloaded) ──
            if (state.modelStatus is ModelStatus.Ready || state.modelStatus is ModelStatus.UpdateAvailable) LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Tikrinti atnaujinimus", style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface, fontWeight = FontWeight.Medium)
                        state.lastChecked?.let {
                            Text("Paskutinį kartą: $it", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                    if (state.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.checkUpdate() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Tikrinti", tint = Primary)
                        }
                    }
                }
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun StatusChip(status: ModelStatus) {
    val (label, color) = when (status) {
        is ModelStatus.Ready           -> "Paruošta"      to Color(0xFF2E7D32)
        is ModelStatus.Downloading     -> "Atsisiunčiama" to Primary
        is ModelStatus.Verifying       -> "Tikrinama"     to Primary
        is ModelStatus.UpdateAvailable -> "Atnaujinimas"  to Color(0xFFE65100)
        is ModelStatus.Error           -> "Klaida"        to MaterialTheme.colorScheme.error
        is ModelStatus.Deleting        -> "Trinama"       to OnSurfaceVariant
        else                           -> "Neatsisiųsta"  to OnSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.12f)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WarningBanner(message: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun InfoBanner(message: String, color: Color = Primary) {
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.1f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = OnSurface, fontWeight = FontWeight.Medium)
    }
}
