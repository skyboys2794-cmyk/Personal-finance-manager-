package com.skyboys.personalfinance

import com.skyboys.personalfinance.importer.PdfStatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfStatementParserTest {
    @Test
    fun parsesDebitAndCreditRowsFromStatementText() {
        val parsed = PdfStatementParser.parseText(
            """
            01/11/2026 UPI/MERCHANT 500.00 0.00 4,500.00
            02/11/2026 SALARY 0.00 25,000.00 29,500.00
            """.trimIndent()
        )

        assertEquals(2, parsed.entries.size)
        assertEquals("EXPENSE", parsed.entries[0].transaction.type)
        assertEquals(50_000L, parsed.entries[0].transaction.amountPaise)
        assertFalse(parsed.entries[0].needsDirectionReview)
        assertEquals("INCOME", parsed.entries[1].transaction.type)
        assertEquals(2_500_000L, parsed.entries[1].transaction.amountPaise)
        assertFalse(parsed.entries[1].needsDirectionReview)
    }

    @Test
    fun flagsRowsWhenDebitCreditDirectionIsNotClear() {
        val parsed = PdfStatementParser.parseText("03/11/2026 NEFT TRANSFER 700.00 30,200.00")

        assertEquals(1, parsed.entries.size)
        assertTrue(parsed.entries.single().needsDirectionReview)
    }
}
