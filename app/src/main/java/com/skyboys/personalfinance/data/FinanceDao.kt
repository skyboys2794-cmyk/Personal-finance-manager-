package com.skyboys.personalfinance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
    @Query("UPDATE transactions SET title=:title, amountPaise=:amountPaise, type=:type, category=:category, accountId=:accountId, paymentMethod=:paymentMethod, tags=:tags, notes=:notes WHERE id=:id")
    suspend fun updateTransaction(id: Long, title: String, amountPaise: Long, type: String, category: String, accountId: Long?, paymentMethod: String, tags: String, notes: String)
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAccounts(): Flow<List<FinanceAccount>>
    @Query("SELECT * FROM accounts ORDER BY name")
    suspend fun allAccounts(): List<FinanceAccount>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: FinanceAccount): Long
    @Query("UPDATE accounts SET name=:name, institution=:institution, type=:type, openingBalancePaise=:openingBalancePaise, currency=:currency, isIncludedInNetWorth=:isIncludedInNetWorth WHERE id=:id")
    suspend fun updateAccount(id: Long, name: String, institution: String, type: String, openingBalancePaise: Long, currency: String, isIncludedInNetWorth: Boolean)
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId=:accountId")
    suspend fun countTransactionsForAccount(accountId: Long): Int
    @Query("UPDATE transactions SET accountId=NULL WHERE accountId=:accountId")
    suspend fun detachTransactionsFromAccount(accountId: Long)
    @Query("UPDATE bill_reminders SET accountId=NULL WHERE accountId=:accountId")
    suspend fun detachRemindersFromAccount(accountId: Long)
    @Query("SELECT DISTINCT transferId FROM transactions WHERE accountId=:accountId AND transferId IS NOT NULL")
    suspend fun transferIdsForAccount(accountId: Long): List<String>
    @Query("DELETE FROM transactions WHERE transferId IN (:transferIds)")
    suspend fun deleteTransfers(transferIds: List<String>)
    @Query("DELETE FROM accounts WHERE id=:id")
    suspend fun deleteAccount(id: Long)
    @Query("SELECT * FROM budgets ORDER BY category")
    fun observeBudgets(): Flow<List<FinanceBudget>>
    @Query("SELECT * FROM budgets ORDER BY category")
    suspend fun allBudgets(): List<FinanceBudget>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: FinanceBudget)
    @Query("SELECT * FROM bill_reminders ORDER BY dueDay")
    fun observeReminders(): Flow<List<BillReminder>>
    @Query("SELECT * FROM bill_reminders ORDER BY dueDay")
    suspend fun allReminders(): List<BillReminder>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReminder(reminder: BillReminder)
    @Query("SELECT * FROM transactions WHERE dateMillis >= :start AND dateMillis < :end")
    suspend fun transactionsBetween(start: Long, end: Long): List<FinanceTransaction>

    @Transaction
    suspend fun replaceAllData(transactions: List<FinanceTransaction>, accounts: List<FinanceAccount>, budgets: List<FinanceBudget>, reminders: List<BillReminder>) {
        clearTransactions()
        clearAccounts()
        clearBudgets()
        clearReminders()
        if (accounts.isNotEmpty()) insertAccountsForRestore(accounts)
        if (transactions.isNotEmpty()) insertTransactionsForRestore(transactions)
        if (budgets.isNotEmpty()) insertBudgetsForRestore(budgets)
        if (reminders.isNotEmpty()) insertRemindersForRestore(reminders)
    }

    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM bill_reminders") suspend fun clearReminders()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccountsForRestore(items: List<FinanceAccount>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactionsForRestore(items: List<FinanceTransaction>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgetsForRestore(items: List<FinanceBudget>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRemindersForRestore(items: List<BillReminder>)
}
