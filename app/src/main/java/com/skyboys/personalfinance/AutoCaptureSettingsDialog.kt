package com.skyboys.personalfinance

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.skyboys.personalfinance.capture.FinanceNotificationCaptureService
import com.skyboys.personalfinance.data.FinanceDao

@Composable
fun AutoCaptureSettingsDialog(dao: FinanceDao, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(FinanceNotificationCaptureService.PREFS, 0) }
    val accounts by dao.observeAccounts().collectAsState(initial = emptyList())
    var enabled by remember { mutableStateOf(prefs.getBoolean(FinanceNotificationCaptureService.KEY_ENABLED, false)) }
    var phonePe by remember { mutableStateOf(prefs.getBoolean(FinanceNotificationCaptureService.KEY_PHONEPE, true)) }
    var gpay by remember { mutableStateOf(prefs.getBoolean(FinanceNotificationCaptureService.KEY_GPAY, true)) }
    var paytm by remember { mutableStateOf(prefs.getBoolean(FinanceNotificationCaptureService.KEY_PAYTM, true)) }
    var amazon by remember { mutableStateOf(prefs.getBoolean(FinanceNotificationCaptureService.KEY_AMAZON, false)) }
    var accountId by remember { mutableStateOf(prefs.getLong(FinanceNotificationCaptureService.KEY_ACCOUNT_ID, -1L)) }
    var showAccounts by remember { mutableStateOf(false) }
    val listenerEnabled = FinanceNotificationCaptureService.isListenerEnabled(context)

    fun persist() {
        prefs.edit()
            .putBoolean(FinanceNotificationCaptureService.KEY_ENABLED, enabled)
            .putBoolean(FinanceNotificationCaptureService.KEY_PHONEPE, phonePe)
            .putBoolean(FinanceNotificationCaptureService.KEY_GPAY, gpay)
            .putBoolean(FinanceNotificationCaptureService.KEY_PAYTM, paytm)
            .putBoolean(FinanceNotificationCaptureService.KEY_AMAZON, amazon)
            .putLong(FinanceNotificationCaptureService.KEY_ACCOUNT_ID, accountId)
            .apply()
    }

    AlertDialog(
        onDismissRequest = { persist(); onDismiss() },
        title = { Text("Automatic transaction capture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (listenerEnabled) "Android notification access is granted." else "Grant Android notification access to read selected payment notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable automatic capture")
                        Text("Processes new notifications only", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it; persist() })
                }
                Text("Choose payment apps", style = MaterialTheme.typography.titleSmall)
                SourceToggle("PhonePe", phonePe) { phonePe = it; persist() }
                SourceToggle("Google Pay", gpay) { gpay = it; persist() }
                SourceToggle("Paytm", paytm) { paytm = it; persist() }
                SourceToggle("Amazon", amazon) { amazon = it; persist() }
                Text("Save captured transactions to", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { showAccounts = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(accounts.firstOrNull { it.id == accountId }?.name ?: "Automatically choose a bank account")
                }
                Text("Clear amount and debit/credit alerts are saved locally. Unknown merchants use Other for review. Unclear notifications are skipped.", style = MaterialTheme.typography.bodySmall)
                if (!listenerEnabled) Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant notification access")
                }
                Text("No SMS access, bank passwords, OTP collection, accessibility service, or cloud upload is used.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { persist(); onDismiss() }) { Text("Done") } }
    )

    if (showAccounts) {
        AlertDialog(
            onDismissRequest = { showAccounts = false },
            title = { Text("Choose default account") },
            text = {
                Column {
                    TextButton(onClick = { accountId = -1L; persist(); showAccounts = false }) { Text("Automatically choose a bank account") }
                    accounts.forEach { account ->
                        TextButton(onClick = { accountId = account.id; persist(); showAccounts = false }) { Text("${account.name} (${account.type})") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccounts = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SourceToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
