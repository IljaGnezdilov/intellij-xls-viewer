package org.cprime.xlsviewer.ui

import com.intellij.util.ui.UIUtil
import org.cprime.xlsviewer.model.SheetTab
import java.awt.Color
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.min

/** Row numbers on the left of the grid, with frozen rows pinned to the top. */
class XlsRowHeader(private val grid: XlsGridComponent) : JComponent() {

    init {
        isOpaque = false
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event)) return
                grid.onRowHeaderClick?.invoke(grid.rowAtViewportY(event.y))
            }
        })
    }

    override fun getPreferredSize(): Dimension = Dimension(grid.rowHeaderWidth(), grid.preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            val visible = visibleRect
            if (visible.isEmpty) return
            g2.color = grid.theme.headerBackground
            g2.fillRect(visible.x, visible.y, visible.width, visible.height)
            paintRows(g2, visible)
            g2.color = grid.theme.separator
            g2.drawLine(visible.x + visible.width - 1, visible.y, visible.x + visible.width - 1, visible.y + visible.height)
        } finally {
            g2.dispose()
        }
    }

    private fun paintRows(g2: Graphics2D, visible: Rectangle) {
        val sheet = grid.sheet ?: return
        val geometry = grid.geometry
        val frozenRows = min(sheet.freezeRows, geometry.rowCount)
        val frozenHeight = geometry.rowY[frozenRows]
        g2.font = grid.headerFont()
        val metrics = g2.fontMetrics
        val width = width

        if (frozenRows > 0) {
            val oldClip = g2.clip
            g2.clipRect(visible.x, visible.y, visible.width, frozenHeight)
            for (row in 0 until frozenRows) paintRow(g2, row, visible.y, metrics, width)
            g2.clip = oldClip
        }

        val top = visible.y + frozenHeight
        val oldClip = g2.clip
        g2.clipRect(visible.x, top, visible.width, visible.height - frozenHeight)
        val first = geometry.rowAt(top)
        val last = geometry.rowAt(visible.y + visible.height)
        for (row in first..last) paintRow(g2, row, 0, metrics, width)
        g2.clip = oldClip
    }

    private fun paintRow(g2: Graphics2D, row: Int, baseY: Int, metrics: java.awt.FontMetrics, width: Int) {
        val geometry = grid.geometry
        val height = geometry.rowHeight(row)
        if (height <= 0) return
        val y = baseY + geometry.rowY[row]
        val diff = grid.sheet?.diff
        val placeholder = diff?.isPlaceholderRow(row) == true
        val selected = row in grid.selection.startRow..grid.selection.endRow
        if (selected) {
            g2.color = grid.theme.headerSelection
            g2.fillRect(0, y, width, height)
        }

        val dot = if (diff != null) grid.markerDiameter(metrics) else 0
        val dotSpace = if (dot > 0) dot + 3 else 0
        val label = if (placeholder) null else diff?.displayRow(row)?.takeIf { it > 0 } ?: (row + 1)
        if (label != null) {
            g2.color = if (selected) grid.theme.accent else grid.theme.headerForeground
            val text = label.toString()
            val x = width - 7 - dotSpace - metrics.stringWidth(text)
            val baseline = y + (height + metrics.ascent - metrics.descent) / 2
            g2.drawString(text, x, baseline)
        }

        val mark = if (placeholder) null else diff?.rowMark(row)
        if (mark != null) {
            drawDot(g2, width - 7 - dot / 2, y + height / 2, dot, grid.theme.markColor(mark))
        }

        if (diff != null && diff.startsRowRange(row)) {
            g2.color = grid.theme.gapLine
            g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(2f, 2f), 0f)
            g2.drawLine(0, y, width, y)
            g2.stroke = BasicStroke(1f)
            g2.color = grid.theme.gapLine
            val centerY = y + height / 2
            for (step in -1..1) g2.fillRect(4, centerY + step * 4 - 1, 2, 2)
        }

        if (!selected) {
            g2.color = grid.theme.gridLine
            g2.drawLine(0, y + height - 1, width, y + height - 1)
        }
    }
}

/** Small filled circle used as a diff marker. Sized to the height of a capital letter. */
private fun drawDot(g2: Graphics2D, centerX: Int, centerY: Int, diameter: Int, color: Color) {
    if (diameter <= 0) return
    g2.color = color
    g2.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter)
}

/** Column letters (A, B, ... AA) above the grid, with frozen columns pinned to the left. */
class XlsColumnHeader(private val grid: XlsGridComponent) : JComponent() {

