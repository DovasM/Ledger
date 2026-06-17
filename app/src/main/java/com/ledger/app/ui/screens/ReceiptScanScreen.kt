package com.ledger.app.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerTextField
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.viewmodel.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanScreen(
    navController: NavController,
    receiptViewModel: ReceiptViewModel = hiltViewModel(),
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val receiptState by receiptViewModel.state.collectAsStateWithLifecycle()
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraFileUri by remember { mutableStateOf<Uri?>(null) }

    val expenseCategoryNames = categoryState.categories.filter { it.isExpense }.map { it.name }
        .ifEmpty { listOf("Maistas", "Transportas", "Sveikata", "Pramogos", "Kita") }

    // Editable form fields
    var formTitle by remember { mutableStateOf("") }
    var formAmount by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf("") }
    var formDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedWalletIndex by remember { mutableStateOf(0) }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val previewExpense = (receiptState as? ReceiptViewModel.State.Preview)?.expense
    LaunchedEffect(previewExpense, expenseCategoryNames) {
        previewExpense?.let { exp ->
            formTitle = exp.store
            formAmount = if (exp.amount > 0) "%.2f".format(exp.amount) else ""
            val matched = expenseCategoryNames.firstOrNull { it.equals(exp.category, ignoreCase = true) }
                ?: expenseCategoryNames.firstOrNull { it.contains(exp.category, ignoreCase = true) }
                ?: expenseCategoryNames.firstOrNull()
                ?: exp.category
            formCategory = matched
            runCatching { LocalDate.parse(exp.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                ?.let { formDate = it }
        }
    }

    // Camera permission + launch
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraFileUri?.let { uri ->
                loadBitmap(context, uri)?.let { bmp ->
                    capturedBitmap = bmp
                    receiptViewModel.processImage(bmp)
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "photos").also { it.mkdirs() }
                .let { File(it, "receipt_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "com.ledger.app.fileprovider", file)
            cameraFileUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            loadBitmap(context, it)?.let { bmp ->
                capturedBitmap = bmp
                receiptViewModel.processImage(bmp)
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = formDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        formDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK", color = Tertiary) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Atšaukti") } }
        ) { DatePicker(state = dpState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Čekio skenavimas", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Uždaryti")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = receiptState) {
                is ReceiptViewModel.State.Idle -> {
                    Text(
                        "Pasirink čekio nuotrauką",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Text(
                        "AI nuskaitys tekstą ir automatiškai užpildys transakcijos duomenis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Kamera")
                        }
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Photo, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Galerija")
                        }
                    }
                }

                is ReceiptViewModel.State.OcrRunning,
                is ReceiptViewModel.State.AiRunning -> {
                    capturedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = Primary)
                            Text(
                                if (s is ReceiptViewModel.State.OcrRunning) "Skaitomas čekis..." else "AI analizuoja...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }

                is ReceiptViewModel.State.Error -> {
                    capturedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    s.msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Button(
                                onClick = { receiptViewModel.reset(); capturedBitmap = null },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Bandyti dar kartą") }
                        }
                    }
                }

                is ReceiptViewModel.State.Preview -> {
                    capturedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        "Tikrink ir redaguok",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )

                    // Wallet
                    if (walletState.wallets.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = walletMenuExpanded,
                            onExpandedChange = { walletMenuExpanded = it }
                        ) {
                            LedgerTextField(
                                value = walletState.wallets.getOrNull(selectedWalletIndex)?.name ?: "",
                                onValueChange = {},
                                label = "Piniginė",
                                leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, null, tint = OnSurfaceVariant) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = walletMenuExpanded,
                                onDismissRequest = { walletMenuExpanded = false }
                            ) {
                                walletState.wallets.forEachIndexed { idx, wallet ->
                                    DropdownMenuItem(
                                        text = { Text(wallet.name) },
                                        onClick = { selectedWalletIndex = idx; walletMenuExpanded = false }
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Nėra piniginių. Pridėk piniginę nustatymuose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    // Amount
                    LedgerTextField(
                        value = formAmount,
                        onValueChange = { formAmount = it },
                        label = "Suma (€)",
                        leadingIcon = { Icon(Icons.Filled.AttachMoney, null, tint = OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category
                    ExposedDropdownMenuBox(
                        expanded = categoryMenuExpanded,
                        onExpandedChange = { categoryMenuExpanded = it }
                    ) {
                        LedgerTextField(
                            value = formCategory,
                            onValueChange = {},
                            label = "Kategorija",
                            leadingIcon = { Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            expenseCategoryNames.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = { formCategory = cat; categoryMenuExpanded = false }
                                )
                            }
                        }
                    }

                    // Title / store
                    LedgerTextField(
                        value = formTitle,
                        onValueChange = { formTitle = it },
                        label = "Parduotuvė / pavadinimas",
                        leadingIcon = { Icon(Icons.Filled.Store, null, tint = OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date
                    LedgerTextField(
                        value = formDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        onValueChange = {},
                        label = "Data",
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = OnSurfaceVariant) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.EditCalendar, null, tint = Tertiary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Items preview
                    if (s.expense.items.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Aptiktos prekės:", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                s.expense.items.forEach { item ->
                                    Text("• $item", style = MaterialTheme.typography.bodySmall, color = OnSurface)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    val amountVal = formAmount.toDoubleOrNull()
                    Button(
                        onClick = {
                            val walletId = walletState.wallets.getOrNull(selectedWalletIndex)?.id
                            if (walletId != null && amountVal != null && amountVal > 0) {
                                transactionViewModel.createTransaction(
                                    walletId = walletId,
                                    title = formTitle.ifBlank { formCategory },
                                    category = formCategory,
                                    amount = amountVal,
                                    isIncome = false,
                                    note = null,
                                    createdAt = formDate.atStartOfDay().atOffset(ZoneOffset.UTC)
                                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                    tagNames = emptyList()
                                ) { navController.popBackStack() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Tertiary),
                        shape = RoundedCornerShape(6.dp),
                        enabled = amountVal != null && amountVal > 0 && walletState.wallets.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Done, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sukurti transakciją", style = MaterialTheme.typography.labelLarge)
                    }

                    TextButton(
                        onClick = { receiptViewModel.reset(); capturedBitmap = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skenuoti kitą čekį", color = Tertiary)
                    }
                }
            }
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val maxDim = 1920
        val scale = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / maxDim)
        BitmapFactory.Options().apply { inSampleSize = scale }.let { finalOpts ->
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, finalOpts) }
        }
    } catch (e: Exception) {
        null
    }
}
