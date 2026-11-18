package org.cprime.xlsviewer.parse

import org.apache.poi.EncryptedDocumentException
import org.apache.poi.hssf.usermodel.HSSFCell
import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.poifs.filesystem.FileMagic
import org.apache.poi.ss.usermodel.CellType
import org.cprime.xlsviewer.model.CellKind
import org.cprime.xlsviewer.model.CellModel
import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.RowModel
import org.cprime.xlsviewer.model.SheetModel
import org.cprime.xlsviewer.model.StyleTable
import org.cprime.xlsviewer.model.XlsDocument
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.max

open class XlsParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class XlsEncryptedException(message: String, cause: Throwable? = null) : XlsParseException(message, cause)

class NotAnXlsFileException(message: String, cause: Throwable? = null) : XlsParseException(message, cause)

/**
 * Reads BIFF8 (Excel 97-2003) workbooks into an immutable, UI-friendly model.
 *
 * The whole workbook is converted eagerly on the calling thread, which is expected to be
 * a background thread. POI resources are closed before this method returns.
 */
object XlsParser {
    private const val MAX_ROWS = 65_536
    private const val MAX_COLS = 256
    private const val MIN_ROWS = 50
    private const val MIN_COLS = 12
    private const val PX_PER_POINT = 96f / 72f
    private const val CHAR_WIDTH_PX = 7.0
    private const val CELL_PADDING_PX = 5.0
    private const val MIN_COL_WIDTH_PX = 8f
    private const val MAX_COL_WIDTH_PX = 3_000f

    fun open(
        bytes: ByteArray,
        locale: Locale = Locale.getDefault(),
        onProgress: ((String) -> Unit)? = null,
    ): XlsDocument {
        if (bytes.isEmpty()) throw XlsParseException("The file is empty")

        LfsPointer.detect(bytes)?.let { throw it }

        val magic = runCatching { FileMagic.valueOf(bytes) }
            .getOrElse { throw XlsParseException("Cannot read the file: ${it.message ?: it.javaClass.simpleName}", it) }
        if (magic != FileMagic.OLE2) {
            throw NotAnXlsFileException("The content is not an Excel 97-2003 (BIFF8) workbook")
        }

        val workbook = try {
            HSSFWorkbook(ByteArrayInputStream(bytes))
        } catch (e: EncryptedDocumentException) {
            throw XlsEncryptedException("The workbook is password protected", e)
        } catch (e: Throwable) {
            throw XlsParseException("The workbook cannot be parsed: ${e.message ?: e.javaClass.simpleName}", e)
        }

        return workbook.use { parseWorkbook(it, locale, onProgress) }
    }

    private fun parseWorkbook(
        workbook: HSSFWorkbook,
        locale: Locale,
        onProgress: ((String) -> Unit)?,
    ): XlsDocument {
        val palette = ColorPalette(workbook)
        val styles = StyleTableBuilder(workbook, palette).build()
        val formatter = CellTextFormatter(locale)

        val sheets = ArrayList<SheetModel>()
        val warnings = ArrayList<String>()
        val sheetCount = runCatching { workbook.numberOfSheets }.getOrDefault(0)

        for (index in 0 until sheetCount) {
            val sheet = runCatching { workbook.getSheetAt(index) }.getOrNull()
            if (sheet == null) {
                warnings += "Sheet #${index + 1} cannot be read"
                continue
            }
            onProgress?.invoke(sheet.sheetName)
            val name = runCatching { sheet.sheetName }.getOrDefault("#${index + 1}")
            runCatching { parseSheet(workbook, sheet, index, styles, formatter) }
                .onFailure { warnings += "Sheet \"$name\": ${it.message ?: it.javaClass.simpleName}" }
                .getOrNull()
                ?.let { sheets += it }
        }

        return XlsDocument(sheets, warnings)
    }

