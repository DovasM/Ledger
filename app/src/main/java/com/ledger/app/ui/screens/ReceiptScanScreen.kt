package com.ledger.app.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ledger.app.ui.components.LedgerTextField
import com.ledger.app.ui.theme.*
import com.ledger.app.ui.util.capitalizeFirst
import com.ledger.app.ui.viewmodel.*
import kotlinx.coroutines.launch
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
    walletViewModel: WalletViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val receiptState by receiptViewModel.state.collectAsStateWithLifecycle()
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val categoryState by categoryViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // rememberSaveable: the capture URI must survive Activity recreation while the camera app
    // is foregrounded (Uri is Parcelable), otherwise the result callback has nothing to load.
    var cameraFileUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Editable form fields
    var formStore by remember { mutableStateOf("") }
    var formDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPhotoViewer by remember { mutableStateOf(false) }
    var selectedWalletIndex by remember { mutableStateOf(0) }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf<EditableItem>() }

    val categoryOptions = (categoryState.categories.filter { it.isExpense }.map { it.name } +
        items.map { it.category })
        .filter { it.isNotBlank() }.distinct()
        .ifEmpty { listOf("Food", "Household", "Transport", "Health", "Entertainment", "Other") }

    val previewReceipt = (receiptState as? ReceiptViewModel.State.Preview)?.receipt
    LaunchedEffect(previewReceipt) {
        previewReceipt?.let { r ->
            formStore = r.store
            runCatching { LocalDate.parse(r.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                ?.let { formDate = it }
            items.clear()
            items.addAll(r.items.map {
                EditableItem(it.name, if (it.price > 0) "%.2f".format(it.price) else "", it.category)
            })
            if (items.isEmpty()) items.add(EditableItem("", "", ""))
            // Auto-fill categories the model left blank — sequential, one at a time, since a
            // single native engine backs every suggestion. Skips rows the user already filled.
            items.toList().forEach { ei ->
                if (ei.category.isBlank() && ei.name.isNotBlank()) {
                    ei.suggesting = true
                    val s = receiptViewModel.suggestCategory(ei.name)
                    if (s != null && ei.category.isBlank()) ei.category = s
                    ei.suggesting = false
                }
            }
        }
    }

    fun decodeAndProcess(uri: Uri) {
        loadBitmap(context, uri)?.let { bmp ->
            capturedBitmap = bmp
            receiptViewModel.processImage(bmp)
        }
    }

    // Camera: do NOT gate purely on `success` — several OEM camera apps return false even on a
    // good capture. Decode from the file whenever it has content; a genuine cancel yields null.
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { _ ->
        cameraFileUri?.let { decodeAndProcess(it) }
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

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { decodeAndProcess(it) }
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
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (showPhotoViewer) {
        capturedBitmap?.let { bmp ->
            FullScreenPhotoViewer(bitmap = bmp, onDismiss = { showPhotoViewer = false })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan receipt", style = MaterialTheme.typography.headlineSmall) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = receiptState) {
                is ReceiptViewModel.State.Idle -> {
                    Text(
                        "Pick a receipt photo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Text(
                        "The AI reads the text and splits the receipt into one categorized transaction per product.",
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
                            Text("Camera")
                        }
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Photo, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Gallery")
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
                                if (s is ReceiptViewModel.State.OcrRunning) "Reading receipt…"
                                else "AI is analyzing the receipt…\nThis can take up to a minute on CPU.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }

                is ReceiptViewModel.State.Saving -> {
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
                            Text("Creating transactions…", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
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
                            ) { Text("Try again") }
                        }
                    }
                }

                is ReceiptViewModel.State.Preview -> {
                    capturedBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showPhotoViewer = true }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Receipt photo — tap to enlarge",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.ZoomIn, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Enlarge", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }

                    Text(
                        "Review & edit",
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
                                label = "Wallet",
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
                        Text("No wallets. Add a wallet in settings first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    // Store / source
                    LedgerTextField(
                        value = formStore,
                        onValueChange = { formStore = it },
                        label = "Store (saved as note)",
                        leadingIcon = { Icon(Icons.Filled.Store, null, tint = OnSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date
                    LedgerTextField(
                        value = formDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        onValueChange = {},
                        label = "Date",
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, null, tint = OnSurfaceVariant) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.EditCalendar, null, tint = Tertiary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Items header + running total
                    val parsedTotals = items.mapNotNull { it.amount.replace(',', '.').toDoubleOrNull() }
                    val itemsSum = parsedTotals.sum()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Products (${items.size})", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = OnSurface)
                        Text("Σ %.2f €".format(itemsSum), style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant)
                    }
                    if (s.receipt.total > 0 && kotlin.math.abs(s.receipt.total - itemsSum) > 0.05) {
                        Text(
                            "Receipt total reads %.2f € — adjust items so they add up.".format(s.receipt.total),
                            style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100)
                        )
                    }

                    items.forEachIndexed { idx, item ->
                        ItemEditRow(
                            item = item,
                            categoryOptions = categoryOptions,
                            onDelete = { if (idx < items.size) items.removeAt(idx) },
                            onSuggestCategory = {
                                if (item.name.isNotBlank() && !item.suggesting) {
                                    scope.launch {
                                        item.suggesting = true
                                        receiptViewModel.suggestCategory(item.name)?.let { item.category = it }
                                        item.suggesting = false
                                    }
                                }
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { items.add(EditableItem("", "", categoryOptions.firstOrNull() ?: "")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add product")
                    }

                    Spacer(Modifier.height(4.dp))

                    val newItems = items.mapNotNull { ei ->
                        val amt = ei.amount.replace(',', '.').toDoubleOrNull()
                        if (amt != null && amt > 0) ReceiptViewModel.NewItem(ei.name, amt, ei.category) else null
                    }
                    val walletId = walletState.wallets.getOrNull(selectedWalletIndex)?.id
                    Button(
                        onClick = {
                            if (walletId != null && newItems.isNotEmpty()) {
                                receiptViewModel.confirmAndCreate(
                                    walletId = walletId,
                                    store = formStore,
                                    dateIso = formDate.atStartOfDay().atOffset(ZoneOffset.UTC)
                                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                    items = newItems
                                ) { navController.popBackStack() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Tertiary),
                        shape = RoundedCornerShape(6.dp),
                        enabled = walletId != null && newItems.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Done, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Create ${newItems.size} transaction${if (newItems.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    TextButton(
                        onClick = { receiptViewModel.reset(); capturedBitmap = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan another receipt", color = Tertiary)
                    }
                }
            }
        }
    }
}

// Compose-state-backed editable product, so field edits and list add/remove recompose.
private class EditableItem(name: String, amount: String, category: String) {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    var category by mutableStateOf(category)
    var suggesting by mutableStateOf(false)  // AI is picking this row's category
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditRow(
    item: EditableItem,
    categoryOptions: List<String>,
    onDelete: () -> Unit,
    onSuggestCategory: () -> Unit
) {
    var catExpanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LedgerTextField(
                value = item.name,
                onValueChange = { item.name = it },
                label = "Product",
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerTextField(
                    value = item.amount,
                    onValueChange = { item.amount = it },
                    label = "Price (€)",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove product", tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    LedgerTextField(
                        value = item.category,
                        onValueChange = { item.category = capitalizeFirst(it) },
                        label = "Category",
                        leadingIcon = { Icon(Icons.Filled.Category, null, tint = OnSurfaceVariant) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categoryOptions.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { item.category = c; catExpanded = false })
                        }
                    }
                }
                // AI auto-suggest a category for this product.
                if (item.suggesting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Tertiary)
                } else {
                    IconButton(onClick = onSuggestCategory, enabled = item.name.isNotBlank()) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Suggest category with AI", tint = Tertiary)
                    }
                }
            }
        }
    }
}

// Full-screen, pinch-to-zoom viewer for the captured receipt photo. Shown over the whole
// screen (usePlatformDefaultWidth = false); dismissed by back gesture, tapping the scrim, or ✕.
@Composable
private fun FullScreenPhotoViewer(bitmap: Bitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            // Only allow panning while zoomed in; snap back to centre at 1×.
            offset = if (scale > 1f) offset + panChange else Offset.Zero
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Receipt photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(transformState)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0) return null
        val maxDim = 1920
        val scale = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / maxDim)
        val bmp = BitmapFactory.Options().apply { inSampleSize = scale }.let { finalOpts ->
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, finalOpts) }
        } ?: return null
        val rotation = readExifRotation(context, uri)
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } else bmp
    } catch (e: Exception) {
        null
    }
}

// Camera photos are usually stored with an EXIF orientation flag rather than physically rotated
// pixels; OCR accuracy drops sharply on a sideways image, so rotate upright before recognition.
private fun readExifRotation(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }
}
