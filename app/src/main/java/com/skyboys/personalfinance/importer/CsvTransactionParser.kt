package com.skyboys.personalfinance.importer

import com.skyboys.personalfinance.data.FinanceTransaction
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Header-based CSV import. Supported headers: date, description/payee, amount,
 * type, category, account, payment_method, tags. Amounts are decimal INR.
 * Quoted cells and escaped quotes follow RFC 4180.
 */
object CsvTransactionParser {
    data class Result(val transactions: List<FinanceTransaction>, val errors: List<String>, val accountNames: List<String?> = emptyList())

    fun parse(csv: String): Result {
        val rows = parseRows(csv)
        if (rows.isEmpty()) return Result(emptyList(), listOf("CSV file is empty"))
        val headers = rows.first().map { it.trim().lowercase(Locale.ROOT).replace(" ", "_") }
        val dateFmt = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy")
        fun index(vararg names: String) = headers.indexOfFirst { it in names }
        val amountCol = index("amount", "value", "transaction_amount")
        val titleCol = index("description", "payee", "title", "narration", "merchant")
        if (amountCol < 0 || titleCol < 0) return Result(emptyList(), listOf("Required columns: amount and description"))
        val dateCol = index("date", "transaction_date", "posted_date")
        val typeCol = index("type", "transaction_type", "debit_credit")
        val categoryCol = index("category")
        val accountCol = index("account", "account_name")
        val methodCol = index("payment_method", "method")
        val tagsCol = index("tags", "tag")
        val out = mutableListOf<FinanceTransaction>()
        val accountNames = mutableListOf<String?>()
        val errors = mutableListOf<String>()
        rows.drop(1).forEachIndexed { rowIndex, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed
            fun cell(i: Int) = if (i >= 0) row.getOrNull(i)?.trim().orEmpty() else ""
            val amountRaw = cell(amountCol).replace(",", "").replace("₹", "").trim()
            val amount = amountRaw.toBigDecimalOrNull()
            if (amount == null || amount.signum() == 0) {
                errors += "Row ${rowIndex + 2}: invalid amount"; return@forEachIndexed
            }
            val title = cell(titleCol)
            if (title.isBlank()) { errors += "Row ${rowIndex + 2}: description is blank"; return@forEachIndexed }
            val typeRaw = cell(typeCol).uppercase(Locale.ROOT)
            val type = when {
                typeRaw in listOf("INCOME", "CREDIT", "CR", "DEPOSIT") -> "INCOME"
                typeRaw in listOf("EXPENSE", "DEBIT", "DR", "WITHDRAWAL") -> "EXPENSE"
                amount.signum() > 0 -> "INCOME"
                else -> "EXPENSE"
            }
            val absolute = amount.abs().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            val dateText = cell(dateCol)
            val date = if (dateText.isBlank()) System.currentTimeMillis() else dateFmt.firstNotNullOfOrNull { pattern ->
                runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(dateText)?.time }.getOrNull()
            }
            if (date == null) { errors += "Row ${rowIndex + 2}: unsupported date '$dateText'"; return@forEachIndexed }
            out += FinanceTransaction(
                title = title, amountPaise = absolute, type = type,
                category = cell(categoryCol).ifBlank { "Uncategorized" },
                dateMillis = date,
                paymentMethod = cell(methodCol).ifBlank { "BANK_TRANSFER" },
                tags = cell(tagsCol)
            )
            accountNames += cell(accountCol).ifBlank { null }
        }
        return Result(out, errors, accountNames)
    }

    private fun parseRows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' && quoted && i + 1 < input.length && input[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                (c == ',' || c == '\n' || c == '\r') && !quoted -> {
                    row += cell.toString(); cell.clear()
                    if (c == ',' ) Unit else {
                        if (row.any { it.isNotBlank() }) rows += row
                        row = mutableListOf()
                        if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    }
                }
                else -> cell.append(c)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}
