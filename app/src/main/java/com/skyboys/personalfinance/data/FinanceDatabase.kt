package com.skyboys.personalfinance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FinanceTransaction::class], version = 1, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile private var INSTANCE: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "personal-finance.db"
                ).build().also { INSTANCE = it }
            }
    }
}
