package org.cprime.xlsviewer.diff

import org.cprime.xlsviewer.model.CellModel
import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.DiffMark
import org.cprime.xlsviewer.model.SheetModel
import org.cprime.xlsviewer.model.XlsDocument

data class RowPair(val left: Int?, val right: Int?)

data class ColPair(val left: Int?, val right: Int?)

/**
 * Result of comparing one worksheet with the same-named worksheet of the other workbook.
 * [rows] and [cols] are aligned sequences: every entry either exists on both sides,
 * or only on one side (additions/deletions).
 */
class SheetDiff(
    val name: String,
    val left: SheetModel?,
    val right: SheetModel?,
    val mark: DiffMark?,
    val rows: List<RowPair>,
    val cols: List<ColPair>,
    val rowMarks: Array<DiffMark?>,
    val colMarks: Array<DiffMark?>,
    /** Changed cells only, in aligned coordinates. */
    val changedCells: Map<Long, DiffMark>,
    val changedCellCount: Int,
    /**
     * Cell differences that were suppressed because both sides are empty (a formatting-only
     * cell against a missing cell and so on). Zero when empty changes are shown.
     */
    val ignoredEmptyCellCount: Int,
    val changedRowCount: Int,
    val addedRowCount: Int,
    val deletedRowCount: Int,
    val changedColCount: Int,
    val addedColCount: Int,
    val deletedColCount: Int,
) {
    val hasChanges: Boolean get() = mark != null
}

class WorkbookDiff(val sheets: List<SheetDiff>) {
    val changedSheetCount: Int get() = sheets.count { it.hasChanges }
    val changedCellCount: Int get() = sheets.sumOf { it.changedCellCount }
    val ignoredEmptyCellCount: Int get() = sheets.sumOf { it.ignoredEmptyCellCount }
}

/**
 * Compares two workbooks. Sheets are matched by name, columns and rows are aligned with a
 * patience-style diff so that inserted/deleted columns and rows do not mark everything after
 * them as changed.
 *
 * With [compare]'s `ignoreEmptyChanges` (the default) cells that are empty on both sides -
 * a formatting-only cell against a missing one, an empty formula result, whitespace-only
 * text - are treated as equal: they get no marks, no counters and no collapsed-view rows.
 * Rows and columns that exist on one side only are still reported even when they are empty.
 */
object WorkbookDiffer {

    fun compare(
        left: XlsDocument,
        right: XlsDocument,
        ignoreEmptyChanges: Boolean = true,
    ): WorkbookDiff {
        val byName = LinkedHashMap<String, Pair<SheetModel?, SheetModel?>>()
        for (sheet in right.sheets) byName[sheet.name] = null to sheet
        for (sheet in left.sheets) byName[sheet.name] = sheet to byName[sheet.name]?.second
        val sheets = byName.map { (name, pair) ->
            SheetDiffer.compare(name, pair.first, pair.second, ignoreEmptyChanges)
        }
        return WorkbookDiff(sheets)
    }
}

object SheetDiffer {

    fun compare(
        name: String,
        left: SheetModel?,
        right: SheetModel?,
        ignoreEmptyChanges: Boolean = true,
    ): SheetDiff {
        if (left == null && right == null) return emptyDiff(name)
        if (left == null) return oneSided(name, right!!, DiffMark.ADDED, rightSide = true)
        if (right == null) return oneSided(name, left, DiffMark.DELETED, rightSide = false)
        return compareBoth(name, left, right, ignoreEmptyChanges)
    }

    private fun emptyDiff(name: String): SheetDiff = SheetDiff(
        name = name,
        left = null,
        right = null,
        mark = null,
        rows = emptyList(),
        cols = emptyList(),
        rowMarks = emptyArray(),
        colMarks = emptyArray(),
        changedCells = emptyMap(),
        changedCellCount = 0,
        ignoredEmptyCellCount = 0,
        changedRowCount = 0,
        addedRowCount = 0,
        deletedRowCount = 0,
        changedColCount = 0,
        addedColCount = 0,
        deletedColCount = 0,
    )

