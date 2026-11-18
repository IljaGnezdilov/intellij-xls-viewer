package org.cprime.xlsviewer.diff

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import org.cprime.xlsviewer.parse.LfsPointer
import org.cprime.xlsviewer.parse.NotAnXlsFileException

/**
 * Reads the two sides of a diff request and decides whether we can handle the request.
 */
internal object XlsDiffContents {

    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )

    fun canCompare(request: DiffRequest): Boolean {
        if (request !is ContentDiffRequest) return false
        val contents = request.contents
        return contents.size == 2 && contents.all { looksLikeXls(it) }
    }

    fun looksLikeXls(content: DiffContent): Boolean {
        val file = (content as? FileContent)?.file
        if (file != null && file.extension?.equals("xls", ignoreCase = true) == true) return true
        val extension = content.contentType?.defaultExtension ?: return false
        return extension.equals("xls", ignoreCase = true)
    }

    /** Bytes of the revision shown on this side; works for VCS revisions and on-disk files. */
    fun readBytes(content: DiffContent): ByteArray {
        val bytes: ByteArray? = when (content) {
            is FileContent -> ApplicationManager.getApplication()
                .runReadAction(Computable { content.file.contentsToByteArray() })

            is DocumentContent -> content.document.charsSequence.toString().toByteArray(Charsets.ISO_8859_1)

            else -> null
        }
        val data = bytes ?: throw NotAnXlsFileException("Unsupported diff content")
        LfsPointer.detect(data)?.let { throw it }
        if (data.size < OLE2_MAGIC.size || !startsWithOle2(data)) {
            throw NotAnXlsFileException("The diff side is not an Excel 97-2003 workbook")
        }
        return data
    }

    private fun startsWithOle2(bytes: ByteArray): Boolean {
        for (index in OLE2_MAGIC.indices) {
            if (bytes[index] != OLE2_MAGIC[index]) return false
        }
        return true
    }
}
