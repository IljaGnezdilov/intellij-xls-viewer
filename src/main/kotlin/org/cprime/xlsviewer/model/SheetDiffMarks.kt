package org.cprime.xlsviewer.model

/** How a sheet, row, column or cell differs between two workbooks. */
enum class DiffMark { CHANGED, ADDED, DELETED }

/**
 * Diff metadata attached to a synthetic "aligned" sheet model shown in one pane of the
 * diff viewer. All arrays are indexed by aligned row/column positions, not by the
 * original spreadsheet coordinates.
 */
class SheetDiffMarks(
    /** Mark per aligned row, null when the row is unchanged or missing on this side. */
    private val rowMarks: Array<DiffMark?>,
    /** Mark per aligned column, null when unchanged or missing on this side. */
    private val colMarks: Array<DiffMark?>,
    /** Changed cells only, keyed by aligned coordinates. */
    private val changedCells: Map<Long, DiffMark>,
    private val placeholderRows: BooleanArray,
    private val placeholderCols: BooleanArray,
    /** 1-based row numbers to render in the row header, 0 for placeholders. */
    private val displayRows: IntArray,
    /** 0-based column indexes used for column letters, -1 for placeholders. */
    private val displayCols: IntArray,
    /** Rows/columns hidden by the "only changes" mode (not by the workbook itself). */
    private val collapsedRows: BooleanArray,
    private val collapsedCols: BooleanArray,
) {
    fun rowMark(row: Int): DiffMark? = rowMarks.getOrNull(row)

    fun colMark(col: Int): DiffMark? = colMarks.getOrNull(col)

    fun cellMark(row: Int, col: Int): DiffMark? = changedCells[CellRange.keyOf(row, col)]

    fun isPlaceholderRow(row: Int): Boolean = placeholderRows.getOrNull(row) == true

    fun isPlaceholderCol(col: Int): Boolean = placeholderCols.getOrNull(col) == true

    fun displayRow(row: Int): Int = displayRows.getOrNull(row) ?: 0

    fun displayCol(col: Int): Int = displayCols.getOrNull(col) ?: -1

    fun isCollapsedRow(row: Int): Boolean = collapsedRows.getOrNull(row) == true

    fun isCollapsedCol(col: Int): Boolean = collapsedCols.getOrNull(col) == true

    /** True when [row] is the first visible row after a collapsed run. */
    fun startsRowRange(row: Int): Boolean =
        row > 0 && row < collapsedRows.size && collapsedRows[row - 1] && !collapsedRows[row]

    /** True when [col] is the first visible column after a collapsed run. */
    fun startsColRange(col: Int): Boolean =
        col > 0 && col < collapsedCols.size && collapsedCols[col - 1] && !collapsedCols[col]

    val hasAnyMark: Boolean
        get() = rowMarks.any { it != null } || colMarks.any { it != null } || changedCells.isNotEmpty()
}
