package org.cprime.xlsviewer.diff

import org.cprime.xlsviewer.parse.XlsParser
import org.cprime.xlsviewer.tools.createDiffPair
import org.cprime.xlsviewer.tools.createPartiallyChangedPair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

class DiffNavigationTest {

    private fun dataSheet(): SheetDiff {
        val (oldBytes, newBytes) = createDiffPair()
        val diff = WorkbookDiffer.compare(
            XlsParser.open(oldBytes, Locale.US),
            XlsParser.open(newBytes, Locale.US),
        )
        return diff.sheets.first()
    }

    @Test
    fun `finds first changed cell in rows and columns`() {
        val diff = dataSheet()
        assertEquals(3, DiffNavigation.firstChangedInRow(diff, 2))
        assertNull(DiffNavigation.firstChangedInRow(diff, 0))
        assertEquals(2, DiffNavigation.firstChangedInColumn(diff, 3))
        assertNull(DiffNavigation.firstChangedInColumn(diff, 0))
    }

    @Test
    fun `row and column header clicks target the first change`() {
        val diff = dataSheet()

        assertEquals(2 to 3, DiffNavigation.cellForRow(diff, 2))
        // Added/deleted row: no per-cell changes, go to the first marked column.
        assertEquals(3 to 1, DiffNavigation.cellForRow(diff, 3))
        // Unchanged row: plain first cell.
        assertEquals(0 to 0, DiffNavigation.cellForRow(diff, 0))

        assertEquals(2 to 3, DiffNavigation.cellForColumn(diff, 3))
        // Added column: first marked row.
        assertEquals(2 to 1, DiffNavigation.cellForColumn(diff, 1))
        assertEquals(0 to 0, DiffNavigation.cellForColumn(diff, 0))
    }

    @Test
    fun `marked rows and columns navigate with wrapping`() {
        val diff = dataSheet()

        assertEquals(3, DiffNavigation.nextMarkedRow(diff, 2, 1))
        assertEquals(2, DiffNavigation.nextMarkedRow(diff, 6, 1))
        assertEquals(6, DiffNavigation.nextMarkedRow(diff, 2, -1))
        assertEquals(2, DiffNavigation.nextMarkedRow(diff, 0, 1))

        assertEquals(3, DiffNavigation.nextMarkedColumn(diff, 1, 1))
        assertEquals(1, DiffNavigation.nextMarkedColumn(diff, 3, 1))
        assertEquals(1, DiffNavigation.nextMarkedColumn(diff, 0, 1))
    }

    @Test
    fun `first change prefers a changed cell`() {
        assertEquals(2 to 3, DiffNavigation.firstChange(dataSheet()))
    }

    @Test
    fun `only-changes mode filters out unchanged sheets`() {
        val (oldBytes, newBytes) = createPartiallyChangedPair()
        val diff = WorkbookDiffer.compare(
            XlsParser.open(oldBytes, Locale.US),
            XlsParser.open(newBytes, Locale.US),
        )
        assertEquals(listOf(0, 1), DiffNavigation.visibleSheets(diff, onlyDiffs = false))
        assertEquals(listOf(0), DiffNavigation.visibleSheets(diff, onlyDiffs = true))

        val same = XlsParser.open(oldBytes, Locale.US)
        val unchanged = WorkbookDiffer.compare(same, same)
        assertEquals(listOf(0, 1), DiffNavigation.visibleSheets(unchanged, onlyDiffs = true))
    }
}
