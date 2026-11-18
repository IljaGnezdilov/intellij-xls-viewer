package org.cprime.xlsviewer.model

enum class HAlign { GENERAL, LEFT, CENTER, RIGHT, FILL, JUSTIFY, CENTER_ACROSS, DISTRIBUTED }

enum class VAlign { TOP, CENTER, BOTTOM, JUSTIFY, DISTRIBUTED }

/**
 * Border line style. [lineWidth] is measured in device independent pixels at 100% zoom.
 */
enum class BorderKind(val lineWidth: Int, val isDashed: Boolean = false) {
    NONE(0),
    HAIR(1),
    THIN(1),
    MEDIUM(2),
    THICK(3),
    DASHED(1, isDashed = true),
    DOTTED(1, isDashed = true),
    DOUBLE(1),
}

/** [color] is ARGB, or 0 when the cell relies on the default theme border color. */
data class BorderSide(val kind: BorderKind, val color: Int) {
    val visible: Boolean get() = kind != BorderKind.NONE

    companion object {
        val NONE = BorderSide(BorderKind.NONE, 0)
    }
}

data class Borders(
    val left: BorderSide = BorderSide.NONE,
    val right: BorderSide = BorderSide.NONE,
    val top: BorderSide = BorderSide.NONE,
    val bottom: BorderSide = BorderSide.NONE,
) {
    val visible: Boolean get() = left.visible || right.visible || top.visible || bottom.visible

    companion object {
        val NONE = Borders()
    }
}

/**
 * Font description. [color] is ARGB, or null when the text uses the IDE theme foreground color.
 */
data class FontModel(
    val name: String,
    val sizePt: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeout: Boolean = false,
    val color: Int? = null,
)

/**
 * Resolved formatting of a single cell (an XF record).
 *
 * [fillColor] is ARGB of a solid fill, or null when the cell is not filled.
 */
data class CellStyleModel(
    val index: Int,
    val font: FontModel,
    val fillColor: Int? = null,
    val borders: Borders = Borders.NONE,
    val hAlign: HAlign = HAlign.GENERAL,
    val vAlign: VAlign = VAlign.BOTTOM,
    val indent: Int = 0,
    val wrap: Boolean = false,
    val shrinkToFit: Boolean = false,
    val rotation: Int = 0,
)

/**
 * Immutable, de-duplicated table of cell styles. Cells only reference styles by index,
 * exactly like the underlying BIFF XF table.
 */
class StyleTable(
    private val styles: Map<Int, CellStyleModel>,
    val default: CellStyleModel,
) {
    operator fun get(index: Int): CellStyleModel = styles[index] ?: default

    val size: Int get() = styles.size
}
