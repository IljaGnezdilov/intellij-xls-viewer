package org.cprime.xlsviewer.parse

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor

/**
 * Resolves indexed BIFF colors to opaque ARGB values, honouring custom palettes
 * stored inside the workbook. Returns null for "automatic" colors.
 */
internal class ColorPalette(private val workbook: HSSFWorkbook) {
    private val cache = HashMap<Int, Int?>()

    fun resolve(index: Int): Int? {
        if (index < 0 || index == AUTOMATIC_INDEX) return null
        return cache.getOrPut(index) { lookup(index) }
    }

    private fun lookup(index: Int): Int? {
        val fromPalette = runCatching { workbook.customPalette.getColor(index) }.getOrNull()
        val triplet = fromPalette?.triplet ?: HSSFColor.getIndexHash()[index]?.triplet
        return triplet?.let(::toArgb)
    }

    companion object {
        const val AUTOMATIC_INDEX = 64

        fun toArgb(triplet: ShortArray): Int {
            val r = triplet.getOrElse(0) { 0 }.toInt() and 0xFF
            val g = triplet.getOrElse(1) { 0 }.toInt() and 0xFF
            val b = triplet.getOrElse(2) { 0 }.toInt() and 0xFF
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
