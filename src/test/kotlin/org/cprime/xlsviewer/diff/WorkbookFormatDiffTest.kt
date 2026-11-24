package org.cprime.xlsviewer.diff

import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.DiffMark
import org.cprime.xlsviewer.parse.WorkbookParser
import org.cprime.xlsviewer.tools.createDiffPair
import org.cprime.xlsviewer.tools.createDiffPairXlsx
import org.cprime.xlsviewer.tools.createMixedPair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** The diff is format agnostic: the same workbooks must compare the same way in both containers. */
class WorkbookFormatDiffTest {

    private fun compare(pair: Pair<ByteArray, ByteArray>): WorkbookDiff = WorkbookDiffer.compare(
        WorkbookParser.open(pair.first, Locale.US),
        WorkbookParser.open(pair.second, Locale.US),
    )

    @Test
    fun `xlsx workbooks produce the same diff as xls`() {
        val xls = compare(createDiffPair())
        val xlsx = compare(createDiffPairXlsx())

        assertEquals(xls.sheets.map { it.name }, xlsx.sheets.map { it.name })
        assertEquals(xls.sheets.map { it.mark }, xlsx.sheets.map { it.mark })
        assertEquals(xls.changedSheetCount, xlsx.changedSheetCount)
        assertEquals(xls.changedCellCount, xlsx.changedCellCount)
        assertEquals(xls.ignoredEmptyCellCount, xlsx.ignoredEmptyCellCount)

        for (index in xls.sheets.indices) {
            val left = xls.sheets[index]
            val right = xlsx.sheets[index]
            assertEquals(left.rows, right.rows, "rows of sheet $index")
            assertEquals(left.cols, right.cols, "columns of sheet $index")
            assertEquals(left.rowMarks.toList(), right.rowMarks.toList(), "row marks of sheet $index")
            assertEquals(left.colMarks.toList(), right.colMarks.toList(), "column marks of sheet $index")
            assertEquals(left.changedCells, right.changedCells, "changed cells of sheet $index")
        }
    }

    @Test
    fun `xls and xlsx sides with the same content have no changes`() {
        val diff = compare(createMixedPair(changed = false))
        val sheet = diff.sheets.single()
        assertNull(sheet.mark)
        assertTrue(sheet.changedCells.isEmpty())
    }

    @Test
    fun `xls and xlsx sides report the changed cell`() {
        val diff = compare(createMixedPair(changed = true))
        val sheet = diff.sheets.single()
        assertEquals(DiffMark.CHANGED, sheet.mark)
        assertEquals(setOf(CellRange.keyOf(1, 1)), sheet.changedCells.keys)
        assertEquals(1, sheet.changedCellCount)
    }
}
