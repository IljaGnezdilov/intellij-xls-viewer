package org.cprime.xlsviewer.model

enum class CellKind { BLANK, TEXT, NUMBER, DATE, BOOLEAN, ERROR }

/**
 * A single non-empty cell. [text] is the value exactly as Excel would display it
 * (number formats, dates, currency, percentages... are already applied).
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

    override fun toString(): String = "CellModel($row,$col,$kind,'$text')"
}

/**
 * One physical row of a sheet. Rows without any cells are represented by `null`
 * in [SheetModel.rows] to keep sparse workbooks cheap.
 */
class RowModel(
    val index: Int,
    val heightPx: Float,
    val hidden: Boolean,
    val cells: Array<CellModel?>,
) {
    val isEmpty: Boolean get() = cells.all { it == null }
}