    private fun parseSheet(
        workbook: HSSFWorkbook,
        sheet: HSSFSheet,
        index: Int,
        styles: StyleTable,
        formatter: CellTextFormatter,
    ): SheetModel {
        val merged = runCatching { sheet.mergedRegions }.getOrDefault(emptyList())
            .map { CellRange(it.firstRow, it.firstColumn, it.lastRow, it.lastColumn) }

        val rawRows = ArrayList<HSSFRow>()
        runCatching { for (row in sheet) rawRows.add(row as HSSFRow) }

        var maxRowIndex = -1
        var maxColExclusive = 0
        for (row in rawRows) {
            if (row.rowNum > maxRowIndex) maxRowIndex = row.rowNum
            val lastCell = runCatching { row.lastCellNum.toInt() }.getOrDefault(0)
            if (lastCell > maxColExclusive) maxColExclusive = lastCell
        }
        for (region in merged) {
            if (region.endRow > maxRowIndex) maxRowIndex = region.endRow
            if (region.endCol + 1 > maxColExclusive) maxColExclusive = region.endCol + 1
        }
        maxRowIndex = max(maxRowIndex, runCatching { sheet.lastRowNum }.getOrDefault(-1))
        if (maxRowIndex >= MAX_ROWS) maxRowIndex = MAX_ROWS - 1

        val rowCount = (maxRowIndex + 1).coerceAtLeast(MIN_ROWS).coerceAtMost(MAX_ROWS)
        val colCount = maxColExclusive.coerceAtLeast(MIN_COLS).coerceAtMost(MAX_COLS)

        val defaultRowHeightPx = toPx(runCatching { sheet.defaultRowHeightInPoints }.getOrDefault(12.75f))
        val rowHeights = FloatArray(rowCount) { defaultRowHeightPx }
        val hiddenRows = BooleanArray(rowCount)
        val rows = arrayOfNulls<RowModel>(rowCount)

        for (row in rawRows) {
            val rowIndex = row.rowNum
            if (rowIndex !in 0 until rowCount) continue

            val hidden = runCatching { row.zeroHeight }.getOrDefault(false)
            hiddenRows[rowIndex] = hidden
            val heightPx = toPx(runCatching { row.heightInPoints }.getOrDefault(defaultRowHeightPx / PX_PER_POINT))
            rowHeights[rowIndex] = heightPx

            val cells = arrayOfNulls<CellModel>(colCount)
            var hasCells = false
            for (cell in row) {
                val hssfCell = cell as? HSSFCell ?: continue
                val col = hssfCell.columnIndex
                if (col !in 0 until colCount) continue
                val model = convertCell(hssfCell, formatter, rowIndex, col) ?: continue
                cells[col] = model
                hasCells = true
            }
            if (hasCells) {
                rows[rowIndex] = RowModel(rowIndex, heightPx, hidden, cells)
            }
        }

        val colWidths = FloatArray(colCount) { col -> columnWidthPx(sheet, col) }
        val hiddenCols = BooleanArray(colCount) { col ->
            runCatching { sheet.isColumnHidden(col) }.getOrDefault(false)
        }

        val pane = runCatching { sheet.paneInformation }.getOrNull()
        val frozen = pane != null && pane.isFreezePane
        // POI names these fields counter-intuitively: the "vertical" split is the column split.
        val freezeRows = if (frozen) pane.horizontalSplitPosition.toInt().coerceIn(0, rowCount) else 0
        val freezeCols = if (frozen) pane.verticalSplitPosition.toInt().coerceIn(0, colCount) else 0

        // Apache POI does not expose the BIFF tab color for HSSF sheets.
        val tabColor: Int? = null

        return SheetModel(
            index = index,
            name = runCatching { sheet.sheetName }.getOrDefault("#${index + 1}"),
            hidden = runCatching { workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index) }.getOrDefault(false),
            tabColor = tabColor,
            rowCount = rowCount,
            colCount = colCount,
            rows = rows,
            rowHeights = rowHeights,
            colWidths = colWidths,
            hiddenRows = hiddenRows,
            hiddenCols = hiddenCols,
            merged = merged,
            freezeRows = freezeRows,
            freezeCols = freezeCols,
            showGridlines = runCatching { sheet.isDisplayGridlines }.getOrDefault(true),
            rtl = runCatching { sheet.isRightToLeft }.getOrDefault(false),
            styles = styles,
            usedRows = (maxRowIndex + 1).coerceAtLeast(0),
            usedCols = maxColExclusive.coerceAtLeast(0),
        )
    }

    private fun convertCell(
        cell: HSSFCell,
        formatter: CellTextFormatter,
        row: Int,
        col: Int,
    ): CellModel? {
        val kind = runCatching { formatter.kind(cell) }.getOrDefault(CellKind.BLANK)
        val styleId = runCatching { cell.cellStyle.index.toInt() }.getOrDefault(0)
        if (kind == CellKind.BLANK && styleId == 0) return null
        val text = runCatching { formatter.text(cell) }.getOrDefault("")
        val formula = if (runCatching { cell.cellType }.getOrNull() == CellType.FORMULA) {
            runCatching { cell.cellFormula }.getOrNull()
        } else {
            null
        }
        return CellModel(row, col, kind, text, styleId, formula)
    }

    private fun columnWidthPx(sheet: HSSFSheet, col: Int): Float {
        val widthIn256 = runCatching { sheet.getColumnWidth(col) }.getOrDefault(2048)
        val characters = widthIn256 / 256.0
        val width = characters * CHAR_WIDTH_PX + CELL_PADDING_PX
        return width.toFloat().coerceIn(MIN_COL_WIDTH_PX, MAX_COL_WIDTH_PX)
    }

    private fun toPx(points: Float): Float = (points * PX_PER_POINT).coerceAtLeast(1f)
}
