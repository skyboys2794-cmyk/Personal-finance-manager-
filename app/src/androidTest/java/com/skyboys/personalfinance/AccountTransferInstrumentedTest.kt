package com.skyboys.personalfinance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skyboys.personalfinance.data.BillReminder
import com.skyboys.personalfinance.data.FinanceAccount
import com.skyboys.personalfinance.data.FinanceDatabase
import com.skyboys.personalfinance.data.FinanceTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountTransferInstrumentedTest {
    @Test fun accountCanBeEditedDeletedAndTransfersRemainPaired() = runBlocking {
        val dao = FinanceDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext).financeDao()
        val suffix = UUID.randomUUID().toString().take(8)
        val fromId = dao.insertAccount(FinanceAccount(name="Test source $suffix", creditLimitPaise=0, statementDueDay=0, balanceAsOfMillis=0))
        val toId = dao.insertAccount(FinanceAccount(name="Test destination $suffix", creditLimitPaise=0, statementDueDay=0, balanceAsOfMillis=0))
        val transferId = UUID.randomUUID().toString()
        dao.insertTransferPair(
            FinanceTransaction(title="Transfer out", amountPaise=1500, type="TRANSFER_OUT", category="Transfers", accountId=fromId, transferId=transferId),
            FinanceTransaction(title="Transfer in", amountPaise=1500, type="TRANSFER_IN", category="Transfers", accountId=toId, transferId=transferId)
        )
        dao.saveReminder(BillReminder(title="Test bill $suffix", amountPaise=1000, dueDay=10, accountId=fromId))
        dao.updateAccount(fromId, "Edited source $suffix", "Test bank", "BANK", 5000, "INR", true, 0L, 0, 0L)
        assertEquals("Edited source $suffix", dao.allAccounts().first { it.id == fromId }.name)
        assertEquals(2, dao.allTransactions().count { it.transferId == transferId })

        assertEquals(listOf(transferId), dao.transferIdsForAccount(fromId))
        dao.deleteAccountSafely(fromId)
        assertFalse(dao.allAccounts().any { it.id == fromId })
        assertFalse(dao.allTransactions().any { it.transferId == transferId })
        assertNull(dao.allReminders().first { it.title == "Test bill $suffix" }.accountId)
        dao.deleteAccount(toId)
    }
}
