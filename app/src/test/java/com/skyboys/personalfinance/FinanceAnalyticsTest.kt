package com.skyboys.personalfinance

import com.skyboys.personalfinance.data.FinanceAccount
import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.reports.FinanceAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceAnalyticsTest {
    @Test fun pairedTransfersDoNotChangeCombinedNetWorth() {
        val accounts = listOf(
            FinanceAccount(id=1, name="Bank A", openingBalancePaise=100_000),
            FinanceAccount(id=2, name="Bank B", openingBalancePaise=25_000)
        )
        val transferId = "transfer-test"
        val transactions = listOf(
            FinanceTransaction(title="Transfer out", amountPaise=20_000, type="TRANSFER_OUT", category="Transfers", accountId=1, transferId=transferId),
            FinanceTransaction(title="Transfer in", amountPaise=20_000, type="TRANSFER_IN", category="Transfers", accountId=2, transferId=transferId)
        )
        assertEquals(125_000L, FinanceAnalytics.netWorth(accounts, transactions))
    }

    @Test fun monthlyReportsExcludeTransfersFromIncomeAndExpenses() {
        val now = java.util.Calendar.getInstance()
        val transactions = listOf(
            FinanceTransaction(title="Salary", amountPaise=500_000, type="INCOME", category="Salary", dateMillis=now.timeInMillis),
            FinanceTransaction(title="Food", amountPaise=25_000, type="EXPENSE", category="Food", dateMillis=now.timeInMillis),
            FinanceTransaction(title="Move money", amountPaise=10_000, type="TRANSFER_OUT", category="Transfers", dateMillis=now.timeInMillis)
        )
        val summary = FinanceAnalytics.summarize(transactions, now)
        assertEquals(500_000L, summary.incomePaise)
        assertEquals(25_000L, summary.expensePaise)
        assertEquals(475_000L, summary.netCashFlowPaise)
    }
}
