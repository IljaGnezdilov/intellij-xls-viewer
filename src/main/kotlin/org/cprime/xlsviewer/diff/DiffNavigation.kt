package org.cprime.xlsviewer.diff

/**
 * Pure navigation helpers for the diff viewer: where the caret should go when a row or a
 * column is clicked, and which marked row/column comes next.
 */
object DiffNavigation {

    /** Column of the first changed cell in [row], or null when the row has no changed cells. */
    fun firstChangedInRow(diff: SheetDiff, row: Int): Int? = diff.changedCells.keys.asSequence()
        .filter { (it ushr 32).toInt() == row }
        .map { (it and 0xFFFF_FFFFL).toInt() }
        .minOrNull()

    /** Row of the first changed cell in [col], or null when the column has no changed cells. */
    fun firstChangedInColumn(diff: SheetDiff, col: Int): Int? = diff.changedCells.keys.asSequence()
        .filter { (it and 0xFFFF_FFFFL).toInt() == col }
        .map { (it ushr 32).toInt() }
        .minOrNull()

    /**
     * Target cell for a click on the row header: the first changed cell of that row.
     * For an added/deleted row (marked, but without per-cell changes) the caret goes to the
     * first marked column; for a completely unchanged row - to the first column.
     */
    fun cellForRow(diff: SheetDiff, row: Int): Pair<Int, Int> {
        firstChangedInRow(diff, row)?.let { return row to it }
        return if (diff.rowMarks.getOrNull(row) != null) {
            row to firstMarkedColumn(diff)
        } else {
            row to 0
        }
    }

    /** Target cell for a click on the column header, mirroring [cellForRow]. */
    fun cellForColumn(diff: SheetDiff, col: Int): Pair<Int, Int> {
        firstChangedInColumn(diff, col)?.let { return it to col }
        return if (diff.colMarks.getOrNull(col) != null) {
            firstMarkedRow(diff) to col
        } else {
            0 to col
        }
    }

    fun nextMarkedRow(diff: SheetDiff, current: Int, delta: Int): Int? =
        step(diff.rowMarks.indices.filter { diff.rowMarks[it] != null }, current, delta)

    fun nextMarkedColumn(diff: SheetDiff, current: Int, delta: Int): Int? =
        step(diff.colMarks.indices.filter { diff.colMarks[it] != null }, current, delta)

    /** Sheet indexes shown in tabs/combo: changed sheets only in "only changes" mode. */
    fun visibleSheets(diff: WorkbookDiff, onlyDiffs: Boolean): List<Int> {
        if (!onlyDiffs) return diff.sheets.indices.toList()
        val changed = diff.sheets.indices.filter { diff.sheets[it].hasChanges }
        return changed.ifEmpty { diff.sheets.indices.toList() }
    }

    /** First change of the whole sheet, used when a sheet is opened. */
    fun firstChange(diff: SheetDiff): Pair<Int, Int> {
        val key = diff.changedCells.keys.minOrNull()
        if (key != null) {
            return (key ushr 32).toInt() to (key and 0xFFFF_FFFFL).toInt()
        }
        return firstMarkedRow(diff) to firstMarkedColumn(diff)
    }

    private fun firstMarkedRow(diff: SheetDiff): Int =
        diff.rowMarks.indexOfFirst { it != null }.let { if (it >= 0) it else 0 }

    private fun firstMarkedColumn(diff: SheetDiff): Int =
        diff.colMarks.indexOfFirst { it != null }.let { if (it >= 0) it else 0 }

    private fun step(values: List<Int>, current: Int, delta: Int): Int? {
        if (values.isEmpty()) return null
        return if (delta > 0) {
            values.firstOrNull { it > current } ?: values.first()
        } else {
            values.lastOrNull { it < current } ?: values.last()
        }
    }
}