    private fun oneSided(name: String, sheet: SheetModel, mark: DiffMark, rightSide: Boolean): SheetDiff {
        val usedRows = sheet.usedRows.coerceIn(1, sheet.rowCount)
        val usedCols = sheet.usedCols.coerceIn(1, sheet.colCount)
        val rows = (0 until usedRows).map { row ->
            if (rightSide) RowPair(null, row) else RowPair(row, null)
        }
        val cols = (0 until usedCols).map { col ->
            if (rightSide) ColPair(null, col) else ColPair(col, null)
        }
        val rowMarks = Array<DiffMark?>(usedRows) { mark }
        val colMarks = Array<DiffMark?>(usedCols) { mark }
        return SheetDiff(
            name = name,
            left = if (rightSide) null else sheet,
            right = if (rightSide) sheet else null,
            mark = mark,
            rows = rows,
            cols = cols,
            rowMarks = rowMarks,
            colMarks = colMarks,
            changedCells = emptyMap(),
            changedCellCount = 0,
            ignoredEmptyCellCount = 0,
            changedRowCount = 0,
            addedRowCount = if (mark == DiffMark.ADDED) usedRows else 0,
            deletedRowCount = if (mark == DiffMark.DELETED) usedRows else 0,
            changedColCount = 0,
            addedColCount = if (mark == DiffMark.ADDED) usedCols else 0,
            deletedColCount = if (mark == DiffMark.DELETED) usedCols else 0,
        )
    }

    private fun compareBoth(
        name: String,
        left: SheetModel,
        right: SheetModel,
        ignoreEmptyChanges: Boolean,
    ): SheetDiff {
        val leftUsedRows = left.usedRows.coerceIn(1, left.rowCount)
        val rightUsedRows = right.usedRows.coerceIn(1, right.rowCount)
        val leftUsedCols = left.usedCols.coerceIn(1, left.colCount)
        val rightUsedCols = right.usedCols.coerceIn(1, right.colCount)

        // Columns are matched by their header cell: the row with the most filled cells among
        // the first rows (handles merged title rows above the real header).
        val leftHeaderRow = headerRowIndex(left, leftUsedRows, ignoreEmptyChanges)
        val rightHeaderRow = headerRowIndex(right, rightUsedRows, ignoreEmptyChanges)
        val leftColKeys = List(leftUsedCols) { headerKey(left, leftHeaderRow, it, ignoreEmptyChanges) }
        val rightColKeys = List(rightUsedCols) { headerKey(right, rightHeaderRow, it, ignoreEmptyChanges) }
        val cols = align(leftColKeys, rightColKeys).map { ColPair(it.first, it.second) }

        val commonLeft = ArrayList<Int>(cols.size)
        val commonRight = ArrayList<Int>(cols.size)
        for (pair in cols) {
            val l = pair.left
            val r = pair.right
            if (l != null && r != null) {
                commonLeft += l
                commonRight += r
            }
        }

        val leftRowKeys = List(leftUsedRows) { rowKey(left, it, commonLeft, ignoreEmptyChanges) }
        val rightRowKeys = List(rightUsedRows) { rowKey(right, it, commonRight, ignoreEmptyChanges) }
        val rows = align(leftRowKeys, rightRowKeys).map { RowPair(it.first, it.second) }

        val changedCells = HashMap<Long, DiffMark>()
        val rowHasChange = BooleanArray(rows.size)
        val colHasChange = BooleanArray(cols.size)
        var ignoredEmptyCells = 0

        for ((rowIndex, rowPair) in rows.withIndex()) {
            val leftRow = rowPair.left
            val rightRow = rowPair.right
            if (leftRow == null || rightRow == null) continue
            val leftRowModel = left.rows.getOrNull(leftRow)
            val rightRowModel = right.rows.getOrNull(rightRow)
            if (leftRowModel == null && rightRowModel == null) continue

            for ((colIndex, colPair) in cols.withIndex()) {
                val leftCol = colPair.left
                val rightCol = colPair.right
                if (leftCol == null || rightCol == null) continue
                val a = leftRowModel?.cellAt(leftCol)
                val b = rightRowModel?.cellAt(rightCol)
                if (a == null && b == null) continue
                if (ignoreEmptyChanges && isEmptyCell(a) && isEmptyCell(b)) {
                    // A formatting-only cell against a missing one (or an empty formula result,
                    // whitespace-only text...): structurally different, visually identical.
                    if (!cellsEqual(a, b)) ignoredEmptyCells++
                    continue
                }
                if (!cellsEqual(a, b)) {
                    changedCells[CellRange.keyOf(rowIndex, colIndex)] = DiffMark.CHANGED
                    rowHasChange[rowIndex] = true
                    colHasChange[colIndex] = true
                }
            }
        }

        val rowMarks = Array<DiffMark?>(rows.size) { index ->
            when {
                rows[index].left == null -> DiffMark.ADDED
                rows[index].right == null -> DiffMark.DELETED
                rowHasChange[index] -> DiffMark.CHANGED
                else -> null
            }
        }
        val colMarks = Array<DiffMark?>(cols.size) { index ->
            when {
                cols[index].left == null -> DiffMark.ADDED
                cols[index].right == null -> DiffMark.DELETED
                colHasChange[index] -> DiffMark.CHANGED
                else -> null
            }
        }

        val changed = changedCells.isNotEmpty() ||
            rowMarks.any { it != null } || colMarks.any { it != null }

        return SheetDiff(
            name = name,
            left = left,
            right = right,
            mark = if (changed) DiffMark.CHANGED else null,
            rows = rows,
            cols = cols,
            rowMarks = rowMarks,
            colMarks = colMarks,
            changedCells = changedCells,
            changedCellCount = changedCells.size,
            ignoredEmptyCellCount = ignoredEmptyCells,
            changedRowCount = rowMarks.count { it == DiffMark.CHANGED },
            addedRowCount = rowMarks.count { it == DiffMark.ADDED },
            deletedRowCount = rowMarks.count { it == DiffMark.DELETED },
            changedColCount = colMarks.count { it == DiffMark.CHANGED },
            addedColCount = colMarks.count { it == DiffMark.ADDED },
            deletedColCount = colMarks.count { it == DiffMark.DELETED },
        )
    }

