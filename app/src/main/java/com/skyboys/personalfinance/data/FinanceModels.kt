package com.skyboys.personalfinance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts", indices = [Index("name", unique = true)])
data class FinanceAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val institution: String = "",
    val type: String = "BANK",
    val openingBalancePaise: Long = 0,
    val currency: String = "INR",
    val isIncludedInNetWorth: Boolean = true,
    val creditLimitPaise: Long = 0,
    val statementDueDay: Int = 0,
    val balanceAsOfMillis: Long = 0
)

@Entity(tableName = "budgets", indices = [Index(value = ["category", "monthKey"], unique = true)])
data class FinanceBudget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val limitPaise: Long,
    val monthKey: String,
    val alertPercent: Int = 80
)

@Entity(tableName = "bill_reminders")
data class BillReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val dueDay: Int,
    val category: String = "Bills",
    val accountId: Long? = null,
    val enabled: Boolean = true
)


@Entity(tableName = "merchant_category_rules")
data class MerchantCategoryRule(
    @PrimaryKey val normalizedMerchant: String,
    val displayName: String,
    val category: String,
    val correctionCount: Int = 1,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
