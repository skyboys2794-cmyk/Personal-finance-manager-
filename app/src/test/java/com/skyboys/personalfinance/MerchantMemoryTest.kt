package com.skyboys.personalfinance

import com.skyboys.personalfinance.categorization.MerchantMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantMemoryTest {
    @Test
    fun normalizationRemovesTransactionIdsAndNumbers() {
        assertEquals(
            "swiggy food",
            MerchantMemory.normalizeMerchant("UPI payment to Swiggy Food Order #123456")
        )
    }

    @Test
    fun normalizationIsCaseInsensitiveAndStable() {
        assertEquals(
            MerchantMemory.normalizeMerchant("Netflix Subscription"),
            MerchantMemory.normalizeMerchant("NETFLIX subscription")
        )
    }
}
