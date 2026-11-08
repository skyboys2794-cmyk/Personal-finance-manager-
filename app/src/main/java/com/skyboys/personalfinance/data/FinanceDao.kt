package com.skyboys.personalfinance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    fun observeAll(): Flow<List<FinanceTransaction>>
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    suspend fun allTransactions(): List<FinanceTransaction>
    @Insert
    suspend fun insert(transaction: FinanceTransaction): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<FinanceTransaction>)
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAccounts(): Flow<List<FinanceAccount>>
    @Query("SELECT * FROM accounts ORDER BY name")
    suspend fun allAccounts(): List<FinanceAccount>
    @Insert
    suspend fun insertAccount(account: FinanceAccount): Long
    @Query("SELECT * FROM budgets ORDER BY category")
    fun observeBudgets(): Flow<List<FinanceBudget>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: FinanceBudget)
    @Query("SELECT * FROM bill_reminders ORDER BY dueDay")
    fun observeReminders(): Flow<List<BillReminder>>
    @Insert
    suspend fun saveReminder(reminder: BillReminder)
    @Query("SELECT * FROM transactions WHERE dateMillis >= :start AND dateMillis < :end")
    suspend fun transactionsBetween(start: Long, end: Long): List<FinanceTransaction>
}
