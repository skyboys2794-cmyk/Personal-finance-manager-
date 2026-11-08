package com.skyboys.personalfinance

import androidx.compose.foundation.layout.*
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
    onReports: () -> Unit,
    onImportCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSheets: () -> Unit,
    onCustomize: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF18243A))
            Text("Tools to manage, understand and protect your money", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7A8798))
        }
        SettingsActionCard("Reports & insights", "Monthly trends, categories and cash flow", onReports, Icons.Default.Assessment)
        SettingsActionCard("Import transactions", "Bring transactions in from a CSV file", onImportCsv, Icons.Default.FileUpload)
        SettingsActionCard("Encrypted backup", "Export your finance data with a password", onBackup, Icons.Default.Backup)
        SettingsActionCard("Restore backup", "Restore a previously saved encrypted backup", onRestore, Icons.Default.Restore)
        SettingsActionCard("Google Sheets", "Create a secure snapshot in your Google account", onSheets, Icons.Default.CloudUpload)
        SettingsActionCard("Personalize dashboard", "Choose which overview sections are visible", onCustomize, Icons.Default.Palette)
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE8F5F1)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your data stays on this device", fontWeight = FontWeight.SemiBold, color = Color(0xFF087F73))
                Text("Backups are encrypted before export. Google Sheets sync is a manual snapshot, not automatic bank syncing.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF536B69))
            }
        }
        Text("Finance Assistant · Version 1.1", color = Color(0xFF7A8798), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE8F5F1)) {
                Icon(icon, null, tint = Color(0xFF087F73), modifier = Modifier.padding(12.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF18243A))
                Text(subtitle, color = Color(0xFF7A8798), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