    // ------------------------------------------------------------------ keys

    private const val SEED = -3750763034362895579L // FNV-1a 64 offset basis
    private const val PRIME = 1099511628211L
    private const val HEADER_SCAN_ROWS = 10

    private fun mix(hash: Long, value: Int): Long = (hash xor value.toLong()) * PRIME


    private fun headerRowIndex(sheet: SheetModel, usedRows: Int, ignoreEmptyChanges: Boolean): Int {
        var bestRow = -1
        var bestCount = 0
        val limit = minOf(usedRows, HEADER_SCAN_ROWS)
        for (row in 0 until limit) {
            val cells = sheet.rows.getOrNull(row)?.cells ?: continue
            val count = cells.count { it != null && (!ignoreEmptyChanges || !it.isEmpty) }
            if (count > bestCount) {
                bestCount = count
                bestRow = row
            }
        }
        return bestRow
    }

    private fun headerKey(sheet: SheetModel, headerRow: Int, col: Int, ignoreEmptyChanges: Boolean): Long {
        if (headerRow < 0) return SEED
        val cell = sheet.rows.getOrNull(headerRow)?.cellAt(col) ?: return SEED
        if (ignoreEmptyChanges && cell.isEmpty) return SEED
        var hash = mix(SEED, cell.kind.ordinal)
        hash = mix(hash, cell.text.hashCode())
        return hash
    }

    private fun rowKey(sheet: SheetModel, row: Int, cols: List<Int>, ignoreEmptyChanges: Boolean): Long {
        val rowModel = sheet.rows.getOrNull(row)
        var hash = SEED
        for (col in cols) {
            val raw = rowModel?.cellAt(col)
            // Empty cells hash like missing ones, so rows that differ only in formatting-only
            // cells are not treated as different rows during alignment.
            val cell = if (ignoreEmptyChanges && raw != null && raw.isEmpty) null else raw
            hash = mix(hash, cell?.kind?.ordinal ?: -1)
            hash = mix(hash, cell?.text?.hashCode() ?: 0)
        }
        return hash
    }

    private fun isEmptyCell(cell: CellModel?): Boolean = cell == null || cell.isEmpty

    private fun cellsEqual(left: CellModel?, right: CellModel?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        return left.kind == right.kind && left.text == right.text
    }

    // ------------------------------------------------------------------ alignment

    /**
     * Aligns two sequences with a patience diff: unique common values are used as anchors,
     * everything between anchors is paired positionally (so edits look like modifications
     * rather than a delete + insert).
     */
    private fun align(left: List<Long>, right: List<Long>): List<Pair<Int?, Int?>> {
        val result = ArrayList<Pair<Int?, Int?>>(maxOf(left.size, right.size))
        val work = ArrayDeque<Item>()
        work.addLast(Item.Segment(0, left.size, 0, right.size))
        while (work.isNotEmpty()) {
            when (val item = work.removeFirst()) {
                is Item.Emit -> result.add(item.pair)
                is Item.Segment -> expand(item, left, right, work)
            }
        }
        return result
    }

