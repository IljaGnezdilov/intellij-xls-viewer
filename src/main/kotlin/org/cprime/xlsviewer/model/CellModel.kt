package org.cprime.xlsviewer.model

enum class CellKind { BLANK, TEXT, NUMBER, DATE, BOOLEAN, ERROR }

/**
 * A single cell. [text] is the value exactly as Excel would display it
 * (number formats, dates, currency, percentages... are already applied).
 * Cells that exist only because of formatting are kept: [isEmpty] tells them apart
 * from cells that actually show a value.
 */
class CellModel(
    val row: Int,
    val col: Int,
    val kind: CellKind,
    val text: String,
    val styleId: Int,
    val formula: String? = null,
) {
    val hasFormula: Boolean get() = formula != null

    /** True when the cell shows no value: blank or a whitespace-only text. */
    val isEmpty: Boolean get() = kind == CellKind.BLANK || text.isBlank()

    override fun toString(): String = "CellModel($row,$col,$kind,'$text')"
}

/**
 * One physical row of a sheet. Rows without any cells are represented by `null`
 * in [SheetModel.rows] to keep sparse workbooks cheap.
 *
 * [cells] is trimmed to the last non-empty column of this row: an `.xlsx` sheet may be
 * 16 384 columns wide, and allocating that many slots for every row would exhaust the heap.
 */
class RowModel(
    val index: Int,
    val heightPx: Float,
    val hidden: Boolean,
    cells: Array<CellModel?>,
) {
    val cells: Array<CellModel?> = trimTrailingEmpty(cells)

    val isEmpty: Boolean get() = cells.isEmpty()

    fun cellAt(col: Int): CellModel? = cells.getOrNull(col)

    private fun trimTrailingEmpty(cells: Array<CellModel?>): Array<CellModel?> {
        var last = cells.size - 1
        while (last >= 0 && cells[last] == null) last--
        return when {
            last < 0 -> EMPTY
            last == cells.size - 1 -> cells
            else -> cells.copyOf(last + 1)
        }
    }

    private companion object {
        val EMPTY = emptyArray<CellModel?>()
    }
}
