package com.skyboys.personalfinance.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankNotificationParserTest {
    @Test fun parsesPhonePeDebitAndCategory() {
        val parsed = BankNotificationParser.parse("PhonePe: You paid ₹250.00 to Swiggy successfully")
        assertEquals(25000L, parsed?.amountPaise)
        assertEquals("EXPENSE", parsed?.type)
        assertEquals("Food", parsed?.category)
    }
    @Test fun parsesBankCredit() {
        val parsed = BankNotificationParser.parse("INR 1,500.00 credited to your account")
        assertEquals(150000L, parsed?.amountPaise)
        assertEquals("INCOME", parsed?.type)
    }
    @Test fun skipsFailedPayments() {
        assertNull(BankNotificationParser.parse("Payment failed for Rs 300.00"))
    }
    @Test fun unknownMerchantIsOther() {
        assertEquals("Other", MerchantCategorizer.categoryFor("UPI payment to XYZ"))
    }
}
