package org.cprime.xlsviewer.parse

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
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
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

class WorkbookParserTest {

    @Test
    fun `reads values, kinds and number formats`() {
        val document = WorkbookParser.open(buildWorkbook(), Locale.US)
        val sheet = document.sheets.single()

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
        val sheet = WorkbookParser.open(buildWorkbook(), Locale.US).sheets.single()
        val cell = sheet.cell(0, 0)!!
        val style = sheet.styles[cell.styleId]

        assertTrue(style.font.bold)
        assertEquals(14f, style.font.sizePt)
        assertNotNull(style.font.color)
        assertEquals(0xFFFF0000.toInt(), style.font.color)
        assertEquals(HAlign.CENTER, style.hAlign)
        assertEquals(VAlign.CENTER, style.vAlign)
        assertEquals(0xFFFFFF00.toInt(), style.fillColor)
        assertEquals(BorderKind.THIN, style.borders.bottom.kind)
    }

    @Test
    fun `reads geometry, merges and frozen panes`() {
        val sheet = WorkbookParser.open(buildWorkbook(), Locale.US).sheets.single()

        assertEquals("Данные", sheet.name)
        assertEquals(1, sheet.freezeRows)
        assertEquals(2, sheet.freezeCols)
        assertTrue(sheet.isHidden(7))
        assertTrue(sheet.isHiddenColumn(5))

        assertEquals(40f, sheet.rowHeights[0], 0.5f)
        assertTrue(abs(sheet.colWidths[1] - 215f) < 2f, "unexpected width ${sheet.colWidths[1]}")

        assertEquals(CellRange(2, 0, 2, 2), sheet.regionAt(2, 1))
        assertEquals(CellRange(2, 0, 2, 2), sheet.anchorOf(2, 2))
        assertNull(sheet.regionAt(3, 1))
    }

    @Test
    fun `rejects files that are not OLE2 documents`() {
        assertThrows(NotAnExcelFileException::class.java) {
            WorkbookParser.open("just,some,text\n1,2,3".toByteArray())
        }
    }

    @Test
    fun `handles workbooks without sheets`() {
        val buffer = ByteArrayOutputStream()
        HSSFWorkbook().use { it.write(buffer) }
        val document = WorkbookParser.open(buffer.toByteArray())
        assertTrue(document.sheets.isEmpty())
    }

    private fun buildWorkbook(): ByteArray {
        val workbook = HSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Данные")

            val header = sheet.createRow(0)
            header.heightInPoints = 30f

            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 14
                color = HSSFColor.HSSFColorPredefined.RED.index
            }
            val titleStyle = workbook.createCellStyle().apply {
                setFont(font)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                fillForegroundColor = HSSFColor.HSSFColorPredefined.YELLOW.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setBorderBottom(BorderStyle.THIN)
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
                setCellValue(java.util.Date.from(noonOf(2025, 11, 17)))
                cellStyle = dateStyle
            }
            val formulaCell = header.createCell(3).apply { cellFormula = "B1*2" }
            workbook.creationHelper.createFormulaEvaluator().evaluateFormulaCell(formulaCell)
            header.createCell(4).setCellValue(true)
            header.createCell(5).setCellErrorValue(FormulaError.DIV0.code)

            val mergedRow = sheet.createRow(2)
            mergedRow.createCell(0).setCellValue("Объединённая ячейка")
            sheet.addMergedRegion(CellRangeAddress(2, 2, 0, 2))

            sheet.setColumnWidth(1, 30 * 256)
            sheet.setColumnHidden(5, true)
            sheet.createRow(7).zeroHeight = true
            sheet.createFreezePane(2, 1)
            sheet.setDisplayGridlines(false)

            val buffer = ByteArrayOutputStream()
            workbook.write(buffer)
            return buffer.toByteArray()
        } finally {
            workbook.close()
        }
    }

    private fun noonOf(year: Int, month: Int, day: Int): java.time.Instant =
        LocalDate.of(year, month, day)
            .atTime(LocalTime.NOON)
            .atZone(ZoneId.systemDefault())
            .toInstant()
}
