package org.cprime.xlsviewer.model

/**
 * Parsed, immutable snapshot of one worksheet. The geometry of the sheet is stored in
 * "model pixels" at 100% zoom; the UI multiplies it by the current zoom factor.
 */
class SheetModel(
    val index: Int,
    val name: String,
    val hidden: Boolean,
    val tabColor: Int?,
    /** Number of rows to show, at least 1. */
    val rowCount: Int,
    /** Number of columns to show, at least 1. */
    val colCount: Int,
    /** Sparse row storage, size == [rowCount]. */
    val rows: Array<RowModel?>,
    /** Row heights in model pixels at 100% zoom, size == [rowCount]. */
    val rowHeights: FloatArray,
    /** Column widths in model pixels at 100% zoom, size == [colCount]. */
    val colWidths: FloatArray,
    val hiddenRows: BooleanArray,
    val hiddenCols: BooleanArray,
    val merged: List<CellRange>,
    val freezeRows: Int,
    val freezeCols: Int,
    val showGridlines: Boolean,
    val rtl: Boolean,
    val styles: StyleTable,
    /** Number of rows that actually contain data, used for the status bar and navigation. */
    val usedRows: Int,
    val usedCols: Int,
    /** Diff metadata, only present for synthetic models shown in the diff viewer. */
    val diff: SheetDiffMarks? = null,
) {
    /**
     * Merged regions indexed by row. Built lazily and skipped for pathological files
     * with enormous merged areas, in which case [regionAt] falls back to a linear scan.
     */
    private val regionsByRow: Map<Int, List<CellRange>>? by lazy {
        if (merged.isEmpty()) return@lazy emptyMap()
        var entries = 0L
        for (region in merged) {
            entries += region.rowCount
            if (entries > 200_000L) return@lazy null
        }
        val map = HashMap<Int, MutableList<CellRange>>()
        for (region in merged) {
            for (row in region.startRow..region.endRow) {
                map.getOrPut(row) { ArrayList(2) }.add(region)
            }
        }
        map
    }

    fun cell(row: Int, col: Int): CellModel? {
        if (row < 0 || row >= rowCount || col < 0 || col >= colCount) return null
        return rows[row]?.cells?.getOrNull(col)
    }

    /** Merged region containing the given cell, if any. */
    fun regionAt(row: Int, col: Int): CellRange? {
        if (merged.isEmpty()) return null
        val byRow = regionsByRow
        if (byRow != null) {
            val candidates = byRow[row] ?: return null
            for (candidate in candidates) {
                if (candidate.contains(row, col)) return candidate
            }
            return null
        }
        return merged.firstOrNull { it.contains(row, col) }
    }

    /** Converts a cell into the anchor of the merged region it belongs to (or the cell itself). */
    fun anchorOf(row: Int, col: Int): CellRange {
        val region = regionAt(row, col)
        if (region != null) return region
        return CellRange.single(row, col)
    }

    fun isHidden(row: Int): Boolean = row in hiddenRows.indices && hiddenRows[row]

    fun isHiddenColumn(col: Int): Boolean = col in hiddenCols.indices && hiddenCols[col]

    /** Column width used by the UI, honouring hidden columns. */
    fun widthOf(col: Int): Float = if (isHiddenColumn(col)) 0f else colWidths.getOrElse(col) { 64f }

    fun heightOf(row: Int): Float = if (isHidden(row)) 0f else rowHeights.getOrElse(row) { 17f }

    companion object {
        /** Placeholder used by UI components before a real sheet is attached. */
        fun empty(): SheetModel = SheetModel(
            index = 0,
            name = "",
            hidden = false,
            tabColor = null,
            rowCount = 1,
            colCount = 1,
            rows = arrayOfNulls(1),
            rowHeights = floatArrayOf(17f),
            colWidths = floatArrayOf(64f),
            hiddenRows = BooleanArray(1),
            hiddenCols = BooleanArray(1),
            merged = emptyList(),
            freezeRows = 0,
            freezeCols = 0,
            showGridlines = true,
            rtl = false,
            styles = StyleTable(emptyMap(), CellStyleModel(0, FontModel("Arial", 10f))),
            usedRows = 0,
            usedCols = 0,
        )
    }
}

/**
 * Parsed workbook. Sheet models are produced eagerly on a background thread, so this
 * object holds no native or POI resources and can be kept in memory freely.
 */
class XlsDocument(
    val sheets: List<SheetModel>,
    val warnings: List<String> = emptyList(),
) {
    val firstVisibleSheet: Int
        get() = sheets.indexOfFirst { !it.hidden }.let { if (it >= 0) it else 0 }

    companion object {
        val EMPTY = XlsDocument(emptyList())
    }
}
