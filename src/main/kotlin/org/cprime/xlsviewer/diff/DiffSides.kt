package org.cprime.xlsviewer.diff

import org.cprime.xlsviewer.model.CellModel
import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.CellStyleModel
import org.cprime.xlsviewer.model.DiffMark
import org.cprime.xlsviewer.model.FontModel
import org.cprime.xlsviewer.model.RowModel
import org.cprime.xlsviewer.model.SheetDiffMarks
import org.cprime.xlsviewer.model.SheetModel
import org.cprime.xlsviewer.model.StyleTable

/**
 * Builds the synthetic "aligned" sheet model rendered in one pane of the diff viewer.
 * Placeholders keep the geometry of the opposite side, so both panes line up.
 */
object DiffSides {
    private const val DEFAULT_ROW_HEIGHT = 17f
    private const val DEFAULT_COL_WIDTH = 64f

    fun build(
        diff: SheetDiff,
        leftSide: Boolean,
        collapsed: Boolean = false,
        context: Int = 1,
    ): SheetModel {
        val source = if (leftSide) diff.left else diff.right
        val other = if (leftSide) diff.right else diff.left
        val sourceRows = if (leftSide) diff.rows.map { it.left } else diff.rows.map { it.right }
        val sourceCols = if (leftSide) diff.cols.map { it.left } else diff.cols.map { it.right }
        val otherRows = if (leftSide) diff.rows.map { it.right } else diff.rows.map { it.left }
        val otherCols = if (leftSide) diff.cols.map { it.right } else diff.cols.map { it.left }

        val rowCount = diff.rows.size
        val colCount = diff.cols.size

        val applyCollapse = collapsed && diff.hasChanges
        val visibleRows = BooleanArray(rowCount) { !applyCollapse }
        val visibleCols = BooleanArray(colCount) { !applyCollapse }
        if (applyCollapse) {
            val ctx = context.coerceAtLeast(0)
            // Keep the first row and column as an orientation "header".
            if (rowCount > 0) visibleRows[0] = true
            if (colCount > 0) visibleCols[0] = true
            for (index in diff.rowMarks.indices) {
                if (diff.rowMarks[index] == null) continue
                for (row in (index - ctx)..(index + ctx)) {
                    if (row in 0 until rowCount) visibleRows[row] = true
                }
            }
            for (index in diff.colMarks.indices) {
                if (diff.colMarks[index] == null) continue
                for (col in (index - ctx)..(index + ctx)) {
                    if (col in 0 until colCount) visibleCols[col] = true
                }
            }
            // An added/deleted row is meaningless with only one visible cell: show all columns
            // that actually contain data in such rows. Symmetrically for added/deleted columns.
            expandColumnsForWholeRows(diff, visibleCols)
            expandRowsForWholeColumns(diff, visibleRows)
        }

        val rowMap = IntArray(source?.rowCount ?: 0) { -1 }
        sourceRows.forEachIndexed { aligned, sourceRowIndex ->
            if (sourceRowIndex != null) rowMap[sourceRowIndex] = aligned
        }
        val colMap = IntArray(source?.colCount ?: 0) { -1 }
        sourceCols.forEachIndexed { aligned, sourceColIndex ->
            if (sourceColIndex != null) colMap[sourceColIndex] = aligned
        }

        val rowHeights = FloatArray(rowCount) { index ->
            val own = sourceRows[index]
            if (own != null && source != null) {
                source.heightOf(own)
            } else {
                val opposite = otherRows[index]
                if (opposite != null && other != null) other.heightOf(opposite) else DEFAULT_ROW_HEIGHT
            }
        }
        val colWidths = FloatArray(colCount) { index ->
            val own = sourceCols[index]
            if (own != null && source != null) {
                source.widthOf(own)
            } else {
                val opposite = otherCols[index]
                if (opposite != null && other != null) other.widthOf(opposite) else DEFAULT_COL_WIDTH
            }
        }
        val hiddenRows = BooleanArray(rowCount) { index ->
            val own = sourceRows[index]
            (own != null && source != null && source.isHidden(own)) || !visibleRows[index]
        }
        val hiddenCols = BooleanArray(colCount) { index ->
            val own = sourceCols[index]
            (own != null && source != null && source.isHiddenColumn(own)) || !visibleCols[index]
        }

        val rows = arrayOfNulls<RowModel>(rowCount)
        if (source != null) {
            for (alignedRow in 0 until rowCount) {
                val sourceRow = sourceRows[alignedRow] ?: continue
                val cells = arrayOfNulls<CellModel>(colCount)
                var any = false
                for (alignedCol in 0 until colCount) {
                    val sourceCol = sourceCols[alignedCol] ?: continue
                    val cell = source.cell(sourceRow, sourceCol) ?: continue
                    cells[alignedCol] = CellModel(
                        row = alignedRow,
                        col = alignedCol,
                        kind = cell.kind,
                        text = cell.text,
                        styleId = cell.styleId,
                        formula = cell.formula,
                    )
                    any = true
                }
                if (any) {
                    rows[alignedRow] = RowModel(alignedRow, rowHeights[alignedRow], hiddenRows[alignedRow], cells)
                }
            }
        }

        val merged = if (source == null) {
            emptyList()
        } else {
            val mapped = source.merged.mapNotNull { region ->
                val startRow = rowMap.getOrNull(region.startRow) ?: return@mapNotNull null
                val endRow = rowMap.getOrNull(region.endRow) ?: return@mapNotNull null
                val startCol = colMap.getOrNull(region.startCol) ?: return@mapNotNull null
                val endCol = colMap.getOrNull(region.endCol) ?: return@mapNotNull null
                if (startRow < 0 || endRow < 0 || startCol < 0 || endCol < 0) {
                    null
                } else {
                    CellRange(startRow, startCol, endRow, endCol)
                }
            }
            if (applyCollapse) {
                mapped.filter { region ->
                    (region.startRow..region.endRow).all { visibleRows[it] } &&
                        (region.startCol..region.endCol).all { visibleCols[it] }
                }
            } else {
                mapped
            }
        }

        val styles = source?.styles ?: other?.styles ?: StyleTable(
            emptyMap(),
            CellStyleModel(0, FontModel("Arial", 10f)),
        )

        val marks = SheetDiffMarks(
            rowMarks = Array(rowCount) { index ->
                if (sourceRows[index] == null) null else diff.rowMarks.getOrNull(index)
            },
            colMarks = Array(colCount) { index ->
                if (sourceCols[index] == null) null else diff.colMarks.getOrNull(index)
            },
            changedCells = diff.changedCells,
            placeholderRows = BooleanArray(rowCount) { sourceRows[it] == null },
            placeholderCols = BooleanArray(colCount) { sourceCols[it] == null },
            displayRows = IntArray(rowCount) { index -> (sourceRows[index] ?: -1) + 1 },
            displayCols = IntArray(colCount) { index -> sourceCols[index] ?: -1 },
            collapsedRows = BooleanArray(rowCount) { applyCollapse && !visibleRows[it] },
            collapsedCols = BooleanArray(colCount) { applyCollapse && !visibleCols[it] },
        )

        return SheetModel(
            index = if (leftSide) 0 else 1,
            name = diff.name,
            hidden = false,
            tabColor = null,
            rowCount = rowCount,
            colCount = colCount,
            rows = rows,
            rowHeights = rowHeights,
            colWidths = colWidths,
            hiddenRows = hiddenRows,
            hiddenCols = hiddenCols,
            merged = merged,
            freezeRows = mapFreeze(sourceRows, source?.freezeRows ?: 0),
            freezeCols = mapFreeze(sourceCols, source?.freezeCols ?: 0),
            showGridlines = source?.showGridlines ?: other?.showGridlines ?: true,
            rtl = source?.rtl ?: other?.rtl ?: false,
            styles = styles,
            usedRows = rowCount,
            usedCols = colCount,
            diff = marks,
        )
    }

