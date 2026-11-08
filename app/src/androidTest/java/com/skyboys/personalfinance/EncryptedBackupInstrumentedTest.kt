package com.skyboys.personalfinance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skyboys.personalfinance.backup.EncryptedBackup
import com.skyboys.personalfinance.backup.FinanceBackupData
import com.skyboys.personalfinance.data.BillReminder
import com.skyboys.personalfinance.data.FinanceAccount
import com.skyboys.personalfinance.data.FinanceBudget
import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.data.MerchantCategoryRule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupInstrumentedTest {
    @Test fun encryptedBackupRoundTripsAllFinanceEntities() {
        val source = FinanceBackupData(
            transactions=listOf(FinanceTransaction(title="Groceries", amountPaise=12500, type="EXPENSE", category="Food")),
            accounts=listOf(FinanceAccount(id=12, name="Primary bank", openingBalancePaise=750000, balanceAsOfMillis=1_799_000_000_000L)),
            budgets=listOf(FinanceBudget(id=4, category="Food", limitPaise=50000, monthKey="2026-11")),
            reminders=listOf(BillReminder(id=7, title="Electricity", amountPaise=200000, dueDay=12)),
            merchantRules=listOf(MerchantCategoryRule("swiggy", "Swiggy", "Food", 3, 1_800_000_000_000L))
        )
        val bytes = EncryptedBackup.export(source, "StrongBackupPass123".toCharArray())
        val restored = EncryptedBackup.restore(bytes, "StrongBackupPass123".toCharArray())
        assertEquals(1, restored.transactions.size)
        assertEquals("Groceries", restored.transactions.first().title)
        assertEquals(12L, restored.accounts.first().id)
        assertEquals(750000L, restored.accounts.first().openingBalancePaise)
        assertEquals(1_799_000_000_000L, restored.accounts.first().balanceAsOfMillis)
        assertEquals(50000L, restored.budgets.first().limitPaise)
        assertEquals("Electricity", restored.reminders.first().title)
        assertEquals("Food", restored.merchantRules.first().category)
        assertEquals(3, restored.merchantRules.first().correctionCount)
    }

    @Test(expected = Exception::class)
    fun wrongPasswordCannotDecryptBackup() {
        val bytes = EncryptedBackup.export(FinanceBackupData(emptyList(), emptyList(), emptyList(), emptyList()), "StrongBackupPass123".toCharArray())
        EncryptedBackup.restore(bytes, "WrongBackupPass123".toCharArray())
    }
}
