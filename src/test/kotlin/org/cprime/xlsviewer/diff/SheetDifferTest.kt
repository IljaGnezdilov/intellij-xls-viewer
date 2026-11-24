package org.cprime.xlsviewer.diff

import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.DiffMark
import org.cprime.xlsviewer.parse.XlsParser
import org.cprime.xlsviewer.tools.createClearedValuePair
import org.cprime.xlsviewer.tools.createDiffPair
import org.cprime.xlsviewer.tools.createEmptyCellsPair
import org.cprime.xlsviewer.tools.createEmptyRowColumnPair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class SheetDifferTest {

    @Test
    fun `matches sheets by name and marks added and deleted ones`() {
        val diff = compare()

        assertEquals(listOf("Данные", "Добавленный лист", "Удалённый лист"), diff.sheets.map { it.name })
        assertEquals(DiffMark.CHANGED, diff.sheets[0].mark)
        assertEquals(DiffMark.ADDED, diff.sheets[1].mark)
        assertEquals(DiffMark.DELETED, diff.sheets[2].mark)
        assertEquals(3, diff.changedSheetCount)
    }

    @Test
    fun `aligns rows and columns instead of shifting everything`() {
        val data = compare().sheets[0]

        assertEquals(
            listOf(
                RowPair(0, 0),
                RowPair(1, 1),
                RowPair(2, 2),
                RowPair(3, null),
                RowPair(4, 3),
                RowPair(5, 4),
                RowPair(null, 5),
            ),
            data.rows,
        )
        assertEquals(
            listOf(ColPair(0, 0), ColPair(null, 1), ColPair(1, 2), ColPair(2, 3)),
            data.cols,
        )
    }

    @Test
    fun `marks changed cells and rows`() {
        val data = compare().sheets[0]

        assertEquals(setOf(CellRange.keyOf(2, 3)), data.changedCells.keys)
        assertEquals(1, data.changedCellCount)
        assertEquals(DiffMark.CHANGED, data.rowMarks[2])
        assertEquals(DiffMark.DELETED, data.rowMarks[3])
        assertEquals(DiffMark.ADDED, data.rowMarks[6])
        assertEquals(DiffMark.ADDED, data.colMarks[1])
        assertEquals(DiffMark.CHANGED, data.colMarks[3])
        assertNull(data.rowMarks[0])
        assertNull(data.colMarks[0])
        assertNull(data.colMarks[2])
    }

    @Test
    fun `builds aligned panes with placeholders`() {
        val data = compare().sheets[0]
        val left = DiffSides.build(data, leftSide = true)
        val right = DiffSides.build(data, leftSide = false)

        assertEquals(left.rowCount, right.rowCount)
        assertEquals(left.colCount, right.colCount)

        val leftMarks = left.diff!!
        val rightMarks = right.diff!!

        // Deleted row: present on the left, placeholder on the right.
        assertFalse(leftMarks.isPlaceholderRow(3))
        assertTrue(rightMarks.isPlaceholderRow(3))
        // Added row: placeholder on the left.
        assertTrue(leftMarks.isPlaceholderRow(6))
        assertFalse(rightMarks.isPlaceholderRow(6))
        // Added column placeholder on the left.
        assertTrue(leftMarks.isPlaceholderCol(1))
        assertFalse(rightMarks.isPlaceholderCol(1))

        assertEquals("Слива", left.cell(3, 0)?.text)
        assertNull(right.cell(3, 0))
        assertEquals("Абрикос", right.cell(6, 0)?.text)
        assertNull(left.cell(6, 0))

        assertEquals(4, leftMarks.displayRow(3))
        assertEquals(4, rightMarks.displayRow(4))
        assertEquals(1, leftMarks.displayCol(2))
        assertEquals(2, rightMarks.displayCol(2))
    }

    @Test
    fun `empty cells are ignored by default`() {
        val diff = comparePair(createEmptyCellsPair(), ignoreEmptyChanges = true)
        val sheet = diff.sheets.single()

        assertFalse(sheet.hasChanges)
        assertEquals(0, sheet.changedCellCount)
        assertEquals(2, sheet.ignoredEmptyCellCount)
        assertEquals(2, diff.ignoredEmptyCellCount)
        assertTrue(sheet.rowMarks.all { it == null })
        assertTrue(sheet.colMarks.all { it == null })
        assertEquals(3, sheet.rows.size)
        assertEquals(3, sheet.cols.size)
    }

    @Test
    fun `showing empty changes restores their marks`() {
        val sheet = comparePair(createEmptyCellsPair(), ignoreEmptyChanges = false).sheets.single()

        assertEquals(DiffMark.CHANGED, sheet.mark)
        assertEquals(2, sheet.changedCellCount)
        assertEquals(0, sheet.ignoredEmptyCellCount)
        assertEquals(2, sheet.changedRowCount)
        assertEquals(1, sheet.changedColCount)
    }

    @Test
    fun `cleared value stays a change when empty changes are hidden`() {
        val sheet = comparePair(createClearedValuePair(), ignoreEmptyChanges = true).sheets.single()

        assertEquals(DiffMark.CHANGED, sheet.mark)
        assertEquals(1, sheet.changedCellCount)
        assertEquals(setOf(CellRange.keyOf(1, 1)), sheet.changedCells.keys)
        assertEquals(0, sheet.ignoredEmptyCellCount)
    }

    @Test
    fun `added empty rows and columns stay visible`() {
        val sheet = comparePair(createEmptyRowColumnPair(), ignoreEmptyChanges = true).sheets.single()

        assertEquals(DiffMark.CHANGED, sheet.mark)
        assertEquals(0, sheet.changedCellCount)
        assertEquals(1, sheet.addedRowCount)
        assertEquals(1, sheet.addedColCount)
        assertEquals(0, sheet.changedRowCount)
        assertEquals(0, sheet.changedColCount)
        assertEquals(0, sheet.ignoredEmptyCellCount)
        assertEquals(4, sheet.rows.size)
        assertEquals(3, sheet.cols.size)
    }

    private fun compare(): WorkbookDiff = comparePair(createDiffPair(), ignoreEmptyChanges = true)

    private fun comparePair(pair: Pair<ByteArray, ByteArray>, ignoreEmptyChanges: Boolean): WorkbookDiff =
        WorkbookDiffer.compare(
            XlsParser.open(pair.first, Locale.US),
            XlsParser.open(pair.second, Locale.US),
            ignoreEmptyChanges = ignoreEmptyChanges,
        )
}
