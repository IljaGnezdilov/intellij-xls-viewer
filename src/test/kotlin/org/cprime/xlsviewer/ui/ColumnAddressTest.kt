package org.cprime.xlsviewer.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColumnAddressTest {

    @Test
    fun `maps column indexes to Excel letters`() {
        assertEquals("A", columnName(0))
        assertEquals("B", columnName(1))
        assertEquals("Z", columnName(25))
        assertEquals("AA", columnName(26))
        assertEquals("AB", columnName(27))
        assertEquals("AZ", columnName(51))
        assertEquals("BA", columnName(52))
        assertEquals("IV", columnName(255))
    }

    @Test
    fun `builds cell addresses`() {
        assertEquals("A1", cellAddress(0, 0))
        assertEquals("C7", cellAddress(6, 2))
        assertEquals("IV65536", cellAddress(65_535, 255))
    }
}
