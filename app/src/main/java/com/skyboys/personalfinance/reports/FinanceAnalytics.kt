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

object FinanceAnalytics {
    fun summarize(items: List<FinanceTransaction>, month: Calendar = Calendar.getInstance()): FinanceSummary {
        val start = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val end = (month.clone() as Calendar).apply { add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val current = items.filter { it.dateMillis >= start && it.dateMillis < end }
        val income = current.filter { it.type == "INCOME" }.sumOf { it.amountPaise }
        val expenses = current.filter { it.type == "EXPENSE" }
        val total = expenses.sumOf { it.amountPaise }
        return FinanceSummary(income, total, income - total,
            expenses.groupBy { it.category }.mapValues { (_, tx) -> tx.sumOf { it.amountPaise } }.toList().sortedByDescending { it.second }.toMap(),
            java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(month.time))
    }

    fun netWorth(accounts: List<FinanceAccount>, transactions: List<FinanceTransaction>): Long {
        return accounts.filter { it.isIncludedInNetWorth }.sumOf { account ->
            account.openingBalancePaise + transactions.filter { it.accountId == account.id }
                .sumOf { if (it.type == "INCOME") it.amountPaise else -it.amountPaise }
        }
    }
}
