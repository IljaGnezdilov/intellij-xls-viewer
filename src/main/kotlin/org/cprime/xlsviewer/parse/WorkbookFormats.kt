package org.cprime.xlsviewer.parse

import java.util.Locale

/**
 * Extensions claimed by the editor and the diff tool. The actual format is detected from
 * the file content, so a renamed workbook still opens correctly.
 */
val WORKBOOK_EXTENSIONS: Set<String> = setOf("xls", "xlsx")

fun isWorkbookExtension(extension: String?): Boolean =
    extension != null && extension.lowercase(Locale.ROOT) in WORKBOOK_EXTENSIONS
