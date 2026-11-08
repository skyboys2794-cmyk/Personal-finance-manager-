package com.skyboys.personalfinance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN accountId INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'CASH'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE transactions ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, institution TEXT NOT NULL, type TEXT NOT NULL, openingBalancePaise INTEGER NOT NULL, currency TEXT NOT NULL, isIncludedInNetWorth INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_name ON accounts(name)")
        db.execSQL("CREATE TABLE IF NOT EXISTS budgets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, limitPaise INTEGER NOT NULL, monthKey TEXT NOT NULL, alertPercent INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_category_monthKey ON budgets(category, monthKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bill_reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountPaise INTEGER NOT NULL, dueDay INTEGER NOT NULL, category TEXT NOT NULL, accountId INTEGER, enabled INTEGER NOT NULL)")
    }
}

@Database(entities = [FinanceTransaction::class, FinanceAccount::class, FinanceBudget::class, BillReminder::class], version = 2, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
    companion object {
        @Volatile private var INSTANCE: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "personal-finance.db")
                    .addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
