package com.skyboys.personalfinance

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.skyboys.personalfinance.data.FinanceDao
import com.skyboys.personalfinance.importer.PdfPasswordRequiredException
import com.skyboys.personalfinance.importer.PdfStatementEntry
import com.skyboys.personalfinance.importer.PdfStatementParser
import com.skyboys.personalfinance.notifications.BudgetAlertEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfStatementImportDialog(dao: FinanceDao, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by dao.observeAccounts().collectAsState(initial = emptyList())
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(accounts) { if (selectedAccountId == null) selectedAccountId = accounts.firstOrNull { it.type != "CREDIT_CARD" }?.id }
    var accountMenu by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<PdfStatementEntry>>(emptyList()) }
    var skippedLines by remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            password = ""
            loading = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { PdfStatementParser.read(context, uri) }
                    entries = result.entries
                    skippedLines = result.skippedLines
                    preview = result.entries.isNotEmpty()
                    if (entries.isEmpty()) errorMessage = "No readable transaction rows were found. This PDF may be scanned or use an unsupported statement layout."
                } catch (_: PdfPasswordRequiredException) {
                    passwordDialog = true
                } catch (e: Exception) {
                    errorMessage = "Could not read this PDF: ${e.message ?: "unsupported or damaged file"}"
                } finally { loading = false }
            }
        }
    }

    if (passwordDialog) {
        AlertDialog(
            onDismissRequest = { passwordDialog = false; password = ""; selectedUri = null },
            title = { Text("Unlock bank statement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This PDF is password-protected. Enter its password to read it on this device.")
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("PDF password") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty() && !loading, onClick = {
                    val uri = selectedUri ?: return@TextButton
                    passwordDialog = false
                    loading = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { PdfStatementParser.read(context, uri, password) }
                            entries = result.entries
                            skippedLines = result.skippedLines
                            preview = result.entries.isNotEmpty()
                            if (entries.isEmpty()) errorMessage = "No readable transaction rows were found. Check the password and statement format."
                        } catch (e: Exception) {
                            if (e is PdfPasswordRequiredException) {
                                Toast.makeText(context, "Incorrect PDF password. Please try again.", Toast.LENGTH_LONG).show()
                                passwordDialog = true
                            } else {
                                errorMessage = "Could not read this PDF: ${e.message ?: "unsupported or damaged file"}"
                            }
                        } finally {
                            password = ""
                            loading = false
                        }
                    }
                }) { Text("Unlock & read") }
            },
            dismissButton = { TextButton(onClick = { passwordDialog = false; password = ""; selectedUri = null }) { Text("Cancel") } }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Statement import") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }

    if (preview) {
        val unresolved = entries.count { it.needsDirectionReview }
        AlertDialog(
            onDismissRequest = { preview = false; entries = emptyList(); password = "" },
            title = { Text("Review statement transactions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${entries.size} rows found · $skippedLines lines skipped. Review debit/credit where requested, then import.")
                    ExposedDropdownMenuBox(expanded = accountMenu, onExpandedChange = { accountMenu = it }) {
                        OutlinedTextField(
                            value = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Choose bank account",
                            onValueChange = {}, readOnly = true, label = { Text("Bank account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            accounts.filter { it.type != "CREDIT_CARD" }.forEach { account ->
                                DropdownMenuItem(text = { Text(account.name) }, onClick = { selectedAccountId = account.id; accountMenu = false })
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(entries) { index, entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.transaction.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(entry.transaction.dateMillis))} · ₹${"%.2f".format(Locale("en", "IN"), entry.transaction.amountPaise / 100.0)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = entry.transaction.type == "EXPENSE",
                                        onClick = { entries = entries.toMutableList().also { it[index] = entry.copy(transaction = entry.transaction.copy(type = "EXPENSE"), needsDirectionReview = false) } },
                                        label = { Text("Debit / expense") }
                                    )
                                    FilterChip(
                                        selected = entry.transaction.type == "INCOME",
                                        onClick = { entries = entries.toMutableList().also { it[index] = entry.copy(transaction = entry.transaction.copy(type = "INCOME"), needsDirectionReview = false) } },
                                        label = { Text("Credit / income") }
                                    )
                                    if (entry.needsDirectionReview) Text("Review", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (unresolved > 0) Text("$unresolved rows need debit/credit confirmation.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = entries.isNotEmpty() && selectedAccountId != null && unresolved == 0, onClick = {
                    val accountId = selectedAccountId ?: return@TextButton
                    val toImport = entries.map { it.transaction.copy(accountId = accountId) }
                    preview = false
                    loading = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val existing = dao.allTransactions().map { item ->
                                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(item.dateMillis))
                                    "$day|${item.title.trim().lowercase(Locale.ROOT)}|${item.amountPaise}|${item.type}|${item.accountId ?: "unassigned"}"
                                }.toMutableSet()
                                val unique = toImport.filter { item ->
                                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(item.dateMillis))
                                    existing.add("$day|${item.title.trim().lowercase(Locale.ROOT)}|${item.amountPaise}|${item.type}|${item.accountId ?: "unassigned"}")
                                }
                                if (unique.isNotEmpty()) {
                                    dao.insertAll(unique)
                                    unique.forEach { BudgetAlertEvaluator.evaluate(context, it) }
                                }
                                unique.size to (toImport.size - unique.size)
                            }
                            Toast.makeText(context, "Imported ${result.first} transactions. ${result.second} duplicates skipped.", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            loading = false
                            password = ""
                        }
                    }
                }) { Text(if (loading) "Importing…" else "Import transactions") }
            },
            dismissButton = { TextButton(onClick = { preview = false; entries = emptyList(); password = "" }) { Text("Cancel") } }
        )
    } else if (!passwordDialog && errorMessage == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import bank statement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Read a PDF statement on this device. Password-protected PDFs are supported. No statement is uploaded.")
                    if (loading) Text("Reading PDF…")
                    else Text("Choose a PDF to preview its transactions before importing.")
                }
            },
            confirmButton = { TextButton(enabled = !loading, onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Choose PDF") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}
