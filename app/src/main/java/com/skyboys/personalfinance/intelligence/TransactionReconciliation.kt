package com.skyboys.personalfinance.intelligence

import com.skyboys.personalfinance.data.FinanceTransaction
import kotlin.math.abs

/** Matches statement rows against local transactions. Results are suggestions, never silent deletions. */
object TransactionReconciliation {
    data class Match(
        val statementTransaction: FinanceTransaction,
        val localTransaction: FinanceTransaction,
        val confidence: Double,
        val reasons: List<String>
    )
    data class Result(
        val matches: List<Match>,
        val statementOnly: List<FinanceTransaction>,
        val localOnly: List<FinanceTransaction>
    )

    fun reconcile(
        local: List<FinanceTransaction>,
        statement: List<FinanceTransaction>,
        dateWindowMillis: Long = 3L * 24 * 60 * 60 * 1000
    ): Result {
        require(dateWindowMillis > 0)
        val usedLocalIds = mutableSetOf<Long>()
        val matches = mutableListOf<Match>()
        val unmatchedStatement = mutableListOf<FinanceTransaction>()
        statement.sortedBy { it.dateMillis }.forEach { row ->
            val candidates = local.asSequence()
                .filter { it.id !in usedLocalIds }
                .mapNotNull { existing -> score(row, existing, dateWindowMillis)?.let { existing to it } }
                .sortedByDescending { it.second.first }
                .toList()
            val best = candidates.firstOrNull()
            if (best != null && best.second.first >= AUTO_MATCH_THRESHOLD) {
                usedLocalIds += best.first.id
                matches += Match(row, best.first, best.second.first, best.second.second)
            } else {
                unmatchedStatement += row
            }
        }
        return Result(matches, unmatchedStatement, local.filter { it.id !in usedLocalIds })
    }

    private fun score(
        statement: FinanceTransaction,
        local: FinanceTransaction,
        dateWindowMillis: Long
    ): Pair<Double, List<String>>? {
        if (statement.amountPaise != local.amountPaise || statement.type != local.type) return null
        if (statement.accountId != null && local.accountId != null && statement.accountId != local.accountId) return null
        val delta = abs(statement.dateMillis - local.dateMillis)
        if (delta > dateWindowMillis) return null
        val a = SmartTransactionEngine.normalizeMerchant(statement.title)
        val b = SmartTransactionEngine.normalizeMerchant(local.title)
        if (a.isBlank() || b.isBlank()) return null
        val similarity = SmartTransactionEngine.tokenSimilarity(a, b)
        if (similarity < MIN_MERCHANT_SIMILARITY) return null
        val dateScore = 1.0 - (delta.toDouble() / dateWindowMillis)
        val confidence = (0.55 + similarity * 0.30 + dateScore * 0.15).coerceIn(0.0, 1.0)
        val reasons = listOf(
            "Same amount",
            "Same transaction type",
            if (delta <= 60_000L) "Same minute" else "Nearby transaction date",
            "Merchant similarity " + (similarity * 100).toInt() + "%"
        )
        return confidence to reasons
    }

    const val AUTO_MATCH_THRESHOLD = 0.90
    const val MIN_MERCHANT_SIMILARITY = 0.5
}
