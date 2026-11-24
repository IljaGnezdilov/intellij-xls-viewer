package org.cprime.xlsviewer.parse

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.cprime.xlsviewer.model.BorderKind
import org.cprime.xlsviewer.model.BorderSide
import org.cprime.xlsviewer.model.Borders
import org.cprime.xlsviewer.model.CellStyleModel
import org.cprime.xlsviewer.model.FontModel
import org.cprime.xlsviewer.model.HAlign
import org.cprime.xlsviewer.model.StyleTable
import org.cprime.xlsviewer.model.VAlign

/**
 * Converts the workbook XF/font tables into the immutable [StyleTable] used by the UI.
 */
internal class StyleTableBuilder(
    private val workbook: Workbook,
    private val colors: StyleColors,
) {
    fun build(): StyleTable {
        val styleCount = runCatching { workbook.numCellStyles }.getOrDefault(1)
        val styles = HashMap<Int, CellStyleModel>(styleCount * 2)
        for (index in 0 until styleCount) {
            val style = runCatching { workbook.getCellStyleAt(index) }.getOrNull() ?: continue
            styles[index] = convert(index, style)
        }
        val default = styles[0] ?: CellStyleModel(
            index = 0,
            font = FontModel(name = "Arial", sizePt = 10f),
        )
        return StyleTable(styles, default)
    }

    private fun convert(index: Int, style: CellStyle): CellStyleModel {
        val font = runCatching { workbook.getFontAt(style.fontIndex) }.getOrNull()
        val fill = if (runCatching { style.fillPattern }.getOrNull() == FillPatternType.NO_FILL) {
            null
        } else {
            runCatching { colors.fill(style) }.getOrNull()
        }

        return CellStyleModel(
            index = index,
            font = toFontModel(font),
            fillColor = fill,
            borders = Borders(
                left = side(style, StyleSide.LEFT, runCatching { style.borderLeft }.getOrNull()),
                right = side(style, StyleSide.RIGHT, runCatching { style.borderRight }.getOrNull()),
                top = side(style, StyleSide.TOP, runCatching { style.borderTop }.getOrNull()),
                bottom = side(style, StyleSide.BOTTOM, runCatching { style.borderBottom }.getOrNull()),
            ),
            hAlign = mapHorizontal(runCatching { style.alignment }.getOrNull()),
            vAlign = mapVertical(runCatching { style.verticalAlignment }.getOrNull()),
            indent = runCatching { style.indention.toInt() }.getOrDefault(0),
            wrap = runCatching { style.wrapText }.getOrDefault(false),
            shrinkToFit = runCatching { style.shrinkToFit }.getOrDefault(false),
            rotation = normalizeRotation(runCatching { style.rotation.toInt() }.getOrDefault(0)),
        )
    }

    private fun toFontModel(font: Font?): FontModel {
        if (font == null) return FontModel(name = "Arial", sizePt = 10f)
        return FontModel(
            name = runCatching { font.fontName }.getOrNull() ?: "Arial",
            sizePt = runCatching { font.fontHeightInPoints.toFloat() }.getOrDefault(10f),
            bold = runCatching { font.bold }.getOrDefault(false),
            italic = runCatching { font.italic }.getOrDefault(false),
            underline = runCatching { font.underline.toInt() != 0 }.getOrDefault(false),
            strikeout = runCatching { font.strikeout }.getOrDefault(false),
            color = runCatching { colors.font(font) }.getOrNull(),
        )
    }

    private fun side(style: CellStyle, side: StyleSide, border: BorderStyle?): BorderSide {
        val kind = mapBorder(border)
        if (kind == BorderKind.NONE) return BorderSide.NONE
        val color = runCatching { colors.border(style, side) }.getOrNull() ?: 0
        return BorderSide(kind, color)
    }

    private fun mapBorder(border: BorderStyle?): BorderKind = when (border) {
        null, BorderStyle.NONE -> BorderKind.NONE
        BorderStyle.HAIR -> BorderKind.HAIR
        BorderStyle.THIN -> BorderKind.THIN
        BorderStyle.MEDIUM -> BorderKind.MEDIUM
        BorderStyle.THICK -> BorderKind.THICK
        BorderStyle.DASHED, BorderStyle.MEDIUM_DASHED -> BorderKind.DASHED
        BorderStyle.DOTTED -> BorderKind.DOTTED
        BorderStyle.DOUBLE -> BorderKind.DOUBLE
        else -> BorderKind.THIN
    }

    private fun mapHorizontal(alignment: HorizontalAlignment?): HAlign = when (alignment) {
        null, HorizontalAlignment.GENERAL -> HAlign.GENERAL
        HorizontalAlignment.LEFT -> HAlign.LEFT
        HorizontalAlignment.CENTER -> HAlign.CENTER
        HorizontalAlignment.RIGHT -> HAlign.RIGHT
        HorizontalAlignment.FILL -> HAlign.FILL
        HorizontalAlignment.JUSTIFY -> HAlign.JUSTIFY
        HorizontalAlignment.CENTER_SELECTION -> HAlign.CENTER_ACROSS
        HorizontalAlignment.DISTRIBUTED -> HAlign.DISTRIBUTED
    }

    private fun mapVertical(alignment: VerticalAlignment?): VAlign = when (alignment) {
        null, VerticalAlignment.BOTTOM -> VAlign.BOTTOM
        VerticalAlignment.TOP -> VAlign.TOP
        VerticalAlignment.CENTER -> VAlign.CENTER
        VerticalAlignment.JUSTIFY -> VAlign.JUSTIFY
        VerticalAlignment.DISTRIBUTED -> VAlign.DISTRIBUTED
    }

    /** Both BIFF and OOXML store rotation in degrees (0..180) or 255 for vertically stacked text. */
    private fun normalizeRotation(rotation: Int): Int = when {
        rotation == 255 -> 0
        rotation in 0..180 -> rotation
        else -> 0
    }
}
