package com.skyboys.personalfinance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index("transferId")])
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val type: String,
    val category: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val accountId: Long? = null,
    val paymentMethod: String = "CASH",
    val tags: String = "",
    val notes: String = "",
    val transferId: String? = null
)
