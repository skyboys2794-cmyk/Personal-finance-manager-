package com.skyboys.personalfinance

import android.os.Bundle
import android.app.DatePickerDialog
import android.os.Build
import android.Manifest
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.skyboys.personalfinance.backup.EncryptedBackup
import com.skyboys.personalfinance.backup.FinanceBackupData
import com.skyboys.personalfinance.backup.GoogleSheetsBackupClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
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
import java.util.Calendar
import java.util.Locale

private val Forest = Color(0xFF087F73)
private val Ink = Color(0xFF18243A)
private val Muted = Color(0xFF7A8798)
private val Mint = Color(0xFFE8F5F1)
private val Coral = Color(0xFFE77769)
private val AppBackground = Color(0xFFF4F7FC)
private val Navy = Color(0xFF172D55)

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
                    secondary = Color(0xFFB7D8C8), background = AppBackground,
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
    var customizeDashboard by remember { mutableStateOf(false) }
    var reportsDialog by remember { mutableStateOf(false) }
    var showSpending by remember { mutableStateOf(context.getSharedPreferences("dashboard_prefs", 0).getBoolean("show_spending", true)) }
    var showRecent by remember { mutableStateOf(context.getSharedPreferences("dashboard_prefs", 0).getBoolean("show_recent", true)) }
    var dashboardPeriod by remember { mutableStateOf("THIS_MONTH") }
    var customStartMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var customEndMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val periodBounds = remember(dashboardPeriod, customStartMillis, customEndMillis) {
        val now = Calendar.getInstance()
        val start = when (dashboardPeriod) {
            "LAST_MONTH" -> (now.clone() as Calendar).apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            "CUSTOM" -> Calendar.getInstance().apply { timeInMillis = customStartMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            else -> (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        }
        val end = when (dashboardPeriod) {
            "LAST_MONTH" -> (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            "CUSTOM" -> Calendar.getInstance().apply { timeInMillis = customEndMillis; add(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            else -> (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        }
        Triple(start.timeInMillis, end.timeInMillis, when (dashboardPeriod) {
            "LAST_MONTH" -> "Last month"
            "CUSTOM" -> "${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(customStartMillis))} – ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(customEndMillis))}"
            else -> "This month"
        })
    }
    val summary = remember(transactions, periodBounds) {
        FinanceAnalytics.summarizeRange(transactions, periodBounds.first, periodBounds.second, periodBounds.third)
    }
    val periodDuration = (periodBounds.second - periodBounds.first).coerceAtLeast(1L)
    val previousPeriodExpense = remember(transactions, periodBounds) {
        FinanceAnalytics.summarizeRange(transactions, periodBounds.first - periodDuration, periodBounds.first, "Previous period").expensePaise
    }
    val periodTransactions = remember(transactions, periodBounds) {
        transactions.filter { it.dateMillis >= periodBounds.first && it.dateMillis < periodBounds.second }
    }
    fun chooseCustomRange() {
        val startCal = Calendar.getInstance().apply { timeInMillis = customStartMillis }
        DatePickerDialog(context, { _, sy, sm, sd ->
            val selectedStart = Calendar.getInstance().apply { set(sy, sm, sd, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            val endCal = Calendar.getInstance().apply { timeInMillis = customEndMillis }
            DatePickerDialog(context, { _, ey, em, ed ->
                val selectedEnd = Calendar.getInstance().apply { set(ey, em, ed, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                if (selectedEnd.timeInMillis >= selectedStart.timeInMillis) {
                    customStartMillis = selectedStart.timeInMillis
                    customEndMillis = selectedEnd.timeInMillis
                    dashboardPeriod = "CUSTOM"
                } else {
                    Toast.makeText(context, "End date must be on or after start date.", Toast.LENGTH_SHORT).show()
                }
            }, endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH)).show()
        }, startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH)).show()
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val csv = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
                val parsed = withContext(Dispatchers.Default) { CsvTransactionParser.parse(csv) }
                val importResult = withContext(Dispatchers.IO) {
                    val accountsByName = dao.allAccounts().associateBy { it.name.trim().lowercase(Locale.ROOT) }
                    val mapped = parsed.transactions.mapIndexed { index, transaction ->
                        val accountName = parsed.accountNames.getOrNull(index)
                        transaction.copy(accountId = accountName?.let { accountsByName[it.trim().lowercase(Locale.ROOT)]?.id })
                    }
                    fun duplicateKey(item: FinanceTransaction): String {
                        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(item.dateMillis))
                        return "$day|${item.title.trim().lowercase(Locale.ROOT)}|${item.amountPaise}|${item.type}"
                    }
                    val existing = dao.allTransactions().map(::duplicateKey).toMutableSet()
                    val unique = mapped.filter { existing.add(duplicateKey(it)) }
                    if (unique.isNotEmpty()) {
                        dao.insertAll(unique)
                        unique.forEach { BudgetAlertEvaluator.evaluate(context, it) }
                    }
                    Triple(unique.size, mapped.count { it.accountId == null }, unique)
                }
                val importedCount = importResult.first
                val unmatchedAccounts = importResult.second
                val skipped = parsed.errors.size + parsed.transactions.size - importedCount
                Toast.makeText(context, "Imported $importedCount transactions. $skipped rows skipped or already present. $unmatchedAccounts left without a matched account.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "CSV import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun uploadSheets(token: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = GoogleSheetsBackupClient().createBackup(token, dao.allTransactions())
                withContext(Dispatchers.Main) { Toast.makeText(context, "Google Sheets backup created: " + result.spreadsheetUrl, Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Google Sheets backup failed: " + e.message, Toast.LENGTH_LONG).show() }
            }
        }
    }
    val sheetsAuthorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken }
                .onSuccess { token -> if (!token.isNullOrBlank()) uploadSheets(token) }
                .onFailure { Toast.makeText(context, "Google authorization failed: " + it.message, Toast.LENGTH_LONG).show() }
        }
    }
    fun beginSheetsSync() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/spreadsheets")))
            .build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending != null) sheetsAuthorizationLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) uploadSheets(token)
                }
            }
            .addOnFailureListener { Toast.makeText(context, "Google authorization failed: " + it.message, Toast.LENGTH_LONG).show() }
    }

    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { EncryptedBackup.export(FinanceBackupData(dao.allTransactions(), dao.allAccounts(), dao.allBudgets(), dao.allReminders()), backupPassword.toCharArray()) }
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
                withContext(Dispatchers.IO) { dao.replaceAllData(restored.transactions, restored.accounts, restored.budgets, restored.reminders) }
                FinanceNotifications.scheduleBillChecks(context)
                Toast.makeText(context, "Restored ${restored.transactions.size} transactions, ${restored.accounts.size} accounts, ${restored.budgets.size} budgets and ${restored.reminders.size} reminders.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(context, "Restore failed. Check the password and backup file.", Toast.LENGTH_LONG).show() }
            backupPassword = ""
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Good day", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Text("Finance Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.TrendingUp, null) }, label = { Text("Overview") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Activity") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Savings, null) }, label = { Text("Accounts") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Plans") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        },
        floatingActionButton = {
            if (tab == 0 || tab == 1) {
                FloatingActionButton(onClick = { showAdd = true }, containerColor = Forest, contentColor = Color.White) {
                    Icon(Icons.Default.Add, contentDescription = "Add transaction")
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(
                Modifier.padding(padding), transactions, periodTransactions, accounts,
                summary.incomePaise, summary.expensePaise, summary.netCashFlowPaise,
                summary.expenseByCategory, showSpending, showRecent, periodBounds.third,
                previousPeriodExpense, dashboardPeriod,
                onPeriodChange = { dashboardPeriod = it },
                onCustomRange = { chooseCustomRange() }
            )
            1 -> ActivityList(Modifier.padding(padding), transactions) { id -> scope.launch(Dispatchers.IO) { dao.delete(id) } }
            2 -> AccountManagementScreen(Modifier.padding(padding), accounts, dao)
            3 -> ManagementScreen(dao)
            else -> FinanceSettingsScreen(
                onReports = { reportsDialog = true },
                onImportCsv = { csvPicker.launch(arrayOf("text/*", "application/vnd.ms-excel")) },
                onBackup = { backupMode = "export"; backupDialog = true },
                onRestore = { backupMode = "restore"; backupDialog = true },
                onSheets = { beginSheetsSync() },
                onCustomize = { customizeDashboard = true }
            )
        }
    }

    if (showAdd) AddTransactionDialog(accounts, { showAdd = false }) { title, amount, type, category, accountId, method, tags, recurring, frequency ->
        scope.launch(Dispatchers.IO) {
            val transaction = FinanceTransaction(title = title, amountPaise = (amount * 100).toLong(), type = type,
                category = category, accountId = accountId, paymentMethod = method, tags = tags,
                isRecurring = recurring, recurrenceFrequency = frequency)
            dao.insert(transaction)
            BudgetAlertEvaluator.evaluate(context, transaction)
        }
        showAdd = false
    }

    if (customizeDashboard) {
        AlertDialog(
            onDismissRequest = { customizeDashboard = false },
            title = { Text("Customize dashboard") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showSpending, onCheckedChange = { showSpending = it; context.getSharedPreferences("dashboard_prefs", 0).edit().putBoolean("show_spending", it).apply() })
                        Text("Show spending breakdown")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showRecent, onCheckedChange = { showRecent = it; context.getSharedPreferences("dashboard_prefs", 0).edit().putBoolean("show_recent", it).apply() })
                        Text("Show recent activity")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { customizeDashboard = false }) { Text("Done") } }
        )
    }

    if (backupDialog) {
        AlertDialog(
            onDismissRequest = { backupDialog = false; backupPassword = "" },
            title = { Text(if (backupMode == "export") "Create encrypted backup" else "Restore backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your complete finance data (transactions, accounts, budgets and bill reminders) is encrypted before saving. Restore replaces the current dataset. Keep this password safe; it cannot be recovered.")
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
    if (reportsDialog) {
        Dialog(onDismissRequest = { reportsDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { reportsDialog = false }) { Text("Close reports") }
                    }
                    AdvancedReports(Modifier.weight(1f).fillMaxWidth(), transactions)
                }
            }
        }
    }

}