    /** Aligned index of the first row/column that lies outside the frozen band. */
    private fun mapFreeze(sourceIndexes: List<Int?>, frozen: Int): Int {
        if (frozen <= 0) return 0
        val index = sourceIndexes.indexOfFirst { it != null && it >= frozen }
        return if (index < 0) 0 else index
    }

    private fun expandColumnsForWholeRows(diff: SheetDiff, visibleCols: BooleanArray) {
        for (alignedRow in diff.rows.indices) {
            val mark = diff.rowMarks.getOrNull(alignedRow) ?: continue
            if (mark == DiffMark.CHANGED) continue
            val added = mark == DiffMark.ADDED
            val source = (if (added) diff.right else diff.left) ?: continue
            val pair = diff.rows[alignedRow]
            val sourceRow = (if (added) pair.right else pair.left) ?: continue
            val rowModel = source.rows.getOrNull(sourceRow) ?: continue
            for (alignedCol in diff.cols.indices) {
                val sourceCol = (if (added) diff.cols[alignedCol].right else diff.cols[alignedCol].left) ?: continue
                if (rowModel.cells.getOrNull(sourceCol) != null) visibleCols[alignedCol] = true
            }
        }
    }

    private fun expandRowsForWholeColumns(diff: SheetDiff, visibleRows: BooleanArray) {
        for (alignedCol in diff.cols.indices) {
            val mark = diff.colMarks.getOrNull(alignedCol) ?: continue
            if (mark == DiffMark.CHANGED) continue
            val added = mark == DiffMark.ADDED
            val source = (if (added) diff.right else diff.left) ?: continue
            val pair = diff.cols[alignedCol]
            val sourceCol = (if (added) pair.right else pair.left) ?: continue
            for (alignedRow in diff.rows.indices) {
                val sourceRow = (if (added) diff.rows[alignedRow].right else diff.rows[alignedRow].left) ?: continue
                val rowModel = source.rows.getOrNull(sourceRow) ?: continue
                if (rowModel.cells.getOrNull(sourceCol) != null) visibleRows[alignedRow] = true
            }
        }
    }
}
