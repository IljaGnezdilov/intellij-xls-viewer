package org.cprime.xlsviewer.ui

import org.cprime.xlsviewer.model.SheetModel
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pixel geometry of a sheet at a fixed zoom level. Row and column offsets are
 * pre-computed prefix sums, so hit testing and virtualized painting are O(log n).
 */
class GridGeometry(sheet: SheetModel, val zoom: Float) {
    val rowCount: Int = sheet.rowCount
    val colCount: Int = sheet.colCount

    private val rowHeights = IntArray(rowCount) { index ->
        if (sheet.isHidden(index)) 0 else max(1, (sheet.heightOf(index) * zoom).roundToInt())
    }
    private val colWidths = IntArray(colCount) { index ->
        if (sheet.isHiddenColumn(index)) 0 else max(1, (sheet.widthOf(index) * zoom).roundToInt())
    }

    val rowY = IntArray(rowCount + 1)
    val colX = IntArray(colCount + 1)

    init {
        for (index in 0 until rowCount) rowY[index + 1] = rowY[index] + rowHeights[index]
        for (index in 0 until colCount) colX[index + 1] = colX[index] + colWidths[index]
    }

    val totalHeight: Int get() = rowY[rowCount]
    val totalWidth: Int get() = colX[colCount]

    fun rowHeight(row: Int): Int = rowHeights.getOrElse(row) { 0 }

    fun colWidth(col: Int): Int = colWidths.getOrElse(col) { 0 }

    /** Row whose vertical span contains [y]. Hidden (zero-height) rows are skipped. */
    fun rowAt(y: Int): Int {
        if (rowCount == 0) return 0
        var lo = 0
        var hi = rowCount - 1
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (rowY[mid] <= y) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        while (found < rowCount - 1 && rowHeights[found] == 0) found++
        return found
    }

    /** Column whose horizontal span contains [x]. Hidden (zero-width) columns are skipped. */
    fun colAt(x: Int): Int {
        if (colCount == 0) return 0
        var lo = 0
        var hi = colCount - 1
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (colX[mid] <= x) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        while (found < colCount - 1 && colWidths[found] == 0) found++
        return found
    }

    fun rectOf(row: Int, col: Int, endRow: Int = row, endCol: Int = col): java.awt.Rectangle {
        val x = colX[col.coerceIn(0, colCount)]
        val y = rowY[row.coerceIn(0, rowCount)]
        val x2 = colX[(endCol + 1).coerceIn(0, colCount)]
        val y2 = rowY[(endRow + 1).coerceIn(0, rowCount)]
        return java.awt.Rectangle(x, y, max(0, x2 - x), max(0, y2 - y))
    }
}

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** 0 -> A, 25 -> Z, 26 -> AA, ... */
fun columnName(index: Int): String {
    if (index < 0) return ""
    var value = index
    val builder = StringBuilder()
    while (value >= 0) {
        builder.insert(0, ALPHABET[value % 26])
        value = value / 26 - 1
    }
    return builder.toString()
}

/** 0,0 -> "A1" */
fun cellAddress(row: Int, col: Int): String = columnName(col) + (row + 1)
