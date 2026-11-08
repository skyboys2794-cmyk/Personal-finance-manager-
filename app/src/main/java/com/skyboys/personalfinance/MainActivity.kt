package com.skyboys.personalfinance

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.skyboys.personalfinance.backup.EncryptedBackup
import com.skyboys.personalfinance.data.FinanceAccount
import com.skyboys.personalfinance.data.FinanceDatabase
import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.importer.CsvTransactionParser
import com.skyboys.personalfinance.reports.FinanceAnalytics
import com.skyboys.personalfinance.notifications.FinanceNotifications
import com.skyboys.personalfinance.notifications.BudgetAlertEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF176B55)
private val Ink = Color(0xFF172B25)
private val Muted = Color(0xFF708078)
private val Mint = Color(0xFFE7F3ED)
private val Coral = Color(0xFFD96B58)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = FinanceDatabase.get(this).financeDao()
        FinanceNotifications.ensureChannel(this)
        FinanceNotifications.scheduleBillChecks(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 701)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            if (dao.allAccounts().isEmpty()) {
                dao.insertAccount(FinanceAccount(name = "Cash wallet", type = "CASH"))
                dao.insertAccount(FinanceAccount(name = "Primary bank", type = "BANK"))
            }
        }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest, onPrimary = Color.White, primaryContainer = Mint,
                    secondary = Color(0xFFB7D8C8), background = Color(0xFFF6F8F6),
                    surface = Color.White, onSurface = Ink, error = Coral
                )
            ) { FinanceApp(dao) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceApp(dao: com.skyboys.personalfinance.data.FinanceDao) {
    val context = LocalContext.current
    val transactions by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by dao.observeAccounts().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var backupDialog by remember { mutableStateOf(false) }
    var backupMode by remember { mutableStateOf("export") }
    var backupPassword by remember { mutableStateOf("") }
    val summary = remember(transactions) { FinanceAnalytics.summarize(transactions) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val csv = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
                val parsed = withContext(Dispatchers.Default) { CsvTransactionParser.parse(csv) }
                if (parsed.transactions.isNotEmpty()) withContext(Dispatchers.IO) { dao.insertAll(parsed.transactions) }
                Toast.makeText(context, "Imported ${parsed.transactions.size} transactions. ${parsed.errors.size} rows skipped.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "CSV import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { EncryptedBackup.export(dao.allTransactions(), backupPassword.toCharArray()) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                Toast.makeText(context, "Encrypted backup saved.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show() }
            backupPassword = ""
        }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf() }
                val restored = withContext(Dispatchers.Default) { EncryptedBackup.restore(bytes, backupPassword.toCharArray()) }
                withContext(Dispatchers.IO) { dao.insertAll(restored) }
                Toast.makeText(context, "Restored ${restored.size} transactions.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(context, "Restore failed. Check the password and backup file.", Toast.LENGTH_LONG).show() }
            backupPassword = ""
        }
    }

    Scaffold(
        containerColor = Color(0xFFF6F8F6),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Good day", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Text("Your finances", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(onClick = { csvPicker.launch(arrayOf("text/*", "application/vnd.ms-excel")) }) { Text("Import CSV") }
                    TextButton(onClick = { backupMode = "export"; backupDialog = true }) { Text("Backup") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF6F8F6))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.TrendingUp, null) }, label = { Text("Overview") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Activity") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Savings, null) }, label = { Text("Accounts") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Plans") })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Forest, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(Modifier.padding(padding), transactions, summary.incomePaise, summary.expensePaise, summary.netCashFlowPaise, summary.expenseByCategory)
            1 -> ActivityList(Modifier.padding(padding), transactions) { id -> scope.launch(Dispatchers.IO) { dao.delete(id) } }
            tab == 2 -> AccountsList(Modifier.padding(padding), accounts, dao) { name, institution, type, balance ->
                scope.launch(Dispatchers.IO) { dao.insertAccount(FinanceAccount(name = name, institution = institution, type = type, openingBalancePaise = (balance * 100).toLong())) }
            }
            else -> ManagementScreen(dao)
        }
    }

    if (showAdd) AddTransactionDialog(accounts, { showAdd = false }) { title, amount, type, category, accountId, method, tags ->
        scope.launch(Dispatchers.IO) {
            val transaction = FinanceTransaction(title = title, amountPaise = (amount * 100).toLong(), type = type,
                category = category, accountId = accountId, paymentMethod = method, tags = tags)
            dao.insert(transaction)
            BudgetAlertEvaluator.evaluate(context, transaction)
        }
        showAdd = false
    }

    if (backupDialog) {
        AlertDialog(
            onDismissRequest = { backupDialog = false; backupPassword = "" },
            title = { Text(if (backupMode == "export") "Create encrypted backup" else "Restore backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your transaction history is encrypted on your device before saving. Keep this password safe; it cannot be recovered.")
                    OutlinedTextField(
                        value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("Backup password (10+ characters)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = backupPassword.length >= 10, onClick = {
                    backupDialog = false
                    if (backupMode == "export") backupExporter.launch("personal-finance-backup.pfm")
                    else backupImporter.launch(arrayOf("*/*"))
                }) { Text(if (backupMode == "export") "Save backup" else "Choose backup") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { backupMode = "restore" }) { Text("Restore instead") }
                    TextButton(onClick = { backupDialog = false; backupPassword = "" }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun Dashboard(modifier: Modifier, transactions: List<FinanceTransaction>, income: Long, expenses: Long, netFlow: Long, categories: Map<String, Long>) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Forest)) {
                Column(Modifier.padding(22.dp)) {
                    Text("NET CASH FLOW · THIS MONTH", color = Color(0xFFD1E9DD), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(money(netFlow), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryMetric("Income", income, Color(0xFFBDE8D2), Modifier.weight(1f))
                        SummaryMetric("Spent", expenses, Color(0xFFFFC9BD), Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spending breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(SimpleDateFormat("MMMM", Locale.getDefault()).format(Date()), color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (categories.isEmpty()) item { EmptyCard("Your spending chart will appear here", "Add expenses to see where your money goes.") }
        else {
            item { SpendingChart(categories) }
            items(categories.entries.toList()) { (category, amount) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Forest))
                    Text(category, Modifier.weight(1f).padding(start = 10.dp), color = Ink)
                    Text(money(amount), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Text("Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(transactions.take(4), key = { it.id }) { TransactionRow(it) }
        if (transactions.isEmpty()) item { EmptyCard("No transactions yet", "Tap + to add your first income or expense.") }
    }
}

@Composable
private fun SummaryMetric(label: String, amount: Long, tint: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium)
        Text(money(amount), color = tint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SpendingChart(categories: Map<String, Long>) {
    val max = (categories.values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.entries.take(6).forEach { (name, amount) ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    Text(money(amount), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    drawRoundRect(Color(0xFFE8EEE9), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                    drawRoundRect(Forest, size = size.copy(width = size.width * amount.toFloat() / max), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun ActivityList(modifier: Modifier, transactions: List<FinanceTransaction>, onDelete: (Long) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("All transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(transactions, key = { it.id }) { TransactionRow(it) { onDelete(it.id) } }
        if (transactions.isEmpty()) item { EmptyCard("Nothing here yet", "Your saved transactions will appear here.") }
    }
}

@Composable
private fun TransactionRow(transaction: FinanceTransaction, onDelete: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(if (transaction.type == "INCOME") Mint else Color(0xFFFFEFEC)), contentAlignment = Alignment.Center) {
                Text(if (transaction.type == "INCOME") "↗" else "↘", color = if (transaction.type == "INCOME") Forest else Coral)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(transaction.title, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("${transaction.category} · ${dateLabel(transaction.dateMillis)} · ${transaction.paymentMethod}", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (transaction.tags.isNotBlank()) Text(transaction.tags, color = Forest, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (transaction.type == "INCOME") "+" else "−") + money(transaction.amountPaise), color = if (transaction.type == "INCOME") Forest else Coral, fontWeight = FontWeight.Bold)
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete transaction", tint = Muted) }
            }
        }
    }
}

@Composable
private fun AccountsList(
    modifier: Modifier,
    accounts: List<FinanceAccount>,
    dao: com.skyboys.personalfinance.data.FinanceDao,
    onAdd: (String, String, String, Double) -> Unit
) {
    val transactions by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var addAccount by remember { mutableStateOf(false) }
    val netWorth = remember(accounts, transactions) { FinanceAnalytics.netWorth(accounts, transactions) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your accounts", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { addAccount = true }) { Text("Add account") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Mint)) {
                Column(Modifier.padding(16.dp)) {
                    Text("ESTIMATED NET WORTH", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text(money(netWorth), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Forest)
                }
            }
        }
        item { Text("Track cash, bank accounts and credit cards manually. No bank connection is required.", color = Muted) }
        items(accounts, key = { it.id }) { account ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Savings, null, tint = Forest)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(account.name, fontWeight = FontWeight.SemiBold)
                        Text("${account.institution.ifBlank { account.type }} · ${account.currency}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(money(account.openingBalancePaise), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (addAccount) AddAccountDialog(onDismiss = { addAccount = false }) { name, institution, type, balance ->
        onAdd(name, institution, type, balance)
        addAccount = false
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BANK") }
    var balance by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Account name") }, singleLine = true)
                OutlinedTextField(institution, { institution = it }, label = { Text("Bank / institution") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("BANK", "CASH", "CREDIT_CARD", "WALLET").forEach { item ->
                        FilterChip(type == item, { type = item }, label = { Text(item) })
                    }
                }
                OutlinedTextField(balance, { balance = it }, label = { Text("Opening balance (₹; debt can be negative)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && balance.toDoubleOrNull()?.isFinite() == true, onClick = {
                onSave(name.trim(), institution.trim(), type, balance.toDouble())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionDialog(accounts: List<FinanceAccount>, onDismiss: () -> Unit, onSave: (String, Double, String, String, Long?, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("Food") }
    var accountId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }
    var method by remember { mutableStateOf("UPI") }
    var tags by remember { mutableStateOf("") }
    var accountExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Food", "Travel", "Bills", "Shopping", "Health", "Salary", "Business", "Other")
    val methods = listOf("UPI", "CASH", "CARD", "BANK_TRANSFER", "WALLET")
    val validAmount = amount.toDoubleOrNull()?.let { it > 0 && it.isFinite() } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "EXPENSE", { type = "EXPENSE" }, label = { Text("Expense") })
                    FilterChip(type == "INCOME", { type = "INCOME" }, label = { Text("Income") })
                }
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Column {
                    categories.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            row.forEach { item -> FilterChip(category == item, { category = item }, label = { Text(item, style = MaterialTheme.typography.labelSmall) }) }
                        }
                    }
                }
                Text("Payment method", style = MaterialTheme.typography.labelMedium)
                methods.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { item -> FilterChip(method == item, { method = item }, label = { Text(item, style = MaterialTheme.typography.labelSmall) }) }
                    }
                }
                if (accounts.isNotEmpty()) {
                    ExposedDropdownMenuBox(accountExpanded, { accountExpanded = it }) {
                        OutlinedTextField(
                            value = accounts.firstOrNull { it.id == accountId }?.name ?: "Select account",
                            onValueChange = {}, readOnly = true, label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(accountExpanded, { accountExpanded = false }) {
                            accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { accountId = a.id; accountExpanded = false }) }
                        }
                    }
                }
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && validAmount, onClick = { onSave(title.trim(), amount.toDouble(), type, category, accountId, method, tags) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun money(paise: Long): String = "₹" + String.format(Locale("en", "IN"), "%,.2f", paise / 100.0)
private fun dateLabel(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
