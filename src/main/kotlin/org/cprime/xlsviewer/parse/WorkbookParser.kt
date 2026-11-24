package org.cprime.xlsviewer.parse

import org.apache.poi.EncryptedDocumentException
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException
import org.apache.poi.poifs.filesystem.FileMagic
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Date1904Support
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.SheetVisibility
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.cprime.xlsviewer.model.CellKind
import org.cprime.xlsviewer.model.CellModel
import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.RowModel
import org.cprime.xlsviewer.model.SheetModel
import org.cprime.xlsviewer.model.StyleTable
import org.cprime.xlsviewer.model.XlsDocument
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.max

open class WorkbookParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class WorkbookEncryptedException(message: String, cause: Throwable? = null) : WorkbookParseException(message, cause)

class NotAnExcelFileException(message: String, cause: Throwable? = null) : WorkbookParseException(message, cause)

/** Workbook container formats we can read. The format is detected from the content, not the name. */
internal enum class WorkbookFormat { XLS, XLSX }

/**
 * Reads Excel workbooks into an immutable, UI-friendly model.
 *
 * Legacy `.xls` (BIFF8, OLE2) files are read with HSSF, `.xlsx` (OOXML) files with XSSF.
 * The whole workbook is converted eagerly on the calling thread, which is expected to be
 * a background thread. POI resources are closed before this method returns.
 */
object WorkbookParser {
    private const val MAX_ROWS_XLS = 65_536
    private const val MAX_COLS_XLS = 256
    private const val MAX_ROWS_XLSX = 1_048_576
    private const val MAX_COLS_XLSX = 16_384
    private const val MIN_ROWS = 50
    private const val MIN_COLS = 12

    /**
     * A workbook with more materialised cells than this is truncated. `.xlsx` files may
     * legitimately contain millions of empty-but-styled cells; keeping them all would
     * exhaust the heap of the IDE.
     */
    private const val MAX_CELLS = 5_000_000

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
        if (bytes.isEmpty()) throw WorkbookParseException("The file is empty")

        LfsPointer.detect(bytes)?.let { throw it }

