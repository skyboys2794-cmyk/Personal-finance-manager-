package com.skyboys.personalfinance

import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.intelligence.FinanceInsights
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class FinanceInsightsTest {
    @Test fun savingsRateUsesIncomeAsDenominator() {
        assertEquals(25.0, FinanceInsights.savingsRatePercent(10000, 7500)!!, 0.001)
    }

    @Test fun budgetInsightIdentifiesOverspending() {
        val insight = FinanceInsights.budgetInsight(12500, 10000)
        assertTrue(insight.isOverBudget)
        assertEquals(125.0, insight.usedPercent, 0.001)
        assertEquals(-2500L, insight.remainingPaise)
    }

    @Test fun monthProjectionIgnoresIncomeAndOtherMonths() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = now.clone() as Calendar
        start.set(Calendar.DAY_OF_MONTH, 1); start.set(Calendar.HOUR_OF_DAY, 0)
        val rows = listOf(
            FinanceTransaction(title="Food", amountPaise=10000, type="EXPENSE", category="Food", dateMillis=now.timeInMillis),
            FinanceTransaction(title="Salary", amountPaise=50000, type="INCOME", category="Salary", dateMillis=now.timeInMillis),
            FinanceTransaction(title="Old", amountPaise=7000, type="EXPENSE", category="Food", dateMillis=start.timeInMillis - 1)
        )
        val projection = FinanceInsights.projectMonthEndExpense(rows, now)
        assertEquals(10000L, projection.spentSoFarPaise)
        assertEquals(31000L, projection.projectedMonthEndPaise)
    }
}
