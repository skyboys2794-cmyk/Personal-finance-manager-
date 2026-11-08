package com.skyboys.personalfinance.backup

import com.skyboys.personalfinance.data.FinanceTransaction
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Google Sheets REST adapter. The caller must obtain a short-lived OAuth access token
 * from Google's user-consent flow with spreadsheets scope and pass it only in memory.
 * Never embed OAuth client secrets or persist access tokens in the repository.
 */
class GoogleSheetsBackupClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS).build()
) {
    data class BackupResult(val spreadsheetId: String, val spreadsheetUrl: String)

    fun createBackup(accessToken: String, transactions: List<FinanceTransaction>, title: String = "Personal Finance Backup"): BackupResult {
        require(accessToken.isNotBlank()) { "Google authorization is required" }
        val media = "application/json; charset=utf-8".toMediaType()
        val createBody = JSONObject().put("properties", JSONObject().put("title", title))
            .put("sheets", JSONArray().put(JSONObject().put("properties", JSONObject().put("title", "Transactions"))))
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
        val values = JSONArray()
        values.put(JSONArray(listOf("Date", "Description", "Type", "Amount INR", "Category", "Payment method", "Tags", "Notes", "Account ID")))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        transactions.sortedBy { it.dateMillis }.forEach { t ->
            values.put(JSONArray(listOf(
                dateFormat.format(Date(t.dateMillis)), t.title, t.type,
                "%.2f".format(Locale.US, t.amountPaise / 100.0), t.category,
                t.paymentMethod, t.tags, t.notes, t.accountId?.toString().orEmpty()
            )))
        }
        val payload = JSONObject().put("majorDimension", "ROWS").put("values", values)
        val updateRequest = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$id/values/Transactions!A1?valueInputOption=RAW")
            .header("Authorization", "Bearer $accessToken")
            .put(payload.toString().toRequestBody(media)).build()
        http.newCall(updateRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "Google Sheets upload failed (${response.code}): $body" }
        }
        return BackupResult(id, "https://docs.google.com/spreadsheets/d/$id/edit")
    }
}
