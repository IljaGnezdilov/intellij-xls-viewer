package org.cprime.xlsviewer.parse

import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.SheetVisibility
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.cprime.xlsviewer.model.BorderKind
import org.cprime.xlsviewer.model.CellKind
import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.HAlign
import org.cprime.xlsviewer.model.VAlign
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

class XlsxParserTest {

    @Test
    fun `reads values, kinds and number formats`() {
        val sheet = WorkbookParser.open(buildWorkbook(), Locale.US).sheets.single { it.index == 0 }

        val text = sheet.cell(0, 0)!!
        assertEquals(CellKind.TEXT, text.kind)
        assertEquals("Привет", text.text)

        val number = sheet.cell(0, 1)!!
        assertEquals(CellKind.NUMBER, number.kind)
        assertEquals("42.00", number.text)

        val date = sheet.cell(0, 2)!!
        assertEquals(CellKind.DATE, date.kind)
        assertEquals("17.11.2025", date.text)

        val formula = sheet.cell(0, 3)!!
        assertEquals(CellKind.NUMBER, formula.kind)
        assertEquals("84", formula.text)
        assertEquals("B1*2", formula.formula)

        val bool = sheet.cell(0, 4)!!
        assertEquals(CellKind.BOOLEAN, bool.kind)
        assertEquals("TRUE", bool.text)

        val error = sheet.cell(0, 5)!!
        assertEquals(CellKind.ERROR, error.kind)
        assertEquals("#DIV/0!", error.text)
    }

    @Test
    fun `reads cell formatting`() {
        val sheet = WorkbookParser.open(buildWorkbook(), Locale.US).sheets.single { it.index == 0 }
        val cell = sheet.cell(0, 0)!!
        val style = sheet.styles[cell.styleId]

        assertTrue(style.font.bold)
        assertEquals(14f, style.font.sizePt)
        assertEquals(0xFFFF0000.toInt(), style.font.color)
        assertEquals(HAlign.CENTER, style.hAlign)
        assertEquals(VAlign.CENTER, style.vAlign)
        assertEquals(0xFFFFFF00.toInt(), style.fillColor)
        assertEquals(BorderKind.THIN, style.borders.bottom.kind)
        assertEquals(0xFF0000FF.toInt(), style.borders.bottom.color)
    }

    @Test
    fun `applies tint and resolves indexed colors`() {
        val sheet = WorkbookParser.open(buildWorkbook(), Locale.US).sheets.single { it.index == 0 }

        // 0x20C040 with a +40% tint, using the same formula as Excel.
        val tinted = sheet.cell(1, 1)!!
        assertEquals(0xFF79D98C.toInt(), sheet.styles[tinted.styleId].fillColor)

        val indexed = sheet.cell(1, 2)!!
        assertEquals(0xFFC0C0C0.toInt(), sheet.styles[indexed.styleId].fillColor)
    }

    @Test
    fun `reads geometry, merges and frozen panes`() {
        val sheet = WorkbookParser.open(buildWorkbook(), Locale.US).sheets.single { it.index == 0 }

        assertEquals("Данные", sheet.name)
        assertEquals(1, sheet.freezeRows)
        assertEquals(2, sheet.freezeCols)
        assertTrue(sheet.isHidden(7))
        assertTrue(sheet.isHiddenColumn(5))
        assertFalse(sheet.showGridlines)

        assertEquals(40f, sheet.rowHeights[0], 0.5f)
        assertTrue(abs(sheet.colWidths[1] - 215f) < 2f, "unexpected width ${sheet.colWidths[1]}")

        assertEquals(CellRange(2, 0, 2, 2), sheet.regionAt(2, 1))
        assertEquals(CellRange(2, 0, 2, 2), sheet.anchorOf(2, 2))
        assertNull(sheet.regionAt(3, 1))
    }

    @Test
    fun `reads hidden sheets and tab colors`() {
        val document = WorkbookParser.open(buildWorkbook(), Locale.US)
        assertEquals(3, document.sheets.size)
        assertTrue(document.sheets[2].hidden)
        assertEquals(0xFF00FF00.toInt(), document.sheets[0].tabColor)
    }

    @Test
    fun `honours the 1904 date system`() {
        val sheet = WorkbookParser.open(buildDate1904Workbook(), Locale.US).sheets.single()
        assertEquals(CellKind.DATE, sheet.cell(0, 0)!!.kind)
        assertEquals("17.11.2025", sheet.cell(0, 0)!!.text)
    }

