package org.cprime.xlsviewer.parse

/** A file whose content is a textual Git LFS pointer, not the actual workbook. */
class XlsLfsPointerException(
    val oid: String?,
    val size: Long?,
) : WorkbookParseException("The file is a Git LFS pointer (oid=${oid ?: "unknown"}, size=${size ?: -1})")

/**
 * Git LFS stores a short text stub in the repository instead of the real file:
 *
 * ```
 * version https://git-lfs.github.com/spec/v1
 * oid sha256:...
 * size 30208
 * ```
 *
 * Detecting it lets the UI explain what to do instead of saying "not a workbook".
 */
object LfsPointer {
    private const val HEADER = "version https://git-lfs.github.com/spec/v1"
    private const val MAX_POINTER_SIZE = 1024

    /** Command that downloads missing LFS objects. */
    const val FETCH_COMMAND = "git lfs fetch origin"

    /** Returns pointer metadata when [bytes] look like an LFS stub, otherwise null. */
    fun detect(bytes: ByteArray): XlsLfsPointerException? {
        if (bytes.isEmpty() || bytes.size > MAX_POINTER_SIZE) return null
        val text = String(bytes, Charsets.UTF_8)
        if (!text.startsWith(HEADER)) return null
        val lines = text.lineSequence()
        val oid = lines.firstOrNull { it.startsWith("oid sha256:") }
            ?.removePrefix("oid sha256:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val size = text.lineSequence().firstOrNull { it.startsWith("size ") }
            ?.removePrefix("size ")
            ?.trim()
            ?.toLongOrNull()
        return XlsLfsPointerException(oid, size)
    }
}
