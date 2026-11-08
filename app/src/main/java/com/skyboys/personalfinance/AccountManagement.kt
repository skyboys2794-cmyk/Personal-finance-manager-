package com.skyboys.personalfinance

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your accounts", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { adding = true }) { Text("Add account") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE7F3ED))) {
                Column(Modifier.padding(16.dp)) {
                    Text("ESTIMATED NET WORTH", style = MaterialTheme.typography.labelMedium)
                    Text("₹" + String.format(java.util.Locale("en", "IN"), "%,.2f", netWorth / 100.0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("Transfers are recorded as matched debit and credit entries, not as income or spending.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { transferring = true }, enabled = accounts.size >= 2, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ArrowOutward, null); Spacer(Modifier.width(8.dp)); Text("Transfer between accounts")
            }
        }
        items(accounts, key = { it.id }) { account ->
            val balance = account.openingBalancePaise + transactions.filter { it.accountId == account.id }.sumOf {
                when (it.type) { "INCOME", "TRANSFER_IN" -> it.amountPaise; "EXPENSE", "TRANSFER_OUT" -> -it.amountPaise; else -> 0L }
            }
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(account.name, fontWeight = FontWeight.SemiBold)
                            Text("${account.institution.ifBlank { account.type }} · ${account.currency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("₹" + String.format(java.util.Locale("en", "IN"), "%,.2f", balance / 100.0), fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { editing = account }) { Icon(Icons.Default.Edit, null); Text("Edit") }
                        TextButton(onClick = { deleting = account }) { Icon(Icons.Default.Delete, null); Text("Delete") }
                    }
                }
            }
        }
        if (accounts.isEmpty()) item { Text("Add your first bank, cash, card or wallet account.") }
    }

    if (adding || editing != null) {
        AccountEditorDialog(editing, onDismiss = { adding = false; editing = null }) { name, institution, type, balance, include ->
            scope.launch(Dispatchers.IO) {
                try {
                    if (editing == null) dao.insertAccount(FinanceAccount(name=name, institution=institution, type=type, openingBalancePaise=balance, isIncludedInNetWorth=include))
                    else editing!!.let { dao.updateAccount(it.id, name, institution, type, balance, it.currency, include) }
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
            confirmButton = { TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    dao.deleteAccountSafely(account.id)
                }
                deleting = null
            }) { Text("Delete account") } },
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
private fun AccountEditorDialog(account: FinanceAccount?, onDismiss: () -> Unit, onSave: (String, String, String, Long, Boolean) -> Unit) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var institution by remember(account?.id) { mutableStateOf(account?.institution.orEmpty()) }
    var type by remember(account?.id) { mutableStateOf(account?.type ?: "BANK") }
    var balance by remember(account?.id) { mutableStateOf(((account?.openingBalancePaise ?: 0L) / 100.0).toString()) }
    var include by remember(account?.id) { mutableStateOf(account?.isIncludedInNetWorth ?: true) }
    val parsed = balance.toBigDecimalOrNull()
    AlertDialog(onDismissRequest=onDismiss, title={ Text(if (account == null) "Add account" else "Edit account") },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Account name")},singleLine=true)
            OutlinedTextField(institution,{institution=it},label={Text("Bank / institution")},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf("BANK","CASH","CREDIT_CARD","WALLET").forEach { FilterChip(type==it,{type=it},label={Text(it)}) } }
            OutlinedTextField(balance,{balance=it},label={Text("Opening balance (₹; debt can be negative)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(include,{include=it}); Text("Include in net worth") }
        } },
        confirmButton={ TextButton(enabled=name.isNotBlank() && parsed != null, onClick={
            val paise = parsed!!.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            onSave(name.trim(),institution.trim(),type,paise,include)
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
