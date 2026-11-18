package org.cprime.xlsviewer.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.cprime.xlsviewer.model.CellStyleModel
import org.cprime.xlsviewer.model.DiffMark
import java.awt.Color
import javax.swing.UIManager

/**
 * Colors used by the grid. Excel stores absolute colors, which look wrong on a dark
 * theme, so by default we keep fills as-is but derive "empty" backgrounds and very dark
 * text from the IDE theme.
 */
class XlsTheme private constructor(
    val originalColors: Boolean,
    val dark: Boolean,
    val sheetBackground: Color,
    val gridLine: Color,
    val headerBackground: Color,
    val headerForeground: Color,
    val headerSelection: Color,
    val selectionFill: Color,
    val selectionOutline: Color,
    val defaultText: Color,
    val defaultBorder: Color,
    val separator: Color,
    val accent: Color,
    val tabBackground: Color,
    val tabSelected: Color,
    val tabForeground: Color,
    val diffChanged: Color,
    val diffAdded: Color,
    val diffDeleted: Color,
    val placeholderBackground: Color,
    val placeholderStripe: Color,
    val gapLine: Color,
    val activeCellOutline: Color,
) {
    fun textColor(style: CellStyleModel): Color {
        val explicit = style.font.color ?: return defaultText
        val color = Color(explicit, true)
        if (originalColors) return color
        // On a dark theme, near-black text on an unfilled cell becomes unreadable.
        if (dark && style.fillColor == null && luminance(color) < 0.35f) return defaultText
        return color
    }

    fun borderColor(argb: Int): Color = if (argb == 0) defaultBorder else Color(argb, true)

    fun markColor(mark: DiffMark): Color = when (mark) {
        DiffMark.CHANGED -> diffChanged
        DiffMark.ADDED -> diffAdded
        DiffMark.DELETED -> diffDeleted
    }

    fun markTint(mark: DiffMark): Color {
        val base = markColor(mark)
        return Color(base.red, base.green, base.blue, DIFF_TINT_ALPHA)
    }

    companion object {
        private const val SELECTION_ALPHA = 60
        private const val DIFF_TINT_ALPHA = 34

        fun current(originalColors: Boolean): XlsTheme {
            val dark = !JBColor.isBright()
            val sheetBackground = if (originalColors) Color.WHITE else UIUtil.getPanelBackground()
            val defaultText = if (originalColors) Color.BLACK else UIUtil.getLabelForeground()
            val gridLine = if (originalColors) Color(0xD0, 0xD0, 0xD0) else blend(sheetBackground, defaultText, 0.22)
            val headerBackground = if (originalColors) Color(0xF3, 0xF3, 0xF3) else blend(sheetBackground, defaultText, 0.06)
            val headerForeground = if (originalColors) Color(0x44, 0x44, 0x44) else defaultText
            val accent = UIManager.getColor("Component.focusColor")?.takeIf { it.alpha > 0 }
                ?: Color(0x35, 0x7A, 0xD6)
            val selectionBase = UIManager.getColor("Table.selectionBackground")?.takeIf { it.alpha > 0 } ?: accent
            return XlsTheme(
                originalColors = originalColors,
                dark = dark,
                sheetBackground = sheetBackground,
                gridLine = gridLine,
                headerBackground = headerBackground,
                headerForeground = headerForeground,
                headerSelection = Color(selectionBase.red, selectionBase.green, selectionBase.blue, 110),
                selectionFill = Color(selectionBase.red, selectionBase.green, selectionBase.blue, SELECTION_ALPHA),
                selectionOutline = selectionBase,
                defaultText = defaultText,
                defaultBorder = if (originalColors) Color(0x80, 0x80, 0x80) else blend(sheetBackground, defaultText, 0.55),
                separator = blend(sheetBackground, defaultText, 0.18),
                accent = accent,
                tabBackground = headerBackground,
                tabSelected = if (originalColors) Color.WHITE else blend(sheetBackground, defaultText, 0.02),
                tabForeground = headerForeground,
                diffChanged = if (dark) Color(0xF2C14E) else Color(0xD89E00),
                diffAdded = if (dark) Color(0x4CC26A) else Color(0x2C9A48),
                diffDeleted = if (dark) Color(0xF06A6A) else Color(0xC93B3B),
                placeholderBackground = if (originalColors) Color(0xEF, 0xEF, 0xEF) else blend(sheetBackground, defaultText, 0.06),
                placeholderStripe = if (originalColors) Color(0xD8, 0xD8, 0xD8) else blend(sheetBackground, defaultText, 0.13),
                gapLine = if (originalColors) Color(0x90, 0x90, 0x90) else blend(sheetBackground, defaultText, 0.38),
                activeCellOutline = if (dark) Color.WHITE else Color.BLACK,
            )
        }

        fun blend(base: Color, other: Color, ratio: Double): Color {
            val r = ratio.coerceIn(0.0, 1.0)
            return Color(
                (base.red + (other.red - base.red) * r).toInt().coerceIn(0, 255),
                (base.green + (other.green - base.green) * r).toInt().coerceIn(0, 255),
                (base.blue + (other.blue - base.blue) * r).toInt().coerceIn(0, 255),
            )
        }

        /** Relative luminance, 0..1. */
        fun luminance(color: Color): Float {
            val r = color.red / 255f
            val g = color.green / 255f
            val b = color.blue / 255f
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
    }
}
