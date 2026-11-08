package com.skyboys.personalfinance.categorization

import com.skyboys.personalfinance.data.FinanceDao
import com.skyboys.personalfinance.data.MerchantCategoryRule
import java.util.Locale

/**
 * Stores only user-confirmed merchant/category corrections in the local Room database.
 * It never calls a remote service.
 */
object MerchantMemory {
    fun normalizeMerchant(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("""\\b(?:upi|txn|transaction|ref|reference|order|id|no)\\s*[:#/-]?\\s*[a-z0-9-]{4,}\\b"""), " ")
        .replace(Regex("""\\b\\d+\\b"""), " ")
        .replace(Regex("""[^a-z\\s]"""), " ")
        .replace(Regex("""\\s+"""), " ")
        .trim()
        .take(80)

    suspend fun rememberCorrection(dao: FinanceDao, merchant: String, category: String) {
        val normalized = normalizeMerchant(merchant)
        if (normalized.length < 3 || category.isBlank() || category == "Other" || category == "Uncategorized") return
        val existing = dao.merchantRule(normalized)
        dao.saveMerchantRule(
            MerchantCategoryRule(
                normalizedMerchant = normalized,
                displayName = merchant.trim().take(100),
                category = category,
                correctionCount = (existing?.correctionCount ?: 0) + 1,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun learnedCategory(dao: FinanceDao, merchant: String): String? {
        val normalized = normalizeMerchant(merchant)
        if (normalized.length < 3) return null
        return dao.merchantRule(normalized)?.category
    }
}
