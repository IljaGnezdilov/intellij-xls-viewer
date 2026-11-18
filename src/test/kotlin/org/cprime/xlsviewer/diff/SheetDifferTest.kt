package org.cprime.xlsviewer.diff

import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.DiffMark
import org.cprime.xlsviewer.parse.XlsParser
import org.cprime.xlsviewer.tools.createDiffPair
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

    private fun compare(): WorkbookDiff {
        val (oldBytes, newBytes) = createDiffPair()
        return WorkbookDiffer.compare(
            XlsParser.open(oldBytes, Locale.US),
            XlsParser.open(newBytes, Locale.US),
        )
    }
}
