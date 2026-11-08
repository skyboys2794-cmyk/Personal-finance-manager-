@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.skyboys.personalfinance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyboys.personalfinance.data.FinanceBudget
import com.skyboys.personalfinance.data.BillReminder
import com.skyboys.personalfinance.data.FinanceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(dao: FinanceDao, modifier: Modifier = Modifier) {
    val budgets by dao.observeBudgets().collectAsStateWithLifecycle(emptyList())
    val reminders by dao.observeReminders().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var dueDay by remember { mutableStateOf("5") }
    var alertPercent by remember { mutableStateOf("80") }
    val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val categories = listOf("Food", "Travel", "Bills", "Shopping", "Health", "Salary", "Business", "Other")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Budgets & bills", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { mode = "budget"; title = ""; amount = "" }, modifier = Modifier.weight(1f)) { Text("Set budget") }
                OutlinedButton(onClick = { mode = "bill"; title = ""; amount = "" }, modifier = Modifier.weight(1f)) { Text("Add bill") }
            }
        }
        item { Text("Monthly budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (budgets.isEmpty()) item { Text("No budgets configured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(budgets, key = { "budget-${it.id}" }) { budget ->
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row {
                        Text(budget.category, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("₹${"%.2f".format(Locale.US, budget.limitPaise / 100.0)}", fontWeight = FontWeight.Bold)
                    }
                    Text("${budget.alertPercent}% alert threshold · ${budget.monthKey}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Bill reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (reminders.isEmpty()) item { Text("No bill reminders configured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(reminders, key = { "bill-${it.id}" }) { bill ->
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(bill.title, fontWeight = FontWeight.SemiBold)
                        Text("Due every month on day ${bill.dueDay}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text("₹${"%.2f".format(Locale.US, bill.amountPaise / 100.0)}", fontWeight = FontWeight.Bold)
                        Switch(
                            checked = bill.enabled,
                            onCheckedChange = { enabled ->
                                scope.launch(Dispatchers.IO) { dao.saveReminder(bill.copy(enabled = enabled)) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (mode.isNotBlank()) {
        val validAmount = amount.toDoubleOrNull()?.let { it > 0 && it.isFinite() } == true
        AlertDialog(
            onDismissRequest = { mode = "" },
            title = { Text(if (mode == "budget") "Set monthly budget" else "Add bill reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(if (mode == "budget") "Category / budget name" else "Bill name") }, singleLine = true)
                    if (mode == "budget") {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded, { expanded = it }) {
                            OutlinedTextField(category, {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded, { expanded = false }) {
                                categories.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { category = item; title = item; expanded = false }) }
                            }
                        }
                        OutlinedTextField(alertPercent, { alertPercent = it.filter(Char::isDigit).take(3) }, label = { Text("Alert at percent (default 80)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    if (mode == "bill") OutlinedTextField(dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, label = { Text("Due day (1–31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = validAmount && title.isNotBlank() && (mode != "bill" || dueDay.toIntOrNull()?.let { it in 1..31 } == true), onClick = {
                    val paise = (amount.toDouble() * 100).toLong()
                    scope.launch(Dispatchers.IO) {
                        if (mode == "budget") {
                            val selectedCategory = title.ifBlank { category }
                            dao.saveBudget(FinanceBudget(category = selectedCategory, limitPaise = paise, monthKey = monthKey, alertPercent = (alertPercent.toIntOrNull() ?: 80).coerceIn(1, 100)))
                        } else {
                            dao.saveReminder(BillReminder(title = title, amountPaise = paise, dueDay = dueDay.toInt(), category = "Bills"))
                        }
                    }
                    mode = ""
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { mode = "" }) { Text("Cancel") } }
        )
    }
}
