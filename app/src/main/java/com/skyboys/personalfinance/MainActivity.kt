package com.skyboys.personalfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.skyboys.personalfinance.data.FinanceDatabase
import com.skyboys.personalfinance.data.FinanceTransaction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = FinanceDatabase.get(this).financeDao()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF176B55))) {
                FinanceScreen(
                    dao = dao,
                    onAdd = { title, amount, type, category ->
                        lifecycleScope.launch {
                            dao.insert(FinanceTransaction(
                                title = title,
                                amountPaise = (amount * 100).toLong(),
                                type = type,
                                category = category
                            ))
                        }
                    },
                    onDelete = { id -> lifecycleScope.launch { dao.delete(id) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceScreen(
    dao: com.skyboys.personalfinance.data.FinanceDao,
    onAdd: (String, Double, String, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val transactions by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    val income = transactions.filter { it.type == "INCOME" }.sumOf { it.amountPaise }
    val expenses = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountPaise }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Personal Finance", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("TOTAL BALANCE", style = MaterialTheme.typography.labelLarge)
                        Text(money(income - expenses), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Income", style = MaterialTheme.typography.labelMedium)
                                Text(money(income), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Expenses", style = MaterialTheme.typography.labelMedium)
                                Text(money(expenses), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item {
                Text("Recent transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (transactions.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No transactions yet", fontWeight = FontWeight.SemiBold)
                            Text("Tap + to record your first income or expense.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(transactions, key = { it.id }) { transaction ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(transaction.title, fontWeight = FontWeight.SemiBold)
                                Text("${transaction.category} • ${dateLabel(transaction.dateMillis)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                (if (transaction.type == "INCOME") "+" else "−") + money(transaction.amountPaise),
                                color = if (transaction.type == "INCOME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { onDelete(transaction.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete transaction")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            onDismiss = { showAdd = false },
            onSave = { title, amount, type, category ->
                onAdd(title, amount, type, category)
                showAdd = false
            }
        )
    }
}

@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("Other") }
    val categories = listOf("Food", "Travel", "Bills", "Shopping", "Health", "Salary", "Business", "Other")
    val validAmount = amount.toDoubleOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Expense") })
                    FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Income") })
                }
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Column {
                    categories.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { item ->
                                FilterChip(
                                    selected = category == item,
                                    onClick = { category = item },
                                    label = { Text(item, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && validAmount,
                onClick = { onSave(title.trim(), amount.toDouble(), type, category) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun money(paise: Long): String =
    "₹" + String.format(Locale("en", "IN"), "%,.2f", paise / 100.0)

private fun dateLabel(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
