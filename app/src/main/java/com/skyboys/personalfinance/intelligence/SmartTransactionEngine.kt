package com.skyboys.personalfinance.intelligence

import com.skyboys.personalfinance.data.FinanceTransaction
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic, on-device transaction intelligence.
 * No network, remote model, or external financial-data service is used.
 */
object SmartTransactionEngine {
    data class CategoryDecision(
        val category: String,
        val confidence: Double,
        val source: String,
        val needsReview: Boolean
    )

    fun normalizeMerchant(raw: String): String {
        val ascii = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
        return ascii.lowercase(Locale.ROOT)
            .replace(Regex("""\\b(?:upi|payment|paid|sent|received|transfer|transaction|txn|purchase|debit|credit|ref|reference|order|id|no)\\b"""), " ")
            .replace(Regex("""\\b[0-9a-f]{8,}\\b"""), " ")
            .replace(Regex("""\\b\\d+\\b"""), " ")
            .replace(Regex("""[^a-z\\s]"""), " ")
            .replace(Regex("""\\s+"""), " ")
            .trim()
            .take(100)
    }

    fun decideCategory(
        merchant: String,
        learnedCategory: String?,
        keywordRules: List<Pair<Regex, String>>
    ): CategoryDecision {
        if (!learnedCategory.isNullOrBlank()) {
            return CategoryDecision(learnedCategory, 0.98, "LOCAL_LEARNING", false)
        }
        val normalized = normalizeMerchant(merchant)
        val matched = keywordRules.firstOrNull { it.first.containsMatchIn(normalized) }
        return if (matched != null) {
            CategoryDecision(matched.second, 0.82, "BUILT_IN_RULE", false)
        } else {
            CategoryDecision("Other", 0.25, "UNCLASSIFIED", true)
        }
    }

    /**
     * Exact amount/type/account and near-time plus merchant similarity.
     * A different merchant name is not enough to suppress a legitimate purchase.
     */
    fun isLikelyDuplicate(
        candidate: FinanceTransaction,
        existing: FinanceTransaction,
        windowMillis: Long = 5 * 60 * 1000L
    ): Boolean {
        if (candidate.amountPaise != existing.amountPaise) return false
        if (canonicalType(candidate.type) != canonicalType(existing.type)) return false
        if (candidate.accountId != null && existing.accountId != null &&
            candidate.accountId != existing.accountId) return false
        if (abs(candidate.dateMillis - existing.dateMillis) > windowMillis) return false
        val a = normalizeMerchant(candidate.title)
        val b = normalizeMerchant(existing.title)
        return a.isNotBlank() && b.isNotBlank() && (a == b || tokenSimilarity(a, b) >= 0.8)
    }

    fun canonicalType(type: String): String = when (type.uppercase(Locale.ROOT)) {
        "DEBIT", "EXPENSE", "TRANSFER_OUT" -> type.uppercase(Locale.ROOT)
        "CREDIT", "INCOME", "TRANSFER_IN" -> type.uppercase(Locale.ROOT)
        else -> type.uppercase(Locale.ROOT)
    }

    internal fun tokenSimilarity(a: String, b: String): Double {
        val left = a.split(" ").filter(String::isNotBlank).toSet()
        val right = b.split(" ").filter(String::isNotBlank).toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.union(right).size.toDouble()
    }
}
