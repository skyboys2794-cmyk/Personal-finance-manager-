package com.skyboys.personalfinance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FinanceSettingsScreen(
    modifier: Modifier = Modifier,
    onReports: () -> Unit,
    onImportCsv: () -> Unit,
    onImportPdf: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSheets: () -> Unit,
    onCustomize: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF18243A))
                Text("Manage, understand and protect your money", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7A8798))
            }
        }
        item { SettingsSectionLabel("REPORTS") }
        item { SettingsActionCard("Reports & insights", "Monthly trends, categories and cash flow", onReports, Icons.Default.Assessment) }
        item { SettingsSectionLabel("DATA MANAGEMENT") }
        item { SettingsActionCard("Import PDF bank statement", "Read password-protected PDF statements on this device", onImportPdf, Icons.Default.FileUpload) }
        item { SettingsActionCard("Import CSV statement", "Import transactions from a CSV statement", onImportCsv, Icons.Default.FileUpload) }
        item { SettingsActionCard("Create encrypted backup", "Export accounts, transactions, budgets and reminders", onBackup, Icons.Default.Backup) }
        item { SettingsActionCard("Restore backup", "Restore finance data from an encrypted backup", onRestore, Icons.Default.Restore) }
        item { SettingsSectionLabel("CONNECTIONS") }
        item { SettingsActionCard("Google Sheets snapshot", "Manually export transaction rows to Google Sheets", onSheets, Icons.Default.CloudUpload) }
        item { SettingsSectionLabel("PERSONALIZATION") }
        item { SettingsActionCard("Customize dashboard", "Choose which overview sections are visible", onCustomize, Icons.Default.Palette) }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE8F5F1), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your data stays on this device", fontWeight = FontWeight.SemiBold, color = Color(0xFF087F73))
                    Text("Backups are encrypted before export. Google Sheets creates a manual transaction snapshot; it does not sync bank accounts automatically.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF536B69))
                }
            }
        }
        item { Text("Finance Assistant · Version 1.1", color = Color(0xFF7A8798), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(title, color = Color(0xFF7A8798), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE8F5F1)) {
                Icon(icon, null, tint = Color(0xFF087F73), modifier = Modifier.padding(11.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF18243A))
                Text(subtitle, color = Color(0xFF7A8798), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
