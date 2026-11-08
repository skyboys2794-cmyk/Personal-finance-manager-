package com.skyboys.personalfinance.intelligence

import com.skyboys.personalfinance.data.FinanceTransaction
import java.util.Calendar

/** Local-only financial insights. All monetary values are stored in paise. */
object FinanceInsights {
    data class BudgetInsight(
        val spentPaise: Long,
        val limitPaise: Long,
        val usedPercent: Double,
        val remainingPaise: Long,
        val isOverBudget: Boolean
    )

    data class SpendingProjection(
        val spentSoFarPaise: Long,
        val projectedMonthEndPaise: Long,
        val elapsedDays: Int,
        val totalDays: Int
    )

    fun savingsRatePercent(incomePaise: Long, expensePaise: Long): Double? {
        if (incomePaise <= 0L) return null
        return ((incomePaise - expensePaise).toDouble() / incomePaise.toDouble() * 100.0)
            .coerceIn(-100.0, 100.0)
    }

    fun budgetInsight(spentPaise: Long, limitPaise: Long): BudgetInsight {
        require(limitPaise >= 0L)
        val percent = if (limitPaise == 0L) {
            if (spentPaise > 0L) 100.0 else 0.0
        } else spentPaise.toDouble() / limitPaise.toDouble() * 100.0
        return BudgetInsight(
            spentPaise = spentPaise,
            limitPaise = limitPaise,
            usedPercent = percent,
            remainingPaise = limitPaise - spentPaise,
            isOverBudget = spentPaise > limitPaise
        )
    }

    fun projectMonthEndExpense(
        transactions: List<FinanceTransaction>,
        now: Calendar = Calendar.getInstance()
    ): SpendingProjection {
        val start = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val next = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val spent = transactions.asSequence()
            .filter { it.type == "EXPENSE" && it.dateMillis >= start.timeInMillis && it.dateMillis < next.timeInMillis }
            .sumOf { it.amountPaise }
        val elapsed = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val days = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val projection = (spent.toDouble() * days.toDouble() / elapsed.toDouble())
            .coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
        return SpendingProjection(spent, projection, elapsed, days)
    }

    fun expenseChangePercent(previousExpensePaise: Long, currentExpensePaise: Long): Double? {
        if (previousExpensePaise <= 0L) return null
        return (currentExpensePaise - previousExpensePaise).toDouble() /
            previousExpensePaise.toDouble() * 100.0
    }
}