@Composable
private fun Dashboard(
    modifier: Modifier,
    transactions: List<FinanceTransaction>,
    periodTransactions: List<FinanceTransaction>,
    accounts: List<FinanceAccount>,
    income: Long,
    expenses: Long,
    netFlow: Long,
    categories: Map<String, Long>,
    showSpending: Boolean,
    showRecent: Boolean,
    periodLabel: String,
    previousPeriodExpense: Long,
    selectedPeriod: String,
    onPeriodChange: (String) -> Unit,
    onCustomRange: () -> Unit
) {
    val bankAccounts = accounts.filter { it.type != "CREDIT_CARD" }
    val creditCards = accounts.filter { it.type == "CREDIT_CARD" }
    fun balanceOf(account: FinanceAccount): Long = account.openingBalancePaise + transactions.filter { it.accountId == account.id }.sumOf {
        when (it.type) { "INCOME", "TRANSFER_IN" -> it.amountPaise; "EXPENSE", "TRANSFER_OUT" -> -it.amountPaise; else -> 0L }
    }
    val cashAndBank = bankAccounts.sumOf { balanceOf(it) }
    val cardUsed = creditCards.sumOf { (-balanceOf(it)).coerceAtLeast(0L) }
    val spendingChange = if (previousPeriodExpense > 0L) ((expenses - previousPeriodExpense).toDouble() / previousPeriodExpense.toDouble() * 100.0).toInt() else 0
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedPeriod == "THIS_MONTH", onClick = { onPeriodChange("THIS_MONTH") }, label = { Text("This month") })
                FilterChip(selected = selectedPeriod == "LAST_MONTH", onClick = { onPeriodChange("LAST_MONTH") }, label = { Text("Last month") })
                FilterChip(selected = selectedPeriod == "CUSTOM", onClick = onCustomRange, label = { Text("Custom") })
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Navy)) {
                Column(Modifier.padding(22.dp)) {
                    Text("NET CASH FLOW · ${periodLabel.uppercase(Locale.getDefault())}", color = Color(0xFFD1E9DD), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(money(netFlow), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryMetric("Income", income, Color(0xFFBDE8D2), Modifier.weight(1f))
                        SummaryMetric("Spent", expenses, Color(0xFFFFC9BD), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("TOTAL NET WORTH", color = Color(0xFFBBD0F4), style = MaterialTheme.typography.labelSmall)
                    Text(money(FinanceAnalytics.netWorth(accounts, transactions)), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("BANK + CASH", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(money(cashAndBank), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Card(Modifier.weight(1f), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("CARD USED", color = Color(0xFF53699D), style = MaterialTheme.typography.labelSmall)
                        Text(money(cardUsed), color = Navy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5F1))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MONTHLY INSIGHT", color = Forest, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(if (previousPeriodExpense > 0L) {
                        if (spendingChange <= 0) "You're spending ${kotlin.math.abs(spendingChange)}% less than the previous period." else "Spending is up ${spendingChange}% compared with the previous period."
                    } else "A comparison will appear when the previous period has expenses.", color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val recurringItems = periodTransactions.filter { it.isRecurring }.take(3)
        if (recurringItems.isNotEmpty()) {
            item { Text("Recurring transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(recurringItems, key = { "recurring-${it.id}" }) { TransactionRow(it) }
        }
        if (showSpending) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spending breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(periodLabel, color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (showSpending && categories.isEmpty()) item { EmptyCard("Your spending chart will appear here", "Add expenses to see where your money goes.") }
        else if (showSpending) {
            item { SpendingChart(categories) }
            items(categories.entries.toList()) { (category, amount) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Forest))
                    Text(category, Modifier.weight(1f).padding(start = 10.dp), color = Ink)
                    Text(money(amount), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (showRecent) item { Text("Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (showRecent) items(periodTransactions.take(4), key = { it.id }) { TransactionRow(it) }
        if (showRecent && periodTransactions.isEmpty()) item { EmptyCard("No transactions in this period", "Choose another date range or add a transaction.") }
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
                if (transaction.isRecurring) Text("↻ ${transaction.recurrenceFrequency.lowercase().replaceFirstChar { it.uppercase() }} recurring", color = Forest, style = MaterialTheme.typography.labelSmall)
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
                    val currentBalance = account.openingBalancePaise + transactions.filter { it.accountId == account.id }.sumOf { if (it.type == "INCOME") it.amountPaise else -it.amountPaise }
                    Text(money(currentBalance), fontWeight = FontWeight.Bold)
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
private fun AddTransactionDialog(
    accounts: List<FinanceAccount>,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, Long?, String, String, Boolean, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("Food") }
    var method by remember { mutableStateOf("UPI") }
    var accountId by remember { mutableStateOf(accounts.firstOrNull { it.type == "BANK" }?.id ?: accounts.firstOrNull()?.id) }
    var tags by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var methodExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var moreOptions by remember { mutableStateOf(false) }
    val categories = listOf("Food", "Travel", "Bills", "Shopping", "Health", "Salary", "Business", "Other")
    val methods = listOf("UPI", "CASH", "CARD", "BANK_TRANSFER", "WALLET")
    val eligibleAccounts = when (method) {
        "CASH" -> accounts.filter { it.type == "CASH" }
        "CARD" -> accounts.filter { it.type == "CREDIT_CARD" }
        "BANK_TRANSFER" -> accounts.filter { it.type == "BANK" }
        "WALLET" -> accounts.filter { it.type == "WALLET" }
        else -> accounts.filter { it.type == "BANK" || it.type == "WALLET" }
    }
    val selectedAccountId = if (eligibleAccounts.any { it.id == accountId }) accountId else eligibleAccounts.firstOrNull()?.id
    val validAmount = amount.toBigDecimalOrNull()?.let { it.signum() > 0 && it.toDouble().isFinite() } == true
    fun methodLabel(value: String) = when (value) {
        "BANK_TRANSFER" -> "Bank transfer"
        "CARD" -> "Card"
        else -> value.lowercase().replaceFirstChar { it.uppercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "EXPENSE", { type = "EXPENSE" }, label = { Text("Expense") })
                    FilterChip(type == "INCOME", { type = "INCOME" }, label = { Text("Income") })
                }
                ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true,
                        label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                        categories.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { category = item; categoryExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(methodExpanded, { methodExpanded = it }) {
                    OutlinedTextField(
                        value = methodLabel(method), onValueChange = {}, readOnly = true,
                        label = { Text("Payment method") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(methodExpanded, { methodExpanded = false }) {
                        methods.forEach { item ->
                            DropdownMenuItem(text = { Text(methodLabel(item)) }, onClick = {
                                method = item
                                val preferred = when (item) {
                                    "CASH" -> accounts.firstOrNull { it.type == "CASH" }
                                    "CARD" -> accounts.firstOrNull { it.type == "CREDIT_CARD" }
                                    "BANK_TRANSFER" -> accounts.firstOrNull { it.type == "BANK" }
                                    "WALLET" -> accounts.firstOrNull { it.type == "WALLET" }
                                    else -> accounts.firstOrNull { it.type == "BANK" } ?: accounts.firstOrNull { it.type == "WALLET" }
                                }
                                accountId = preferred?.id
                                methodExpanded = false
                            })
                        }
                    }
                }
                if (eligibleAccounts.isNotEmpty()) {
                    ExposedDropdownMenuBox(accountExpanded, { accountExpanded = it }) {
                        OutlinedTextField(
                            value = eligibleAccounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Select account",
                            onValueChange = {}, readOnly = true, label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(accountExpanded, { accountExpanded = false }) {
                            eligibleAccounts.forEach { account ->
                                DropdownMenuItem(text = { Text(account.name) }, onClick = { accountId = account.id; accountExpanded = false })
                            }
                        }
                    }
                } else {
                    Text(
                        "No matching account is available for this payment method. Add a matching account on the Accounts tab, or save this transaction without an account.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { moreOptions = !moreOptions }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (moreOptions) "Hide more options" else "More options")
                }
                if (moreOptions) {
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recurring, onCheckedChange = { recurring = it })
                        Text("Track as recurring")
                    }
                    if (recurring) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("WEEKLY", "MONTHLY", "YEARLY").forEach { item ->
                            FilterChip(selected = frequency == item, onClick = { frequency = item }, label = { Text(item.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && validAmount, onClick = {
                onSave(title.trim(), amount.toDouble(), type, category, selectedAccountId, method, tags, recurring, frequency)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun money(paise: Long): String = "₹" + String.format(Locale("en", "IN"), "%,.2f", paise / 100.0)
private fun dateLabel(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
