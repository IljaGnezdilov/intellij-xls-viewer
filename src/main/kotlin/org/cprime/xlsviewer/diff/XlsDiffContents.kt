package org.cprime.xlsviewer.diff

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import org.cprime.xlsviewer.parse.LfsPointer
import org.cprime.xlsviewer.parse.NotAnExcelFileException
import org.cprime.xlsviewer.parse.WorkbookParser
import org.cprime.xlsviewer.parse.isWorkbookExtension

/**
 * Reads the two sides of a diff request and decides whether we can handle the request.
 */
internal object XlsDiffContents {

    fun canCompare(request: DiffRequest): Boolean {
        if (request !is ContentDiffRequest) return false
        val contents = request.contents
        return contents.size == 2 && contents.all { looksLikeWorkbook(it) }
    }

    fun looksLikeWorkbook(content: DiffContent): Boolean {
        val file = (content as? FileContent)?.file
        if (file != null && isWorkbookExtension(file.extension)) return true
        return isWorkbookExtension(content.contentType?.defaultExtension)
    }

    /** Bytes of the revision shown on this side; works for VCS revisions and on-disk files. */
    fun readBytes(content: DiffContent): ByteArray {
        val bytes: ByteArray? = when (content) {
            is FileContent -> ApplicationManager.getApplication()
                .runReadAction(Computable { content.file.contentsToByteArray() })

            is DocumentContent -> content.document.charsSequence.toString().toByteArray(Charsets.ISO_8859_1)

            else -> null
        }
        val data = bytes ?: throw NotAnExcelFileException("Unsupported diff content")
        LfsPointer.detect(data)?.let { throw it }
        if (!WorkbookParser.isWorkbookContent(data)) {
            throw NotAnExcelFileException("The diff side is not an Excel workbook")
        }
        return data
    }
}
