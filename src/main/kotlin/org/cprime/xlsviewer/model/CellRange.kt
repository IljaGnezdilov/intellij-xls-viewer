package org.cprime.xlsviewer.model

/**
 * Inclusive rectangular range of cells. Rows and columns are zero-based.
 */
data class CellRange(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
) {
    val rowCount: Int get() = endRow - startRow + 1
    val colCount: Int get() = endCol - startCol + 1
    val area: Int get() = rowCount * colCount

    fun contains(row: Int, col: Int): Boolean = row in startRow..endRow && col in startCol..endCol

    fun normalized(): CellRange {
        val r0 = minOf(startRow, endRow)
        val r1 = maxOf(startRow, endRow)
        val c0 = minOf(startCol, endCol)
        val c1 = maxOf(startCol, endCol)
        return CellRange(r0, c0, r1, c1)
    }

    fun anchorKey(): Long = keyOf(startRow, startCol)

    companion object {
        fun keyOf(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFF_FFFFL)

        fun single(row: Int, col: Int): CellRange = CellRange(row, col, row, col)

        fun between(row1: Int, col1: Int, row2: Int, col2: Int): CellRange =
            CellRange(minOf(row1, row2), minOf(col1, col2), maxOf(row1, row2), maxOf(col1, col2))
    }
}
