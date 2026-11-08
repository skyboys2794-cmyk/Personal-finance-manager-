package com.skyboys.personalfinance.reports

import com.skyboys.personalfinance.data.FinanceAccount
import com.skyboys.personalfinance.data.FinanceTransaction
import java.util.Calendar

data class FinanceSummary(
    val incomePaise: Long,
    val expensePaise: Long,
    val netCashFlowPaise: Long,
    val expenseByCategory: Map<String, Long>,
    val monthLabel: String
)

data class MonthlyReport(val monthLabel: String, val incomePaise: Long, val expensePaise: Long) {
    val netPaise: Long get() = incomePaise - expensePaise
}

object FinanceAnalytics {
    fun summarize(items: List<FinanceTransaction>, month: Calendar = Calendar.getInstance()): FinanceSummary {
        val start = (month.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = (month.clone() as Calendar).apply {
            add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return summarizeRange(items, start, end, java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(month.time))
    }

    fun summarizeRange(items: List<FinanceTransaction>, startInclusive: Long, endExclusive: Long, label: String): FinanceSummary {
        require(endExclusive >= startInclusive) { "End date must not be before start date" }
        val current = items.filter { it.dateMillis >= startInclusive && it.dateMillis < endExclusive }
        val income = current.filter { it.type == "INCOME" }.sumOf { it.amountPaise }
        val expenses = current.filter { it.type == "EXPENSE" }
        val total = expenses.sumOf { it.amountPaise }
        return FinanceSummary(
            income, total, income - total,
            expenses.groupBy { it.category }.mapValues { (_, tx) -> tx.sumOf { it.amountPaise } }
                .toList().sortedByDescending { it.second }.toMap(),
            label
        )
    }

    fun monthlyTrend(items: List<FinanceTransaction>, count: Int = 6, now: Calendar = Calendar.getInstance()): List<MonthlyReport> {
        require(count in 1..24)
        return (count - 1 downTo 0).map { offset ->
            val month = (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MONTH, -offset)
            }
            val start = month.timeInMillis
            val end = (month.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
            val rows = items.filter { it.dateMillis >= start && it.dateMillis < end }
            MonthlyReport(
                java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(month.time),
                rows.filter { it.type == "INCOME" }.sumOf { it.amountPaise },
                rows.filter { it.type == "EXPENSE" }.sumOf { it.amountPaise }
            )
        }
    }

    fun netWorth(accounts: List<FinanceAccount>, transactions: List<FinanceTransaction>): Long {
        return accounts.filter { it.isIncludedInNetWorth }.sumOf { account ->
            account.openingBalancePaise + transactions.filter { it.accountId == account.id && it.dateMillis > account.balanceAsOfMillis }
                .sumOf { when (it.type) {
                    "INCOME", "TRANSFER_IN" -> it.amountPaise
                    "EXPENSE", "TRANSFER_OUT" -> -it.amountPaise
                    else -> 0L
                } }
        }
    }
}