    init {
        isOpaque = false
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event)) return
                grid.onColumnHeaderClick?.invoke(grid.colAtViewportX(event.x))
            }
        })
    }

    override fun getPreferredSize(): Dimension = Dimension(grid.preferredSize.width, grid.headerHeight())

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            val visible = visibleRect
            if (visible.isEmpty) return
            g2.color = grid.theme.headerBackground
            g2.fillRect(visible.x, visible.y, visible.width, visible.height)
            paintColumns(g2, visible)
            g2.color = grid.theme.separator
            g2.drawLine(visible.x, visible.y + visible.height - 1, visible.x + visible.width, visible.y + visible.height - 1)
        } finally {
            g2.dispose()
        }
    }

    private fun paintColumns(g2: Graphics2D, visible: Rectangle) {
        val sheet = grid.sheet ?: return
        val geometry = grid.geometry
        val frozenCols = min(sheet.freezeCols, geometry.colCount)
        val frozenWidth = geometry.colX[frozenCols]
        g2.font = grid.headerFont()
        val metrics = g2.fontMetrics
        val height = height

        if (frozenCols > 0) {
            val oldClip = g2.clip
            g2.clipRect(visible.x, visible.y, frozenWidth, visible.height)
            for (col in 0 until frozenCols) paintColumn(g2, col, visible.x, metrics, height)
            g2.clip = oldClip
        }

        val left = visible.x + frozenWidth
        val oldClip = g2.clip
        g2.clipRect(left, visible.y, visible.width - frozenWidth, visible.height)
        val first = geometry.colAt(left)
        val last = geometry.colAt(visible.x + visible.width)
        for (col in first..last) paintColumn(g2, col, 0, metrics, height)
        g2.clip = oldClip
    }

    private fun paintColumn(g2: Graphics2D, col: Int, baseX: Int, metrics: java.awt.FontMetrics, height: Int) {
        val geometry = grid.geometry
        val width = geometry.colWidth(col)
        if (width <= 0) return
        val x = baseX + geometry.colX[col]
        val diff = grid.sheet?.diff
        val placeholder = diff?.isPlaceholderCol(col) == true
        val selected = col in grid.selection.startCol..grid.selection.endCol
        if (selected) {
            g2.color = grid.theme.headerSelection
            g2.fillRect(x, 0, width, height)
        }

        val sourceCol = diff?.displayCol(col) ?: col
        val label = if (placeholder || sourceCol < 0) null else columnName(sourceCol)
        val textWidth = label?.let { metrics.stringWidth(it) } ?: 0
        val mark = if (placeholder) null else diff?.colMark(col)
        val dot = if (diff != null) grid.markerDiameter(metrics) else 0
        val dotGap = if (mark != null) dot + 2 else 0
        val groupWidth = textWidth + dotGap
        val startX = x + (width - groupWidth) / 2

        if (label != null) {
            g2.color = if (selected) grid.theme.accent else grid.theme.headerForeground
            val baseline = (height + metrics.ascent - metrics.descent) / 2
            g2.drawString(label, startX, baseline)
        }
        if (mark != null) {
            drawDot(g2, startX + textWidth + 3 + dot / 2, height / 2, dot, grid.theme.markColor(mark))
        }

        if (diff != null && diff.startsColRange(col)) {
            g2.color = grid.theme.gapLine
            g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(2f, 2f), 0f)
            g2.drawLine(x, 0, x, height)
            g2.stroke = BasicStroke(1f)
            g2.color = grid.theme.gapLine
            val centerX = x + width / 2
            for (step in -1..1) g2.fillRect(centerX + step * 4 - 1, 3, 2, 2)
        }

        if (!selected) {
            g2.color = grid.theme.gridLine
            g2.drawLine(x + width - 1, 0, x + width - 1, height)
        }
    }
}

/** Small box above the row numbers. */
class XlsCorner(private val grid: XlsGridComponent) : JComponent() {
    init {
        isOpaque = false
    }

    override fun getPreferredSize(): Dimension = Dimension(grid.rowHeaderWidth(), grid.headerHeight())

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.color = grid.theme.headerBackground
            g2.fillRect(0, 0, width, height)
            g2.color = grid.theme.separator
            g2.drawLine(width - 1, 0, width - 1, height)
            g2.drawLine(0, height - 1, width, height - 1)
        } finally {
            g2.dispose()
        }
    }
}

/** Excel-like sheet tabs at the bottom of the editor. */
class XlsSheetTabs : JComponent() {

    var sheets: List<SheetTab> = emptyList()
        set(value) {
            field = value
            offset = 0
            ensureVisible()
            revalidate()
            repaint()
        }

    var selectedIndex: Int = -1
        set(value) {
            if (field == value) return
            field = value
            ensureVisible()
            repaint()
        }

