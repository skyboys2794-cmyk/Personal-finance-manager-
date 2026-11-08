package com.skyboys.personalfinance.importer

import android.content.Context
import android.net.Uri
import com.skyboys.personalfinance.data.FinanceTransaction
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.SimpleDateFormat
import java.util.Locale

class PdfPasswordRequiredException : Exception("This PDF is password-protected")

data class PdfStatementEntry(
    val transaction: FinanceTransaction,
    val needsDirectionReview: Boolean
)

data class PdfStatementParseResult(
    val entries: List<PdfStatementEntry>,
    val skippedLines: Int
)

/**
 * Offline PDF statement reader. It extracts text locally and flags rows whose
 * debit/credit direction cannot be inferred safely for user review.
 */
object PdfStatementParser {
    private val dateRegex = Regex("""\b(?:\d{2}[/-]\d{2}[/-]\d{4}|\d{4}-\d{2}-\d{2}|\d{2}[- ]?[A-Za-z]{3}[- ]?\d{4})\b""")
    private val amountRegex = Regex("""(?<![A-Za-z0-9])(?:INR\s*|₹\s*)?(-?\d[\d,]*\.\d{1,2})(?![A-Za-z0-9])""")
    private val dateFormats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yyyy", "dd-MMM-yyyy")

    fun read(context: Context, uri: Uri, password: String = ""): PdfStatementParseResult {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, password).use { document ->
                    PDFTextStripper().getText(document)
                }
            } ?: error("Could not open the selected PDF")
        } catch (_: InvalidPasswordException) {
            throw PdfPasswordRequiredException()
        }
        return parseText(text)
    }

    internal fun parseText(text: String): PdfStatementParseResult {
        val entries = mutableListOf<PdfStatementEntry>()
        var skipped = 0
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.replace(Regex("""\s+"""), " ").trim()
            if (line.isBlank()) return@forEach
            val dateMatch = dateRegex.find(line) ?: return@forEach
            val dateText = dateMatch.value
            val date = dateFormats.firstNotNullOfOrNull { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                        .parse(dateText)?.time
                }.getOrNull()
            }
            if (date == null) { skipped++; return@forEach }

            val amountMatches = amountRegex.findAll(line).toList()
            if (amountMatches.isEmpty()) { skipped++; return@forEach }
            val values = amountMatches.mapNotNull { it.groupValues[1].replace(",", "").toBigDecimalOrNull() }
            if (values.isEmpty()) { skipped++; return@forEach }

            val description = line.substring(0, amountMatches.first().range.first)
                .replace(dateText, "")
                .trim(' ', '-', '|', ':', '/', '\\')
            if (description.isBlank()) { skipped++; return@forEach }

            // Common statements expose debit, credit and running balance columns.
            // With two values, the first is treated as transaction amount and the
            // second as balance. Direction is confirmed from markers or by user.
            var amount = values.first().abs()
            var inferredType: String? = null
            var needsReview = false
            if (values.size >= 3) {
                val debit = values[values.size - 3].abs()
                val credit = values[values.size - 2].abs()
                when {
                    debit.signum() > 0 && credit.signum() == 0 -> { amount = debit; inferredType = "EXPENSE" }
                    credit.signum() > 0 && debit.signum() == 0 -> { amount = credit; inferredType = "INCOME" }
                    debit.signum() > 0 && credit.signum() > 0 -> { amount = debit; needsReview = true }
                    else -> { amount = values[values.size - 2].abs(); needsReview = true }
                }
            }

            val directionText = line.uppercase(Locale.ROOT)
            val hasDebit = Regex("""\b(DR|DEBIT|WITHDRAWAL|WITHDRAWN|PURCHASE|PAID)\b""").containsMatchIn(directionText)
            val hasCredit = Regex("""\b(CR|CREDIT|DEPOSIT|RECEIVED|REFUND|SALARY|INTEREST)\b""").containsMatchIn(directionText)
            if (inferredType == null) {
                inferredType = when {
                    hasDebit && !hasCredit -> "EXPENSE"
                    hasCredit && !hasDebit -> "INCOME"
                    values.first().signum() < 0 -> "EXPENSE"
                    else -> { needsReview = true; "EXPENSE" }
                }
            }
            if (amount.signum() == 0) { skipped++; return@forEach }
            val paise = runCatching {
                amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            }.getOrNull()
            if (paise == null || paise <= 0L) { skipped++; return@forEach }

            entries += PdfStatementEntry(
                FinanceTransaction(
                    title = description.take(180),
                    amountPaise = paise,
                    type = inferredType,
                    category = "Uncategorized",
                    dateMillis = date,
                    paymentMethod = "BANK_TRANSFER",
                    tags = "STATEMENT_IMPORT"
                ),
                needsDirectionReview = needsReview
            )
        }
        return PdfStatementParseResult(entries, skipped)
    }
}