    @Test
    fun `rejects a plain zip package`() {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("hello.txt"))
            zip.write("not a workbook".toByteArray())
            zip.closeEntry()
        }
        assertThrows(NotAnExcelFileException::class.java) {
            WorkbookParser.open(archive.toByteArray())
        }
    }

    @Test
    fun `handles workbooks without sheets`() {
        val buffer = ByteArrayOutputStream()
        XSSFWorkbook().use { it.write(buffer) }
        val document = WorkbookParser.open(buffer.toByteArray())
        assertTrue(document.sheets.isEmpty())
    }

    @Test
    fun `reports encrypted ooxml containers as password protected`() {
        val container = POIFSFileSystem()
        container.root.createDocument("EncryptionInfo", ByteArrayInputStream(byteArrayOf(4, 0, 4, 0, 0x40, 0, 0, 0)))
        container.root.createDocument("EncryptedPackage", ByteArrayInputStream(ByteArray(32)))
        val buffer = ByteArrayOutputStream()
        container.writeFilesystem(buffer)
        container.close()

        assertThrows(WorkbookEncryptedException::class.java) {
            WorkbookParser.open(buffer.toByteArray())
        }
    }

    private fun buildWorkbook(): ByteArray {
        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Данные")
            val header = sheet.createRow(0)
            header.heightInPoints = 30f

            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 14
                setColor(XSSFColor(byteArrayOf(0xFF.toByte(), 0x00, 0x00)))
            }
            val titleStyle = workbook.createCellStyle().apply {
                setFont(font)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                setFillForegroundColor(XSSFColor(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00)))
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setBorderBottom(BorderStyle.THIN)
                setBottomBorderColor(XSSFColor(byteArrayOf(0x00, 0x00, 0xFF.toByte())))
            }

            val dateStyle = workbook.createCellStyle().apply {
                dataFormat = workbook.createDataFormat().getFormat("dd.MM.yyyy")
            }
            val numberStyle = workbook.createCellStyle().apply {
                dataFormat = workbook.createDataFormat().getFormat("0.00")
            }

            header.createCell(0).apply {
                setCellValue("Привет")
                cellStyle = titleStyle
            }
            header.createCell(1).apply {
                setCellValue(42.0)
                cellStyle = numberStyle
            }
            header.createCell(2).apply {
                setCellValue(noonOf(2025, 11, 17))
                cellStyle = dateStyle
            }
            val formulaCell = header.createCell(3).apply { cellFormula = "B1*2" }
            workbook.creationHelper.createFormulaEvaluator().evaluateFormulaCell(formulaCell)
            header.createCell(4).setCellValue(true)
            header.createCell(5).setCellErrorValue(FormulaError.DIV0.code)

            val tintedStyle = workbook.createCellStyle().apply {
                setFillForegroundColor(XSSFColor(byteArrayOf(0x20, 0xC0.toByte(), 0x40)).apply { tint = 0.4 })
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }
            sheet.createRow(1).apply {
                createCell(1).apply {
                    setCellValue("tint")
                    cellStyle = tintedStyle
                }
                createCell(2).apply {
                    setCellValue("indexed")
                    cellStyle = workbook.createCellStyle().apply {
                        fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                        fillPattern = FillPatternType.SOLID_FOREGROUND
                    }
                }
            }

            val mergedRow = sheet.createRow(2)
            mergedRow.createCell(0).setCellValue("Объединённая ячейка")
            sheet.addMergedRegion(CellRangeAddress(2, 2, 0, 2))

            sheet.setColumnWidth(1, 30 * 256)
            sheet.setColumnHidden(5, true)
            sheet.createRow(7).zeroHeight = true
            sheet.createFreezePane(2, 1)
            sheet.isDisplayGridlines = false
            sheet.setTabColor(XSSFColor(byteArrayOf(0x00, 0xFF.toByte(), 0x00)))

            val notes = workbook.createSheet("Примечания")
            notes.createRow(0).createCell(0).setCellValue("Лист с пояснениями")
            val hidden = workbook.createSheet("Скрытый лист")
            hidden.createRow(0).createCell(0).setCellValue("Этот лист скрыт")
            workbook.setSheetVisibility(2, SheetVisibility.HIDDEN)

            return write(workbook)
        } finally {
            workbook.close()
        }
    }

    private fun buildDate1904Workbook(): ByteArray {
        val workbook = XSSFWorkbook()
        try {
            val workbookPr = if (workbook.ctWorkbook.isSetWorkbookPr) {
                workbook.ctWorkbook.workbookPr
            } else {
                workbook.ctWorkbook.addNewWorkbookPr()
            }
            workbookPr.date1904 = true

            val sheet = workbook.createSheet("1904")
            val style = workbook.createCellStyle().apply {
                dataFormat = workbook.createDataFormat().getFormat("dd.MM.yyyy")
            }
            sheet.createRow(0).createCell(0).apply {
                setCellValue(DateUtil.getExcelDate(noonOf(2025, 11, 17), true))
                cellStyle = style
            }
            return write(workbook)
        } finally {
            workbook.close()
        }
    }

    private fun write(workbook: XSSFWorkbook): ByteArray {
        val buffer = ByteArrayOutputStream()
        workbook.write(buffer)
        return buffer.toByteArray()
    }

    private fun noonOf(year: Int, month: Int, day: Int): Date =
        Date.from(
            LocalDate.of(year, month, day)
                .atTime(LocalTime.NOON)
                .atZone(ZoneId.systemDefault())
                .toInstant(),
        )
}
