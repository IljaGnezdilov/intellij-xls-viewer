package org.cprime.xlsviewer.parse

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FormulaError
import org.cprime.xlsviewer.model.CellKind
import java.util.Locale

/**
 * Turns raw cell values into the text Excel would show, honouring number formats,
 * dates, currencies and cached formula results.
 */
internal class CellTextFormatter(
    private val locale: Locale,
    /** OOXML workbooks may use the 1904 date system; BIFF8 almost always uses 1900. */
    private val date1904: Boolean = false,
) {
    private val formatter = DataFormatter(locale)

    fun kind(cell: Cell): CellKind {
        val type = effectiveType(cell)
        return when (type) {
            CellType.BLANK, CellType._NONE -> CellKind.BLANK
            CellType.STRING -> CellKind.TEXT
            CellType.BOOLEAN -> CellKind.BOOLEAN
            CellType.ERROR -> CellKind.ERROR
            CellType.NUMERIC -> if (isDate(cell)) CellKind.DATE else CellKind.NUMBER
            CellType.FORMULA -> if (isDate(cell)) CellKind.DATE else CellKind.NUMBER
        }
    }

    fun text(cell: Cell): String = when (kind(cell)) {
        CellKind.BLANK -> ""
        CellKind.TEXT -> stringValue(cell)
        CellKind.BOOLEAN -> if (runCatching { cell.booleanCellValue }.getOrDefault(false)) "TRUE" else "FALSE"
        CellKind.ERROR -> errorText(cell)
        CellKind.NUMBER, CellKind.DATE -> numberText(cell)
    }

    private fun effectiveType(cell: Cell): CellType =
        if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType

    private fun isDate(cell: Cell): Boolean =
        runCatching { DateUtil.isCellDateFormatted(cell) }.getOrDefault(false)

    private fun stringValue(cell: Cell): String {
        runCatching { cell.richStringCellValue?.string }.getOrNull()?.let { return it }
        return runCatching { cell.stringCellValue }.getOrDefault("")
    }

    private fun numberText(cell: Cell): String {
        val value = runCatching { cell.numericCellValue }.getOrElse { return "" }
        val style = cell.cellStyle
        val formatIndex = runCatching { style.dataFormat.toInt() }.getOrDefault(0)
        val formatString = runCatching { style.dataFormatString }.getOrNull() ?: "General"
        return runCatching {
            formatter.formatRawCellContents(value, formatIndex, formatString, date1904)
        }.getOrElse { defaultNumber(value) }
    }

    private fun defaultNumber(value: Double): String {
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        return java.text.NumberFormat.getInstance(locale).format(value)
    }

    private fun errorText(cell: Cell): String {
        val code = runCatching { cell.errorCellValue.toInt() }.getOrDefault(0)
        return runCatching { FormulaError.forInt(code.toByte()).string }.getOrDefault("#ERROR")
    }
}