    private fun expand(segment: Item.Segment, left: List<Long>, right: List<Long>, work: ArrayDeque<Item>) {
        var leftFrom = segment.leftFrom
        var leftTo = segment.leftTo
        var rightFrom = segment.rightFrom
        var rightTo = segment.rightTo

        val ops = ArrayList<Item>()
        while (leftFrom < leftTo && rightFrom < rightTo && left[leftFrom] == right[rightFrom]) {
            ops.add(Item.Emit(leftFrom to rightFrom))
            leftFrom++
            rightFrom++
        }

        val suffix = ArrayList<Item>()
        while (leftTo > leftFrom && rightTo > rightFrom && left[leftTo - 1] == right[rightTo - 1]) {
            leftTo--
            rightTo--
            suffix.add(Item.Emit(leftTo to rightTo))
        }

        val anchors = patienceAnchors(leftFrom, leftTo, rightFrom, rightTo, left, right)
        if (anchors.isEmpty()) {
            addPositional(leftFrom, leftTo, rightFrom, rightTo, ops)
        } else {
            var previousLeft = leftFrom
            var previousRight = rightFrom
            for ((anchorLeft, anchorRight) in anchors) {
                if (previousLeft < anchorLeft || previousRight < anchorRight) {
                    ops.add(Item.Segment(previousLeft, anchorLeft, previousRight, anchorRight))
                }
                ops.add(Item.Emit(anchorLeft to anchorRight))
                previousLeft = anchorLeft + 1
                previousRight = anchorRight + 1
            }
            if (previousLeft < leftTo || previousRight < rightTo) {
                ops.add(Item.Segment(previousLeft, leftTo, previousRight, rightTo))
            }
        }
        for (index in suffix.indices.reversed()) ops.add(suffix[index])
        for (index in ops.indices.reversed()) work.addFirst(ops[index])
    }

    private fun addPositional(
        leftFrom: Int,
        leftTo: Int,
        rightFrom: Int,
        rightTo: Int,
        ops: MutableList<Item>,
    ) {
        val leftSize = leftTo - leftFrom
        val rightSize = rightTo - rightFrom
        val common = minOf(leftSize, rightSize)
        for (index in 0 until common) ops.add(Item.Emit((leftFrom + index) to (rightFrom + index)))
        if (leftSize > rightSize) {
            for (index in common until leftSize) ops.add(Item.Emit((leftFrom + index) to null))
        } else if (rightSize > leftSize) {
            for (index in common until rightSize) ops.add(Item.Emit(null to (rightFrom + index)))
        }
    }

    private fun patienceAnchors(
        leftFrom: Int,
        leftTo: Int,
        rightFrom: Int,
        rightTo: Int,
        left: List<Long>,
        right: List<Long>,
    ): List<Pair<Int, Int>> {
        if (leftFrom >= leftTo || rightFrom >= rightTo) return emptyList()

        val leftCount = HashMap<Long, Int>(leftTo - leftFrom)
        for (index in leftFrom until leftTo) leftCount.merge(left[index], 1, Int::plus)
        val rightCount = HashMap<Long, Int>(rightTo - rightFrom)
        for (index in rightFrom until rightTo) rightCount.merge(right[index], 1, Int::plus)

        val leftUnique = ArrayList<Long>(leftCount.size)
        val leftIndex = HashMap<Long, Int>(leftCount.size)
        for (index in leftFrom until leftTo) {
            val key = left[index]
            if (leftCount[key] == 1) {
                leftUnique.add(key)
                leftIndex[key] = index
            }
        }
        if (leftUnique.isEmpty()) return emptyList()

        val candidates = ArrayList<Pair<Int, Int>>()
        for (index in rightFrom until rightTo) {
            val key = right[index]
            if (rightCount[key] != 1) continue
            val leftPosition = leftIndex[key] ?: continue
            candidates.add(leftPosition to index)
        }
        if (candidates.isEmpty()) return emptyList()
        return longestIncreasing(candidates)
    }

    /** Longest strictly increasing subsequence of the left indexes, keeping right order. */
    private fun longestIncreasing(candidates: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val tails = IntArray(candidates.size)
        val previous = IntArray(candidates.size) { -1 }
        var size = 0
        for (index in candidates.indices) {
            val value = candidates[index].first
            var low = 0
            var high = size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (candidates[tails[middle]].first < value) low = middle + 1 else high = middle
            }
            if (low > 0) previous[index] = tails[low - 1]
            tails[low] = index
            if (low == size) size++
        }
        val result = ArrayList<Pair<Int, Int>>(size)
        var cursor = tails[size - 1]
        while (cursor >= 0) {
            result.add(candidates[cursor])
            cursor = previous[cursor]
        }
        result.reverse()
        return result
    }

    private sealed interface Item {
        data class Emit(val pair: Pair<Int?, Int?>) : Item

        data class Segment(val leftFrom: Int, val leftTo: Int, val rightFrom: Int, val rightTo: Int) : Item
    }
}
