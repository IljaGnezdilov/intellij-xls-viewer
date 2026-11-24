package org.cprime.xlsviewer.parse

import org.cprime.xlsviewer.tools.createFormattingSample
import org.cprime.xlsviewer.tools.createFormattingSampleXlsx
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkbookFormatsTest {

    @Test
    fun `claims xls and xlsx but not other spreadsheet formats`() {
        assertTrue(isWorkbookExtension("xls"))
        assertTrue(isWorkbookExtension("XLSX"))
        assertFalse(isWorkbookExtension("xlsm"))
        assertFalse(isWorkbookExtension("csv"))
        assertFalse(isWorkbookExtension(null))
    }

    @Test
    fun `detects workbook content by magic bytes`() {
        assertTrue(WorkbookParser.isWorkbookContent(createFormattingSample()))
        assertTrue(WorkbookParser.isWorkbookContent(createFormattingSampleXlsx()))
        assertFalse(WorkbookParser.isWorkbookContent("id,name\n1,2".toByteArray()))
        assertFalse(WorkbookParser.isWorkbookContent(ByteArray(4)))
    }
}
