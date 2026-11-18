package org.cprime.xlsviewer.tools

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/**
 * Generates demo workbooks used for manual checks of the editor.
 * Run with: gradlew generateSamples
 */
fun main(args: Array<String>) {
    val outputDir = File(args.firstOrNull() ?: "build/samples")
    outputDir.mkdirs()

    File(outputDir, "formatting.xls").writeBytes(createFormattingSample())
    File(outputDir, "large.xls").writeBytes(createLargeSample())
    println("Samples written to ${outputDir.absolutePath}")
}

fun createFormattingSample(): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val sheet = workbook.createSheet("Форматирование")

        val titleFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 18
            color = HSSFColor.HSSFColorPredefined.WHITE.index
            fontName = "Calibri"
        }
        val titleStyle = workbook.createCellStyle().apply {
            setFont(titleFont)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = HSSFColor.HSSFColorPredefined.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val titleRow = sheet.createRow(0)
        titleRow.heightInPoints = 34f
        titleRow.createCell(0).apply {
            setCellValue("Отчёт по продажам за 2026 год")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 5))

        val headerFont = workbook.createFont().apply { bold = true }
        val headerStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            fillForegroundColor = HSSFColor.HSSFColorPredefined.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setBorderBottom(BorderStyle.MEDIUM)
            setBorderTop(BorderStyle.THIN)
            setBorderLeft(BorderStyle.THIN)
            setBorderRight(BorderStyle.THIN)
        }
        val headers = listOf("Товар", "Количество", "Цена", "Сумма", "Дата", "Доля")
        val headerRow = sheet.createRow(1)
        headerRow.heightInPoints = 22f
        headers.forEachIndexed { index, title ->
            headerRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        val moneyFormat = workbook.createDataFormat().getFormat("#,##0.00\\ \"₽\"")
        val moneyStyle = workbook.createCellStyle().apply { dataFormat = moneyFormat }
        val percentStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("0.0%")
        }
        val dateStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("dd.MM.yyyy")
            alignment = HorizontalAlignment.CENTER
        }
        val wrappedStyle = workbook.createCellStyle().apply {
            wrapText = true
            verticalAlignment = VerticalAlignment.TOP
        }
        val italicRed = workbook.createFont().apply {
            italic = true
            color = HSSFColor.HSSFColorPredefined.DARK_RED.index
        }
        val italicRedStyle = workbook.createCellStyle().apply {
            setFont(italicRed)
            wrapText = true
            verticalAlignment = VerticalAlignment.TOP
        }

        val products = listOf(
            "Ноутбук ThinkPad X1 Carbon Gen 12 (14\", 32 ГБ, 1 ТБ SSD, OLED)",
            "Монитор Dell UltraSharp U2724D",
            "Клавиатура Logitech MX Keys",
            "Мышь Logitech MX Master 3S",
            "Док-станция CalDigit TS4",
            "Наушники Sony WH-1000XM5",
            "Веб-камера Logitech Brio 4K",
            "Кабель USB-C 2 м",
        )
        for ((index, product) in products.withIndex()) {
            val row = sheet.createRow(index + 2)
            row.heightInPoints = if (index == 0) 42f else 18f
            row.createCell(0).apply {
                setCellValue(product)
                cellStyle = if (index % 3 == 0) italicRedStyle else wrappedStyle
            }
            row.createCell(1).setCellValue((index + 1) * 3.0)
            row.createCell(2).apply {
                setCellValue(12_990.0 + index * 4_500)
                cellStyle = moneyStyle
            }
            row.createCell(3).apply {
                setCellValue((index + 1) * 3.0 * (12_990.0 + index * 4_500))
                cellStyle = moneyStyle
            }
            row.createCell(4).apply {
                setCellValue(date(2026, 1 + index, 10 + index))
                cellStyle = dateStyle
            }
            row.createCell(5).apply {
                setCellValue((index + 1) * 0.031)
                cellStyle = percentStyle
            }
        }

        val totalRow = sheet.createRow(products.size + 2)
        totalRow.createCell(0).apply {
            setCellValue("Итого")
            cellStyle = headerStyle
        }
        val totalStyle = workbook.createCellStyle().apply {
            dataFormat = moneyFormat
            setBorderTop(BorderStyle.DOUBLE)
            setBorderBottom(BorderStyle.DOUBLE)
            setBorderLeft(BorderStyle.THICK)
            setBorderRight(BorderStyle.THICK)
            alignment = HorizontalAlignment.RIGHT
        }
        totalRow.createCell(3).apply {
            cellFormula = "SUM(D3:D${products.size + 1})"
            cellStyle = totalStyle
        }
        workbook.creationHelper.createFormulaEvaluator().evaluateFormulaCell(totalRow.getCell(3))
        sheet.addMergedRegion(CellRangeAddress(products.size + 2, products.size + 2, 0, 2))

        val footerRow = sheet.createRow(products.size + 4)
        footerRow.createCell(0).apply {
            setCellValue("Дата формирования: ")
            cellStyle = headerStyle
        }
        footerRow.createCell(1).apply {
            setCellValue(date(2026, 11, 17))
            cellStyle = dateStyle
        }

        sheet.setColumnWidth(0, 52 * 256)
        sheet.setColumnWidth(1, 14 * 256)
        sheet.setColumnWidth(2, 16 * 256)
        sheet.setColumnWidth(3, 18 * 256)
        sheet.setColumnWidth(4, 16 * 256)
        sheet.setColumnWidth(5, 10 * 256)
        sheet.createFreezePane(1, 2)

        val notes = workbook.createSheet("Примечания")
        notes.createRow(0).createCell(0).setCellValue("Лист с пояснениями к отчёту")

        val hidden = workbook.createSheet("Скрытый лист")
        hidden.createRow(0).createCell(0).setCellValue("Этот лист скрыт в Excel")
        workbook.setSheetHidden(2, true)

        return write(workbook)
    } finally {
        workbook.close()
    }
}

fun createLargeSample(): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val sheet = workbook.createSheet("50000 строк")
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = HSSFColor.HSSFColorPredefined.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply { bold = true })
        }
        val header = sheet.createRow(0)
        listOf("ID", "Название", "Значение", "Дата").forEachIndexed { index, title ->
            header.createCell(index).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }
        val dateStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("dd.MM.yyyy")
        }
        sheet.createFreezePane(0, 1)
        for (index in 1 until 50_000) {
            val row = sheet.createRow(index)
            row.createCell(0).setCellValue(index.toDouble())
            row.createCell(1).setCellValue("Строка №$index")
            row.createCell(2).setCellValue(index * 1.5)
            if (index % 7 == 0) {
                row.createCell(3).apply {
                    setCellValue(date(2026, 1 + index % 12, 1 + index % 28))
                    cellStyle = dateStyle
                }
            }
        }
        sheet.setColumnWidth(1, 26 * 256)
        sheet.setColumnWidth(3, 14 * 256)
        return write(workbook)
    } finally {
        workbook.close()
    }
}

private fun write(workbook: HSSFWorkbook): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    workbook.write(buffer)
    return buffer.toByteArray()
}

private fun date(year: Int, month: Int, day: Int): Date =
    Date.from(
        LocalDate.of(year, month, day)
            .atTime(LocalTime.NOON)
            .atZone(ZoneId.systemDefault())
            .toInstant(),
    )
