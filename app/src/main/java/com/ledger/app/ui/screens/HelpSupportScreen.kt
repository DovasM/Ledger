package com.ledger.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ledger.app.ui.components.*
import com.ledger.app.ui.theme.*

private data class FaqItem(val question: String, val answer: String)

private val faqItems = listOf(
    FaqItem("How do I add a transaction?",
        "Tap the '+' button on the Dashboard or navigate to Activity and tap the '+' button. Fill in the amount, category, and description."),
    FaqItem("How do categories work?",
        "Every transaction is assigned a category. You can manage categories in Settings → Manage Categories. Custom categories can be created with custom icons and colors."),
    FaqItem("Can I connect my bank account?",
        "Yes! Go to Settings → Connected Accounts to link Revolut, Trading 212, and other supported brokers. Bank CSV files can also be imported via Settings → Import Transactions."),
    FaqItem("How are budgets calculated?",
        "Budgets are based on your transaction categories. Set a monthly limit per category in Settings → Budgets. You'll be alerted when you approach your limit."),
    FaqItem("Is my financial data secure?",
        "All data is stored locally on your device. We use industry-standard encryption. No financial data is shared with third parties without your explicit consent."),
    FaqItem("How do recurring transactions work?",
        "Recurring transactions are templates that repeat on a schedule (weekly, monthly, etc.). Go to Settings → Recurring Transactions to manage them."),
    FaqItem("What is the Net Worth screen?",
        "Net Worth shows total assets minus total liabilities. It gives you a complete financial snapshot. Access it via Settings → Net Worth."),
    FaqItem("How do I export my data?",
        "Go to Settings → Import / Export to export all transactions as a CSV file. This can be opened in Excel or any spreadsheet app."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(navController: NavController) {
    var expandedFaq by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showFeedbackSheet by remember { mutableStateOf(false) }

    if (showFeedbackSheet) {
        ModalBottomSheet(onDismissRequest = { showFeedbackSheet = false }, containerColor = SurfaceContainerLowest, tonalElevation = 0.dp) {
            FeedbackSheet { showFeedbackSheet = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainerLow)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceContainerLow
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Quick actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportCard(Icons.Filled.Feedback, Primary, "Send Feedback", Modifier.weight(1f)) { showFeedbackSheet = true }
                SupportCard(Icons.Filled.Star, Color(0xFFFF9800), "Rate App", Modifier.weight(1f)) {}
                SupportCard(Icons.Filled.Email, Color(0xFF1565C0), "Contact Us", Modifier.weight(1f)) {}
            }

            // FAQ
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("Frequently Asked Questions", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        faqItems.forEachIndexed { idx, faq ->
                            Surface(onClick = { expandedFaq = if (expandedFaq == idx) null else idx }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(faq.question, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Icon(if (expandedFaq == idx) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                    if (expandedFaq == idx) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(faq.answer, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                    }
                                }
                            }
                            if (idx < faqItems.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        }
                    }
                }
            }

            // Legal
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("Legal", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                LedgerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        HelpNavRow(Icons.Filled.Policy, "Privacy Policy", "How we handle your data") {}
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        HelpNavRow(Icons.Filled.Gavel, "Terms of Service", "Usage terms and conditions") {}
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = OutlineVariant.copy(alpha = 0.15f))
                        HelpNavRow(Icons.Filled.Code, "Open Source Licenses", "Third-party libraries used") {}
                    }
                }
            }

            // App info
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("App Version", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Text("Ledger v1.0.0", style = MaterialTheme.typography.bodySmall, color = OnSurface)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Build", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Text("2026.04.05", style = MaterialTheme.typography.bodySmall, color = OnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportCard(icon: ImageVector, color: Color, label: String, modifier: Modifier, onClick: () -> Unit) {
    LedgerCard(modifier = modifier) {
        Surface(onClick = onClick, color = Color.Transparent) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun HelpNavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FeedbackSheet(onDone: () -> Unit) {
    var rating by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Send Feedback", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        Text("How would you rate Ledger?", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { i ->
                IconButton(onClick = { rating = i + 1 }) {
                    Icon(if (i < rating) Icons.Filled.Star else Icons.Filled.StarOutline, null, tint = Color(0xFFFF9800), modifier = Modifier.size(32.dp))
                }
            }
        }
        OutlinedTextField(
            value = message, onValueChange = { message = it },
            label = { Text("Your message (optional)") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedLabelColor = Primary),
            shape = RoundedCornerShape(8.dp)
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(6.dp)) {
            Text("Submit Feedback", style = MaterialTheme.typography.labelLarge)
        }
    }
}
