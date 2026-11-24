package org.cprime.xlsviewer.parse

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.ExtendedColor
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

internal enum class StyleSide { LEFT, RIGHT, TOP, BOTTOM }

/**
 * Resolves the colors stored by a workbook into opaque ARGB values. BIFF8 uses an indexed
 * palette, OOXML stores RGB or theme references with a tint, so each format has its own
 * implementation.
 */
internal interface StyleColors {
    fun fill(style: CellStyle): Int?

    fun border(style: CellStyle, side: StyleSide): Int?

    fun font(font: Font): Int?

    fun tab(sheet: Sheet): Int?
}

internal object NoStyleColors : StyleColors {
    override fun fill(style: CellStyle): Int? = null
    override fun border(style: CellStyle, side: StyleSide): Int? = null
    override fun font(font: Font): Int? = null
    override fun tab(sheet: Sheet): Int? = null
}

/** BIFF8: indexed colors, honouring the custom palette stored in the workbook. */
internal class HssfStyleColors(workbook: HSSFWorkbook) : StyleColors {
    private val palette = ColorPalette(workbook)

    override fun fill(style: CellStyle): Int? {
        if (runCatching { style.fillPattern }.getOrNull() == FillPatternType.NO_FILL) return null
        return runCatching { palette.resolve(style.fillForegroundColor.toInt()) }.getOrNull()
    }

    override fun border(style: CellStyle, side: StyleSide): Int? {
        val index = runCatching {
            when (side) {
                StyleSide.LEFT -> style.leftBorderColor
                StyleSide.RIGHT -> style.rightBorderColor
                StyleSide.TOP -> style.topBorderColor
                StyleSide.BOTTOM -> style.bottomBorderColor
            }
        }.getOrNull() ?: return 0
        return palette.resolve(index.toInt()) ?: 0
    }

    override fun font(font: Font): Int? =
        runCatching { palette.resolve(font.color.toInt()) }.getOrNull()

    /** Apache POI does not expose the BIFF tab color for HSSF sheets. */
    override fun tab(sheet: Sheet): Int? = null
}

/** OOXML: RGB, indexed and theme colors with tint. */
internal class XssfStyleColors(workbook: XSSFWorkbook) : StyleColors {
    private val theme = runCatching { workbook.stylesSource?.theme }.getOrNull()

    override fun fill(style: CellStyle): Int? {
        if (runCatching { style.fillPattern }.getOrNull() == FillPatternType.NO_FILL) return null
        // POI resolves theme references for fills itself.
        val color = (style as? XSSFCellStyle)?.fillForegroundColorColor ?: return null
        return toArgb(color)
    }

    override fun border(style: CellStyle, side: StyleSide): Int? {
        val xssf = style as? XSSFCellStyle ?: return 0
        val color = runCatching {
            when (side) {
                StyleSide.LEFT -> xssf.leftBorderXSSFColor
                StyleSide.RIGHT -> xssf.rightBorderXSSFColor
                StyleSide.TOP -> xssf.topBorderXSSFColor
                StyleSide.BOTTOM -> xssf.bottomBorderXSSFColor
            }
        }.getOrNull() ?: return 0
        // Border colors are the only place where POI does not apply the theme table.
        theme?.inheritFromThemeAsRequired(color)
        return toArgb(color) ?: 0
    }

    override fun font(font: Font): Int? {
        // POI resolves theme references for fonts itself.
        val color = (font as? XSSFFont)?.xssfColor ?: return null
        return toArgb(color)
    }

    override fun tab(sheet: Sheet): Int? {
        val color = runCatching { (sheet as? XSSFSheet)?.tabColor }.getOrNull() ?: return null
        theme?.inheritFromThemeAsRequired(color)
        return toArgb(color)
    }

    private fun toArgb(color: ExtendedColor): Int? {
        // getRGB() resolves indexed colors as well; getRGBWithTint() only looks at stored RGB.
        val rgb = runCatching { color.rgb }.getOrNull() ?: return null
        if (rgb.size < 3) return null
        val tint = runCatching { color.tint }.getOrDefault(0.0)
        val red = applyTint(rgb[0].toInt() and 0xFF, tint)
        val green = applyTint(rgb[1].toInt() and 0xFF, tint)
        val blue = applyTint(rgb[2].toInt() and 0xFF, tint)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun applyTint(value: Int, tint: Double): Int = when {
        tint > 0 -> (value * (1.0 - tint) + (255 - 255 * (1.0 - tint))).toInt()
        tint < 0 -> (value * (1.0 + tint)).toInt()
        else -> value
    }.coerceIn(0, 255)
}
