package org.cprime.xlsviewer.diff

import org.cprime.xlsviewer.parse.WorkbookParser
import org.cprime.xlsviewer.tools.createCellChangePair
import org.cprime.xlsviewer.tools.createColumnAddedPair
import org.cprime.xlsviewer.tools.createRowAddedPair
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class DiffSidesTest {

    private fun diffOf(pair: Pair<ByteArray, ByteArray>): WorkbookDiff = WorkbookDiffer.compare(
        WorkbookParser.open(pair.first, Locale.US),
        WorkbookParser.open(pair.second, Locale.US),
    )

    @Test
    fun `added row is shown with all its columns`() {
        val sheet = diffOf(createRowAddedPair()).sheets.single()
        val right = DiffSides.build(sheet, leftSide = false, collapsed = true, context = 0)

        // Header plus the added row; the unchanged data rows stay collapsed.
        assertFalse(right.isHidden(0))
        assertTrue(right.isHidden(1))
        assertTrue(right.isHidden(2))
        assertFalse(right.isHidden(3))

        // The added row carries data in all three columns, so all of them must be visible.
        for (column in 0..2) {
            assertFalse(right.isHiddenColumn(column), "column $column should be visible")
        }
        assertTrue(right.diff!!.startsRowRange(3))
        assertFalse(right.diff!!.startsRowRange(1))
    }

    @Test
    fun `added column is shown across all data rows`() {
        val sheet = diffOf(createColumnAddedPair()).sheets.single()
        val right = DiffSides.build(sheet, leftSide = false, collapsed = true, context = 0)

        // Every row has data in the new column, so the row view is fully expanded.
        for (row in 0..3) {
            assertFalse(right.isHidden(row), "row $row should be visible")
        }
        assertFalse(right.isHiddenColumn(0))
        assertFalse(right.isHiddenColumn(1))
        assertTrue(right.isHiddenColumn(2))
    }

    @Test
    fun `collapsed rows and columns are marked as gaps`() {
        val sheet = diffOf(createCellChangePair()).sheets.single()
        val left = DiffSides.build(sheet, leftSide = true, collapsed = true, context = 0)

        assertFalse(left.isHidden(0))
        assertTrue(left.isHidden(1))
        assertFalse(left.isHidden(30))
        assertTrue(left.isHiddenColumn(1))
        assertFalse(left.isHiddenColumn(2))

        val marks = left.diff!!
        assertTrue(marks.startsRowRange(30))
        assertTrue(marks.startsColRange(2))
    }

    @Test
    fun `sheet without changes is never collapsed`() {
        val (oldBytes, _) = createRowAddedPair()
        val document = WorkbookParser.open(oldBytes, Locale.US)
        val diff = WorkbookDiffer.compare(document, document)
        val model = DiffSides.build(diff.sheets.single(), leftSide = true, collapsed = true, context = 0)

        assertFalse(model.isHidden(0))
        assertFalse(model.isHidden(1))
        assertFalse(model.isHidden(2))
    }
}
