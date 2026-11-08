package com.skyboys.personalfinance.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Creates a readable, full-dataset Google Sheets backup after explicit OAuth consent.
 * Access tokens are supplied by the caller and are never persisted by this adapter.
 */
class GoogleSheetsBackupClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS).build()
) {
    data class BackupResult(val spreadsheetId: String, val spreadsheetUrl: String)

    fun createBackup(
        accessToken: String,
        data: FinanceBackupData,
        title: String = "Personal Finance Backup"
    ): BackupResult {
        require(accessToken.isNotBlank()) { "Google authorization is required" }
        val media = "application/json; charset=utf-8".toMediaType()
        val tabNames = listOf("Transactions", "Accounts", "Budgets", "BillReminders", "Metadata")
        val createBody = JSONObject()
            .put("properties", JSONObject().put("title", title))
            .put("sheets", JSONArray().apply {
                tabNames.forEach { tab ->
                    put(JSONObject().put("properties", JSONObject().put("title", tab)))
                }
            })
        val createRequest = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets")
            .header("Authorization", "Bearer $accessToken")
            .post(createBody.toString().toRequestBody(media)).build()
        val created = http.newCall(createRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "Google Sheets create failed (${response.code}): $body" }
            JSONObject(body)
        }
        val id = created.getString("spreadsheetId")

        fun rows(headers: List<Any?>, records: List<List<Any?>>): JSONArray =
            JSONArray().apply {
                put(JSONArray(headers))
                records.forEach { put(JSONArray(it)) }
            }

        val transactionRows = data.transactions.sortedBy { it.dateMillis }.map { t ->
            listOf(t.id, t.title, t.amountPaise, t.type, t.category, t.dateMillis,
                t.accountId ?: "", t.paymentMethod, t.tags, t.notes, t.transferId ?: "",
                t.isRecurring, t.recurrenceFrequency)
        }
        val accountRows = data.accounts.sortedBy { it.id }.map { a ->
            listOf(a.id, a.name, a.institution, a.type, a.openingBalancePaise, a.currency,
                a.isIncludedInNetWorth, a.creditLimitPaise, a.statementDueDay, a.balanceAsOfMillis)
        }
        val budgetRows = data.budgets.sortedBy { it.id }.map { b ->
            listOf(b.id, b.category, b.limitPaise, b.monthKey, b.alertPercent)
        }
        val reminderRows = data.reminders.sortedBy { it.id }.map { r ->
            listOf(r.id, r.title, r.amountPaise, r.dueDay, r.category, r.accountId ?: "", r.enabled)
        }
        val metadataRows = listOf(
            listOf("format", "personal-finance-sheets-backup"),
            listOf("version", "1"),
            listOf("exportedAtMillis", System.currentTimeMillis().toString()),
            listOf("transactionCount", data.transactions.size.toString()),
            listOf("accountCount", data.accounts.size.toString()),
            listOf("budgetCount", data.budgets.size.toString()),
            listOf("reminderCount", data.reminders.size.toString())
        )
        val ranges = JSONArray()
            .put(JSONObject().put("range", "Transactions!A1")
                .put("majorDimension", "ROWS")
                .put("values", rows(
                    listOf("id", "title", "amountPaise", "type", "category", "dateMillis", "accountId",
                        "paymentMethod", "tags", "notes", "transferId", "isRecurring", "recurrenceFrequency"),
                    transactionRows
                )))
            .put(JSONObject().put("range", "Accounts!A1")
                .put("majorDimension", "ROWS")
                .put("values", rows(
                    listOf("id", "name", "institution", "type", "openingBalancePaise", "currency",
                        "isIncludedInNetWorth", "creditLimitPaise", "statementDueDay", "balanceAsOfMillis"),
                    accountRows
                )))
            .put(JSONObject().put("range", "Budgets!A1")
                .put("majorDimension", "ROWS")
                .put("values", rows(listOf("id", "category", "limitPaise", "monthKey", "alertPercent"), budgetRows)))
            .put(JSONObject().put("range", "BillReminders!A1")
                .put("majorDimension", "ROWS")
                .put("values", rows(listOf("id", "title", "amountPaise", "dueDay", "category", "accountId", "enabled"), reminderRows)))
            .put(JSONObject().put("range", "Metadata!A1")
                .put("majorDimension", "ROWS")
                .put("values", JSONArray().apply { metadataRows.forEach { put(JSONArray(it)) } }))

        val payload = JSONObject().put("valueInputOption", "RAW").put("data", ranges)
        val updateRequest = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$id/values:batchUpdate")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(media)).build()
        http.newCall(updateRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "Google Sheets upload failed (${response.code}): $body" }
        }
        return BackupResult(id, "https://docs.google.com/spreadsheets/d/$id/edit")
    }
}
