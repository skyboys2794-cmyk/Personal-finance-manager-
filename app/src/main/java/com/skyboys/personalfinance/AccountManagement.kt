package com.skyboys.personalfinance

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyboys.personalfinance.data.FinanceAccount
import com.skyboys.personalfinance.data.FinanceDao
import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.reports.FinanceAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private val AccountTeal = Color(0xFF087F73)
private val AccountBlue = Color(0xFF3158C9)
private val AccountPale = Color(0xFFEAF4F2)

@Composable
fun AccountManagementScreen(modifier: Modifier, accounts: List<FinanceAccount>, dao: FinanceDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<FinanceAccount?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FinanceAccount?>(null) }
    var transferring by remember { mutableStateOf(false) }
    val netWorth = remember(accounts, transactions) { FinanceAnalytics.netWorth(accounts, transactions) }
    fun balanceOf(account: FinanceAccount): Long = account.openingBalancePaise + transactions.filter { it.accountId == account.id }.sumOf {
        when (it.type) { "INCOME", "TRANSFER_IN" -> it.amountPaise; "EXPENSE", "TRANSFER_OUT" -> -it.amountPaise; else -> 0L }
    }
    val banks = accounts.filter { it.type != "CREDIT_CARD" }
    val cards = accounts.filter { it.type == "CREDIT_CARD" }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (accounts.isEmpty()) "Set up your finances" else "Your accounts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (accounts.isEmpty()) "Add each bank account, wallet and credit card with its current balance and limits. This information stays on your device." else "Banks, cash and credit cards in one place", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { adding = true }, shape = RoundedCornerShape(16.dp)) { Text("Add account") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF172D55))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TOTAL NET WORTH", color = Color(0xFFBBD0F4), style = MaterialTheme.typography.labelMedium)
                    Text(money(netWorth), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("${banks.size} bank / cash", color = Color(0xFFE0E9FA), style = MaterialTheme.typography.labelMedium)
                        Text("${cards.size} credit cards", color = Color(0xFFE0E9FA), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { transferring = true }, enabled = accounts.size >= 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.ArrowOutward, null); Spacer(Modifier.width(8.dp)); Text("Transfer between accounts")
            }
        }
        item { AccountSectionHeading("Bank accounts & wallets", banks.size) }
        if (banks.isEmpty()) item { AccountEmptyState("No bank or cash accounts yet") }
        items(banks, key = { "bank-${it.id}" }) { account ->
            val balance = balanceOf(account)
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = AccountPale) {
                            Icon(Icons.Default.AccountBalance, null, tint = AccountTeal, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(account.name, fontWeight = FontWeight.SemiBold)
                            Text("${account.institution.ifBlank { account.type.replace('_',' ') }} · ${account.currency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(balance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    AccountActions(onEdit = { editing = account }, onDelete = { deleting = account })
                }
            }
        }
        item { AccountSectionHeading("Credit cards", cards.size) }
        if (cards.isEmpty()) item { AccountEmptyState("Add a credit card to track usage and statement due dates") }
        items(cards, key = { "card-${it.id}" }) { account ->
            val balance = balanceOf(account)
            val used = (-balance).coerceAtLeast(0L)
            val limit = account.creditLimitPaise.coerceAtLeast(0L)
            val available = (limit - used).coerceAtLeast(0L)
            val progress = if (limit > 0L) (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF182F67))) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, null, tint = Color(0xFFB9CCFF))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(account.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(account.institution.ifBlank { "Credit card" }, color = Color(0xFFB9CCFF), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("CREDIT", color = Color(0xFFB9CCFF), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(money(used), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Outstanding amount", color = Color(0xFFB9CCFF), style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Color(0xFF75E0BD), trackColor = Color(0xFF40568C))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("AVAILABLE", color = Color(0xFFB9CCFF), style = MaterialTheme.typography.labelSmall)
                            Text(money(available), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("CREDIT LIMIT", color = Color(0xFFB9CCFF), style = MaterialTheme.typography.labelSmall)
                            Text(money(limit), color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = .18f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (account.statementDueDay in 1..31) "Statement due: day ${account.statementDueDay} monthly" else "Statement due date not set", color = Color(0xFFE0E9FA), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { editing = account }) { Text("Edit", color = Color.White) }
                        TextButton(onClick = { deleting = account }) { Text("Delete", color = Color(0xFFFFC6BE)) }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        AccountEditorDialog(editing, onDismiss = { adding = false; editing = null }) { name, institution, type, balance, include, limit, dueDay ->
            scope.launch(Dispatchers.IO) {
                try {
                    if (editing == null) dao.insertAccount(FinanceAccount(name=name, institution=institution, type=type, openingBalancePaise=balance, isIncludedInNetWorth=include, creditLimitPaise=limit, statementDueDay=dueDay))
                    else editing!!.let { dao.updateAccount(it.id, name, institution, type, balance, it.currency, include, limit, dueDay) }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) { Toast.makeText(context, "Could not save account. Check for a duplicate name.", Toast.LENGTH_LONG).show() }
                }
            }
            adding = false; editing = null
        }
    }
    deleting?.let { account ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${account.name}?") },
            text = { Text("Transactions and reminders will be kept, but detached from this account. This action cannot be undone.") },
            confirmButton = { TextButton(onClick = { scope.launch(Dispatchers.IO) { dao.deleteAccountSafely(account.id) }; deleting = null }) { Text("Delete account") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    if (transferring) TransferDialog(accounts, onDismiss = { transferring = false }) { from, to, amount, note ->
        scope.launch(Dispatchers.IO) {
            val transferId = UUID.randomUUID().toString()
            val time = System.currentTimeMillis()
            dao.insertTransferPair(
                FinanceTransaction(title=note.ifBlank { "Transfer to ${accounts.first { it.id == to }.name}" }, amountPaise=amount, type="TRANSFER_OUT", category="Transfers", dateMillis=time, accountId=from, paymentMethod="BANK_TRANSFER", transferId=transferId),
                FinanceTransaction(title=note.ifBlank { "Transfer from ${accounts.first { it.id == from }.name}" }, amountPaise=amount, type="TRANSFER_IN", category="Transfers", dateMillis=time, accountId=to, paymentMethod="BANK_TRANSFER", transferId=transferId)
            )
        }
        transferring = false
    }
}

@Composable
private fun AccountSectionHeading(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AccountEmptyState(message: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = AccountPale, modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(16.dp), color = AccountTeal, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccountActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Text("Edit") }
        TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("Delete") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorDialog(account: FinanceAccount?, onDismiss: () -> Unit, onSave: (String, String, String, Long, Boolean, Long, Int) -> Unit) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var institution by remember(account?.id) { mutableStateOf(account?.institution.orEmpty()) }
    var type by remember(account?.id) { mutableStateOf(account?.type ?: "BANK") }
    var balance by remember(account?.id) { mutableStateOf((kotlin.math.abs(account?.openingBalancePaise ?: 0L) / 100.0).toString()) }
    var include by remember(account?.id) { mutableStateOf(account?.isIncludedInNetWorth ?: true) }
    var limit by remember(account?.id) { mutableStateOf(((account?.creditLimitPaise ?: 0L) / 100.0).toString()) }
    var dueDay by remember(account?.id) { mutableStateOf(if (account == null || account.statementDueDay == 0) "" else account.statementDueDay.toString()) }
    var typeExpanded by remember { mutableStateOf(false) }
    val parsed = balance.toBigDecimalOrNull()
    val parsedLimit = limit.toBigDecimalOrNull()
    AlertDialog(onDismissRequest=onDismiss, title={ Text(if (account == null) "Add account" else "Edit account") },
        text={ Column(
            Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(name,{name=it},label={Text("Account name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(institution,{institution=it},label={Text("Bank / institution")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            ExposedDropdownMenuBox(typeExpanded,{typeExpanded=it}) {
                OutlinedTextField(
                    value=type.replace('_',' '), onValueChange={}, readOnly=true,
                    label={Text("Account type")},
                    trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded)},
                    modifier=Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(typeExpanded,{typeExpanded=false}) {
                    listOf("BANK","CASH","CREDIT_CARD","WALLET").forEach { item ->
                        DropdownMenuItem(text={Text(item.replace('_',' '))},onClick={type=item;typeExpanded=false})
                    }
                }
            }
            OutlinedTextField(balance,{balance=it},label={Text(if(type=="CREDIT_CARD") "Current outstanding amount (₹)" else "Current balance (₹)")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            if (type == "CREDIT_CARD") {
                OutlinedTextField(limit,{limit=it},label={Text("Credit limit (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
                OutlinedTextField(dueDay,{dueDay=it.filter(Char::isDigit).take(2)},label={Text("Statement due day (1–31)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(include,{include=it}); Text("Include in net worth") }
        } },
        confirmButton={ TextButton(enabled=name.isNotBlank() && parsed != null && (type!="CREDIT_CARD" || (parsedLimit != null && parsedLimit.signum()>=0 && (dueDay.isBlank() || dueDay.toIntOrNull()?.let { it in 1..31 }==true))), onClick={
            val enteredPaise = parsed!!.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            val paise = if (type == "CREDIT_CARD") -kotlin.math.abs(enteredPaise) else enteredPaise
            val limitPaise = if (type=="CREDIT_CARD") (parsedLimit ?: java.math.BigDecimal.ZERO).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() else 0L
            onSave(name.trim(),institution.trim(),type,paise,include,limitPaise,dueDay.toIntOrNull() ?: 0)
        }) { Text("Save") } },
        dismissButton={ TextButton(onClick=onDismiss){Text("Cancel")} })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferDialog(accounts: List<FinanceAccount>, onDismiss: () -> Unit, onTransfer: (Long,Long,Long,String) -> Unit) {
    var from by remember { mutableStateOf(accounts.first().id) }
    var to by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.first().id) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }
    val parsed = amount.toBigDecimalOrNull()
    val valid = parsed != null && parsed.signum() > 0 && from != to
    AlertDialog(onDismissRequest=onDismiss,title={Text("Transfer between accounts")},
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(fromExpanded,{fromExpanded=it}) {
                OutlinedTextField(accounts.firstOrNull{it.id==from}?.name.orEmpty(),{},readOnly=true,label={Text("From")},modifier=Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(fromExpanded,{fromExpanded=false}) { accounts.forEach { DropdownMenuItem(text={Text(it.name)},onClick={from=it.id;fromExpanded=false}) } }
            }
            ExposedDropdownMenuBox(toExpanded,{toExpanded=it}) {
                OutlinedTextField(accounts.firstOrNull{it.id==to}?.name.orEmpty(),{},readOnly=true,label={Text("To")},modifier=Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(toExpanded,{toExpanded=false}) { accounts.filter{it.id!=from}.forEach { DropdownMenuItem(text={Text(it.name)},onClick={to=it.id;toExpanded=false}) } }
            }
            OutlinedTextField(amount,{amount=it},label={Text("Amount (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            OutlinedTextField(note,{note=it},label={Text("Note (optional)")},singleLine=true)
        } },
        confirmButton={ TextButton(enabled=valid,onClick={
            val paise=parsed!!.movePointRight(2).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact()
            onTransfer(from,to,paise,note)
        }){Text("Transfer")} },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

private fun money(paise: Long): String = "₹" + String.format(java.util.Locale("en", "IN"), "%,.2f", paise / 100.0)
