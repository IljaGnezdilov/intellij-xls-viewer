package org.cprime.xlsviewer.tools

import org.apache.poi.hssf.usermodel.HSSFRow
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import java.io.ByteArrayOutputStream

/**
 * Deterministic workbook pairs used by diff tests and for manual checks.
 * Run with: gradlew generateSamples
 */
fun createDiffPair(): Pair<ByteArray, ByteArray> = buildDataWorkbook(old = true) to buildDataWorkbook(old = false)

fun createFormattingPair(): Pair<ByteArray, ByteArray> = buildStyleWorkbook(changeColor = false) to buildStyleWorkbook(changeColor = true)

private fun buildDataWorkbook(old: Boolean): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val data = workbook.createSheet("Данные")
        var rowIndex = 0
        if (old) {
            writeRow(data.createRow(rowIndex++), "Товар", "Кол-во", "Цена")
            writeRow(data.createRow(rowIndex++), "Яблоко", 10.0, 100.0)
            writeRow(data.createRow(rowIndex++), "Груша", 20.0, 200.0)
            writeRow(data.createRow(rowIndex++), "Слива", 30.0, 300.0)
            writeRow(data.createRow(rowIndex++), "Вишня", 40.0, 400.0)
            writeRow(data.createRow(rowIndex++), "Дыня", 50.0, 500.0)
            workbook.createSheet("Удалённый лист").createRow(0).createCell(0).setCellValue("Этот лист будет удалён")
        } else {
            writeRow(data.createRow(rowIndex++), "Товар", "Налог", "Кол-во", "Цена")
            writeRow(data.createRow(rowIndex++), "Яблоко", 1.0, 10.0, 100.0)
            writeRow(data.createRow(rowIndex++), "Груша", 2.0, 20.0, 250.0)
            writeRow(data.createRow(rowIndex++), "Вишня", 4.0, 40.0, 400.0)
            writeRow(data.createRow(rowIndex++), "Дыня", 5.0, 50.0, 500.0)
            writeRow(data.createRow(rowIndex++), "Абрикос", 6.0, 60.0, 600.0)
            workbook.createSheet("Добавленный лист").createRow(0).createCell(0).setCellValue("Это новый лист")
        }
        data.setColumnWidth(0, 18 * 256)
        data.setColumnWidth(2, 12 * 256)
        data.createFreezePane(0, 1)
        return write(workbook)
    } finally {
        workbook.close()
    }
}

private fun buildStyleWorkbook(changeColor: Boolean): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val sheet = workbook.createSheet("Стиль")
        val font = workbook.createFont().apply {
            bold = true
            if (changeColor) color = HSSFColor.HSSFColorPredefined.RED.index
        }
        val style = workbook.createCellStyle().apply { setFont(font) }
        sheet.createRow(0).createCell(0).apply {
            setCellValue("Заголовок")
            cellStyle = style
        }
        val data = sheet.createRow(1)
        data.createCell(0).setCellValue("Значение 1")
        data.createCell(1).setCellValue(10.0)
        return write(workbook)
    } finally {
        workbook.close()
    }
}

fun createCellChangePair(): Pair<ByteArray, ByteArray> =
    buildCellChange(changed = false) to buildCellChange(changed = true)

fun createRowAddedPair(): Pair<ByteArray, ByteArray> =
    buildRowAdded(added = false) to buildRowAdded(added = true)

fun createColumnAddedPair(): Pair<ByteArray, ByteArray> =
    buildColumnAdded(added = false) to buildColumnAdded(added = true)

fun createPartiallyChangedPair(): Pair<ByteArray, ByteArray> =
    buildPartiallyChanged(changed = false) to buildPartiallyChanged(changed = true)

private fun buildRowAdded(added: Boolean): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val sheet = workbook.createSheet("Rows")
        writeRow(sheet.createRow(0), "A", "B", "C")
        for (row in 1..2) writeRow(sheet.createRow(row), "a$row", "b$row", "c$row")
        if (added) writeRow(sheet.createRow(3), "a3", "b3", "c3")
        return write(workbook)
    } finally {
        workbook.close()
    }
}

private fun buildColumnAdded(added: Boolean): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val sheet = workbook.createSheet("Columns")
        if (added) {
            writeRow(sheet.createRow(0), "A", "X", "B", "C")
            for (row in 1..3) writeRow(sheet.createRow(row), "a$row", "x$row", "b$row", "c$row")
        } else {
            writeRow(sheet.createRow(0), "A", "B", "C")
            for (row in 1..3) writeRow(sheet.createRow(row), "a$row", "b$row", "c$row")
        }
        return write(workbook)
    } finally {
        workbook.close()
    }
}

private fun buildCellChange(changed: Boolean): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val sheet = workbook.createSheet("Rows")
        writeRow(sheet.createRow(0), "A", "B", "C", "D")
        for (row in 1..40) {
            val value = if (changed && row == 30) 999.0 else row * 1.5
            writeRow(sheet.createRow(row), "a$row", "b$row", value, "d$row")
        }
        return write(workbook)
    } finally {
        workbook.close()
    }
}

private fun buildPartiallyChanged(changed: Boolean): ByteArray {
    val workbook = HSSFWorkbook()
    try {
        val modified = workbook.createSheet("Изменённый")
        writeRow(modified.createRow(0), "A", "B")
        writeRow(modified.createRow(1), "x", if (changed) 2.0 else 1.0)

        val untouched = workbook.createSheet("Без изменений")
        writeRow(untouched.createRow(0), "C", "D")
        writeRow(untouched.createRow(1), "y", "z")
        return write(workbook)
    } finally {
        workbook.close()
    }
}

private fun writeRow(row: HSSFRow, vararg values: Any) {
    values.forEachIndexed { index, value ->
        val cell = row.createCell(index)
        when (value) {
            is String -> cell.setCellValue(value)
            is Double -> cell.setCellValue(value)
            is Int -> cell.setCellValue(value.toDouble())
            else -> cell.setCellValue(value.toString())
        }
    }
}

private fun write(workbook: HSSFWorkbook): ByteArray {
    val buffer = ByteArrayOutputStream()
    workbook.write(buffer)
    return buffer.toByteArray()
}
