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

    @Test fun balanceSnapshotsIgnoreTransactionsOnOrBeforeSnapshotDate() {
        val snapshot = 1_000_000L
        val account = FinanceAccount(id = 7, name = "Bank", openingBalancePaise = 500_000, balanceAsOfMillis = snapshot)
        val transactions = listOf(
            FinanceTransaction(title = "Old salary", amountPaise = 100_000, type = "INCOME", category = "Salary", dateMillis = snapshot - 1, accountId = 7),
            FinanceTransaction(title = "New expense", amountPaise = 25_000, type = "EXPENSE", category = "Food", dateMillis = snapshot + 1, accountId = 7)
        )
        assertEquals(475_000L, FinanceAnalytics.netWorth(listOf(account), transactions))
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
    @Test fun customDateRangeIncludesStartAndExcludesEnd() {
        val start = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val transactions = listOf(
            FinanceTransaction(title="Start day", amountPaise=1000, type="INCOME", category="Salary", dateMillis=start),
            FinanceTransaction(title="Inside", amountPaise=2500, type="EXPENSE", category="Food", dateMillis=start + 86_400_000L),
            FinanceTransaction(title="End boundary", amountPaise=9000, type="EXPENSE", category="Food", dateMillis=end)
        )
        val summary = FinanceAnalytics.summarizeRange(transactions, start, end, "October 2026")
        assertEquals(1000L, summary.incomePaise)
        assertEquals(2500L, summary.expensePaise)
        assertEquals(2500L, summary.expenseByCategory["Food"])
        assertEquals("October 2026", summary.monthLabel)
    }

}