        val format = detectFormat(bytes)
        val workbook = openWorkbook(bytes, format)
        return workbook.use { parseWorkbook(it, format, locale, onProgress) }
    }

    /**
     * True when the bytes start like an OLE2 (BIFF8) or OOXML (ZIP) container. Used by the
     * diff tool to reject sides that cannot be a workbook at all; the actual format
     * validation happens in [open].
     */
    fun isWorkbookContent(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val magic = runCatching { FileMagic.valueOf(bytes) }.getOrNull() ?: return false
        return magic == FileMagic.OLE2 || magic == FileMagic.OOXML
    }

    private fun detectFormat(bytes: ByteArray): WorkbookFormat {
        val magic = runCatching { FileMagic.valueOf(bytes) }
            .getOrElse { throw WorkbookParseException("Cannot read the file: ${it.message ?: it.javaClass.simpleName}", it) }
        return when (magic) {
            FileMagic.OLE2 -> WorkbookFormat.XLS
            FileMagic.OOXML -> WorkbookFormat.XLSX
            else -> throw NotAnExcelFileException("The content is not an Excel workbook")
        }
    }

    private fun openWorkbook(bytes: ByteArray, format: WorkbookFormat): Workbook = when (format) {
        WorkbookFormat.XLS -> openHssf(bytes)
        WorkbookFormat.XLSX -> openXssf(bytes)
    }

    private fun openHssf(bytes: ByteArray): Workbook {
        try {
            return HSSFWorkbook(ByteArrayInputStream(bytes))
        } catch (e: EncryptedDocumentException) {
            throw WorkbookEncryptedException("The workbook is password protected", e)
        } catch (e: Throwable) {
            when (ole2Kind(bytes)) {
                // Excel stores encrypted OOXML workbooks as an OLE2 container with an
                // EncryptedPackage stream; without this check the user would see a
                // misleading "not an Excel file" message.
                Ole2Kind.ENCRYPTED -> throw WorkbookEncryptedException("The workbook is password protected", e)
                Ole2Kind.NOT_A_WORKBOOK -> throw NotAnExcelFileException("The content is not an Excel workbook", e)
                Ole2Kind.WORKBOOK -> throw WorkbookParseException(
                    "The workbook cannot be parsed: ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
        }
    }

    private fun openXssf(bytes: ByteArray): Workbook {
        try {
            return XSSFWorkbook(ByteArrayInputStream(bytes))
        } catch (e: EncryptedDocumentException) {
            throw WorkbookEncryptedException("The workbook is password protected", e)
        } catch (e: NotOfficeXmlFileException) {
            // An OLE2 document renamed to .xlsx.
            throw NotAnExcelFileException("The content is not an Excel workbook", e)
        } catch (e: Throwable) {
            // A plain ZIP (or another OPC package) without the OOXML content-types part.
            if (!hasContentTypesPart(bytes)) {
                throw NotAnExcelFileException("The content is not an Excel workbook", e)
            }
            throw WorkbookParseException(
                "The workbook cannot be parsed: ${e.message ?: e.javaClass.simpleName}",
                e,
            )
        }
    }

    private enum class Ole2Kind { WORKBOOK, ENCRYPTED, NOT_A_WORKBOOK }

    /** An OOXML package always starts with the `[Content_Types].xml` part. */
    private fun hasContentTypesPart(bytes: ByteArray): Boolean = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "[Content_Types].xml") return@use true
                entry = zip.nextEntry
            }
            false
        }
    }.getOrDefault(false)

    private fun ole2Kind(bytes: ByteArray): Ole2Kind = runCatching {
        POIFSFileSystem(ByteArrayInputStream(bytes)).use { fileSystem ->
            val root = fileSystem.root
            when {
                root.hasEntry("EncryptedPackage") -> Ole2Kind.ENCRYPTED
                root.hasEntry("Workbook") || root.hasEntry("Book") -> Ole2Kind.WORKBOOK
                else -> Ole2Kind.NOT_A_WORKBOOK
            }
        }
    }.getOrDefault(Ole2Kind.NOT_A_WORKBOOK)

    private fun parseWorkbook(
        workbook: Workbook,
        format: WorkbookFormat,
        locale: Locale,
        onProgress: ((String) -> Unit)?,
    ): XlsDocument {
        val colors: StyleColors = when (workbook) {
            is HSSFWorkbook -> HssfStyleColors(workbook)
            is XSSFWorkbook -> XssfStyleColors(workbook)
            else -> NoStyleColors
        }
        val styles = StyleTableBuilder(workbook, colors).build()
        val formatter = CellTextFormatter(
            locale = locale,
            date1904 = uses1904Dates(workbook),
        )

        val sheets = ArrayList<SheetModel>()
        val warnings = ArrayList<String>()
        val budget = CellBudget(MAX_CELLS)
        var truncationReported = false
        val sheetCount = runCatching { workbook.numberOfSheets }.getOrDefault(0)

        for (index in 0 until sheetCount) {
            if (budget.exhausted) break
            val sheet = runCatching { workbook.getSheetAt(index) }.getOrNull()
            if (sheet == null) {
                warnings += "Sheet #${index + 1} cannot be read"
                continue
            }
            onProgress?.invoke(runCatching { sheet.sheetName }.getOrDefault("#${index + 1}"))
            val name = runCatching { sheet.sheetName }.getOrDefault("#${index + 1}")
            runCatching { parseSheet(workbook, sheet, index, format, styles, formatter, colors, budget) }
                .onFailure { warnings += "Sheet \"$name\": ${it.message ?: it.javaClass.simpleName}" }
                .getOrNull()
                ?.let { sheets += it }
            if (budget.exhausted && !truncationReported) {
                warnings += "The workbook is too large: only the first $MAX_CELLS cells are shown"
                truncationReported = true
            }
        }

        return XlsDocument(sheets, warnings)
    }

    private fun parseSheet(
        workbook: Workbook,
        sheet: Sheet,
        index: Int,
        format: WorkbookFormat,
        styles: StyleTable,
        formatter: CellTextFormatter,
        colors: StyleColors,
        budget: CellBudget,
    ): SheetModel {
        val maxRows = if (format == WorkbookFormat.XLS) MAX_ROWS_XLS else MAX_ROWS_XLSX
        val maxCols = if (format == WorkbookFormat.XLS) MAX_COLS_XLS else MAX_COLS_XLSX

        val merged = runCatching { sheet.mergedRegions }.getOrDefault(emptyList())
            .map { CellRange(it.firstRow, it.firstColumn, it.lastRow, it.lastColumn) }

        val rawRows = ArrayList<Row>()
        runCatching { for (row in sheet) rawRows.add(row) }

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
        if (maxRowIndex >= maxRows) maxRowIndex = maxRows - 1

        val rowCount = (maxRowIndex + 1).coerceAtLeast(MIN_ROWS).coerceAtMost(maxRows)
        val colCount = maxColExclusive.coerceAtLeast(MIN_COLS).coerceAtMost(maxCols)

        val defaultRowHeightPx = toPx(runCatching { sheet.defaultRowHeightInPoints }.getOrDefault(12.75f))
        val rowHeights = FloatArray(rowCount) { defaultRowHeightPx }
        val hiddenRows = BooleanArray(rowCount)
        val rows = arrayOfNulls<RowModel>(rowCount)

        rows@ for (row in rawRows) {
            if (budget.exhausted) break@rows
            val rowIndex = row.rowNum
            if (rowIndex !in 0 until rowCount) continue

            val hidden = runCatching { row.zeroHeight }.getOrDefault(false)
            hiddenRows[rowIndex] = hidden
            val heightPx = toPx(runCatching { row.heightInPoints }.getOrDefault(defaultRowHeightPx / PX_PER_POINT))
            rowHeights[rowIndex] = heightPx

            val rowCellCount = runCatching { row.lastCellNum.toInt() }.getOrDefault(0).coerceIn(0, colCount)
            val cells = arrayOfNulls<CellModel>(rowCellCount)
            var hasCells = false
            for (cell in row) {
                if (budget.exhausted) break@rows
                val col = cell.columnIndex
                if (col !in 0 until colCount || col >= cells.size) continue
                val model = convertCell(cell, formatter, rowIndex, col) ?: continue
                cells[col] = model
                hasCells = true
                budget.consume()
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

        return SheetModel(
            index = index,
            name = runCatching { sheet.sheetName }.getOrDefault("#${index + 1}"),
            hidden = runCatching {
                workbook.getSheetVisibility(index) != SheetVisibility.VISIBLE
            }.getOrDefault(false),
            tabColor = runCatching { colors.tab(sheet) }.getOrNull(),
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

    private fun uses1904Dates(workbook: Workbook): Boolean = when (workbook) {
        is Date1904Support -> runCatching { workbook.isDate1904() }.getOrDefault(false)
        // HSSF does not implement Date1904Support; BIFF8 has the same workbook flag.
        is HSSFWorkbook -> runCatching { workbook.workbook.isUsing1904DateWindowing }.getOrDefault(false)
        else -> false
    }

    private fun convertCell(
        cell: Cell,
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

    private fun columnWidthPx(sheet: Sheet, col: Int): Float {
        val widthIn256 = runCatching { sheet.getColumnWidth(col) }.getOrDefault(2048)
        val characters = widthIn256 / 256.0
        val width = characters * CHAR_WIDTH_PX + CELL_PADDING_PX
        return width.toFloat().coerceIn(MIN_COL_WIDTH_PX, MAX_COL_WIDTH_PX)
    }

    private fun toPx(points: Float): Float = (points * PX_PER_POINT).coerceAtLeast(1f)

    private class CellBudget(private val limit: Int) {
        private var remaining = limit
        val exhausted: Boolean get() = remaining <= 0

        fun consume() {
            if (remaining > 0) remaining--
        }
    }
}
