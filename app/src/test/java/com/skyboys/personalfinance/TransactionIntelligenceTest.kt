package com.skyboys.personalfinance

import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.intelligence.SmartTransactionEngine
import com.skyboys.personalfinance.intelligence.TransactionReconciliation
import org.junit.Assert.*
import org.junit.Test

class TransactionIntelligenceTest {
    @Test fun learnedCategoryWinsOverBuiltInRules() {
        val decision = SmartTransactionEngine.decideCategory(
            "UPI payment to Swiggy", "Food",
            listOf(Regex("swiggy") to "Shopping")
        )
        assertEquals("Food", decision.category)
        assertEquals("LOCAL_LEARNING", decision.source)
        assertFalse(decision.needsReview)
    }

    @Test fun unknownMerchantIsFlaggedForReview() {
        val decision = SmartTransactionEngine.decideCategory("Unknown merchant", null, emptyList())
        assertEquals("Other", decision.category)
        assertTrue(decision.needsReview)
        assertTrue(decision.confidence < 0.5)
    }

    @Test fun duplicateRequiresSameMerchantAmountTypeAndNearbyTime() {
        val first = tx(id = 1, title = "Swiggy Food", amount = 12000, time = 1000000)
        val same = tx(id = 2, title = "UPI payment to Swiggy Food order 12345", amount = 12000, time = 1050000)
        val otherMerchant = tx(id = 3, title = "Metro Ticket", amount = 12000, time = 1050000)
        assertTrue(SmartTransactionEngine.isLikelyDuplicate(first, same))
        assertFalse(SmartTransactionEngine.isLikelyDuplicate(first, otherMerchant))
    }

    @Test fun reconciliationFindsHighConfidenceStatementMatch() {
        val local = tx(id = 10, title = "Swiggy Food", amount = 12000, time = 1_000_000, account = 4)
        val statement = tx(id = 0, title = "SWIGGY FOOD UPI", amount = 12000, time = 1_010_000, account = 4)
        val result = TransactionReconciliation.reconcile(listOf(local), listOf(statement))
        assertEquals(1, result.matches.size)
        assertTrue(result.matches.first().confidence >= TransactionReconciliation.AUTO_MATCH_THRESHOLD)
        assertTrue(result.statementOnly.isEmpty())
        assertTrue(result.localOnly.isEmpty())
    }

    @Test fun reconciliationDoesNotMatchDifferentAccountOrAmount() {
        val local = tx(id = 10, title = "Swiggy Food", amount = 12000, time = 1_000_000, account = 4)
        val statement = tx(id = 0, title = "Swiggy Food", amount = 12001, time = 1_010_000, account = 5)
        val result = TransactionReconciliation.reconcile(listOf(local), listOf(statement))
        assertTrue(result.matches.isEmpty())
        assertEquals(1, result.statementOnly.size)
        assertEquals(1, result.localOnly.size)
    }

    @Test fun oneLocalTransactionCannotMatchTwoStatementRows() {
        val local = tx(id = 10, title = "Swiggy Food", amount = 12000, time = 1_000_000)
        val rows = listOf(
            tx(id = 0, title = "Swiggy Food", amount = 12000, time = 1_000_000),
            tx(id = 0, title = "Swiggy Food UPI", amount = 12000, time = 1_005_000)
        )
        val result = TransactionReconciliation.reconcile(listOf(local), rows)
        assertEquals(1, result.matches.size)
        assertEquals(1, result.statementOnly.size)
    }

    private fun tx(
        id: Long, title: String, amount: Long, time: Long, account: Long? = null
    ) = FinanceTransaction(
        id = id, title = title, amountPaise = amount, type = "EXPENSE",
        category = "Other", dateMillis = time, accountId = account
    )
}