    var theme: XlsTheme = XlsTheme.current(false)
        set(value) {
            field = value
            repaint()
        }

    var onSelect: ((Int) -> Unit)? = null

    private var offset = 0
    private var tabsLayout: List<TabEntry> = emptyList()

    init {
        isOpaque = false
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(event: java.awt.event.MouseEvent) {
                val entry = tabsLayout.firstOrNull { it.bounds.contains(event.point) } ?: return
                onSelect?.invoke(entry.sheetIndex)
            }
        })
        addMouseWheelListener { event ->
            if (tabsLayout.isEmpty()) return@addMouseWheelListener
            offset = (offset - event.unitsToScroll * 24).coerceIn(0, maxOffset())
            repaint()
        }
    }

    override fun getPreferredSize(): Dimension {
        val metrics = getFontMetrics(UIUtil.getLabelFont())
        return Dimension(JBUI_WIDTH_ESTIMATE, metrics.height + 12)
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.color = theme.tabBackground
            g2.fillRect(0, 0, width, height)
            g2.color = theme.separator
            g2.drawLine(0, 0, width, 0)
            g2.font = UIUtil.getLabelFont()
            val metrics = g2.fontMetrics
            tabsLayout = layoutTabs(metrics)
            val dotDiameter = maxOf(3, metrics.ascent / 2)
            for (entry in tabsLayout) {
                val bounds = entry.bounds
                val selected = entry.sheetIndex == selectedIndex
                g2.color = if (selected) theme.tabSelected else theme.tabBackground
                g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
                if (selected) {
                    g2.color = theme.accent
                    g2.fillRect(bounds.x, bounds.y, bounds.width, 2)
                }
                g2.color = if (selected) theme.accent else theme.tabForeground
                val label = entry.name
                val textWidth = metrics.stringWidth(label)
                val mark = entry.mark
                val dotGap = if (mark != null) dotDiameter + 3 else 0
                val groupWidth = textWidth + dotGap
                val textX = bounds.x + maxOf(6, (bounds.width - groupWidth) / 2)
                val baseline = bounds.y + (bounds.height + metrics.ascent - metrics.descent) / 2
                g2.drawString(label, textX, baseline)
                if (mark != null) {
                    drawDot(g2, textX + textWidth + 3 + dotDiameter / 2, bounds.y + bounds.height / 2, dotDiameter, theme.markColor(mark))
                }
                if (!selected) {
                    g2.color = theme.separator
                    g2.drawLine(bounds.x + bounds.width - 1, bounds.y + 4, bounds.x + bounds.width - 1, bounds.y + bounds.height - 4)
                }
            }
        } finally {
            g2.dispose()
        }
    }

    private fun layoutTabs(metrics: FontMetrics): List<TabEntry> {
        if (sheets.isEmpty()) return emptyList()
        val result = ArrayList<TabEntry>()
        var x = 2 - offset
        val markerWidth = maxOf(3, metrics.ascent / 2) + 3
        for (sheet in sheets) {
            if (sheet.hidden) continue
            val tabWidth = metrics.stringWidth(sheet.name) + 24 + if (sheet.mark != null) markerWidth else 0
            result.add(TabEntry(sheet.index, sheet.name, sheet.mark, Rectangle(x, 1, tabWidth, height - 2)))
            x += tabWidth + 2
        }
        return result
    }

    private fun maxOffset(): Int {
        val metrics = getFontMetrics(UIUtil.getLabelFont())
        val markerWidth = maxOf(3, metrics.ascent / 2) + 3
        val total = sheets.filter { !it.hidden }.sumOf {
            metrics.stringWidth(it.name) + 26 + if (it.mark != null) markerWidth else 0
        }
        return maxOf(0, total - width)
    }

    private fun ensureVisible() {
        if (width <= 0) return
        val metrics = getFontMetrics(UIUtil.getLabelFont())
        tabsLayout = layoutTabs(metrics)
        val entry = tabsLayout.firstOrNull { it.sheetIndex == selectedIndex } ?: return
        val left = entry.bounds.x
        val right = entry.bounds.x + entry.bounds.width
        offset = when {
            left < 0 -> (offset + left - 2).coerceAtLeast(0)
            right > width -> (offset + right - width + 2).coerceAtLeast(0)
            else -> offset
        }
        offset = offset.coerceAtMost(maxOffset())
        tabsLayout = layoutTabs(metrics)
    }

    private data class TabEntry(
        val sheetIndex: Int,
        val name: String,
        val mark: org.cprime.xlsviewer.model.DiffMark?,
        val bounds: Rectangle,
    )

    private companion object {
        const val JBUI_WIDTH_ESTIMATE = 200
    }
}
