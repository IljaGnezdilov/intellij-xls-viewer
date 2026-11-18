package org.cprime.xlsviewer.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.cprime.xlsviewer.model.BorderKind
import org.cprime.xlsviewer.model.BorderSide
import org.cprime.xlsviewer.model.Borders
import org.cprime.xlsviewer.model.CellKind
import org.cprime.xlsviewer.model.CellModel
import org.cprime.xlsviewer.model.CellRange
import org.cprime.xlsviewer.model.CellStyleModel
import org.cprime.xlsviewer.model.DiffMark
import org.cprime.xlsviewer.model.FontModel
import org.cprime.xlsviewer.model.HAlign
import org.cprime.xlsviewer.model.SheetModel
import org.cprime.xlsviewer.model.VAlign
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Virtualized owner-drawn spreadsheet grid.
 *
 * Painting is intentionally custom instead of JTable based: merged ranges, frozen panes,
 * per-side border styles and Excel-precise row/column geometry do not map onto JTable.
 */
class XlsGridComponent : JComponent(), Scrollable {

    var sheet: SheetModel? = null
        private set

    var zoom: Float = 1f
        private set

    var theme: XlsTheme = XlsTheme.current(false)
        private set

    private var geometryValue: GridGeometry = GridGeometry(SheetModel.empty(), 1f)
    val geometry: GridGeometry get() = geometryValue

    var selection: CellRange = CellRange.single(0, 0)
        private set

    var activeRow: Int = 0
        private set

    var activeCol: Int = 0
        private set

    private var anchorRow = 0
    private var anchorCol = 0
    private var dragging = false

    var onSelectionChanged: ((CellRange) -> Unit)? = null

    /** Invoked when the user clicks a row header / column header. */
    var onRowHeaderClick: ((Int) -> Unit)? = null
    var onColumnHeaderClick: ((Int) -> Unit)? = null

    /** When set, overrides the workbook "show gridlines" flag. */
    var gridlinesOverride: Boolean? = null
        set(value) {
            field = value
            repaint()
        }

    fun gridlinesVisible(): Boolean = gridlinesOverride ?: (sheet?.showGridlines ?: true)

    private val fontCache = HashMap<FontKey, Font>()

    init {
        isOpaque = true
        isFocusable = true
        ToolTipManager.sharedInstance().registerComponent(this)

        bindKey(KeyEvent.VK_DOWN, 0) { move(1, 0, false) }
        bindKey(KeyEvent.VK_UP, 0) { move(-1, 0, false) }
        bindKey(KeyEvent.VK_LEFT, 0) { move(0, -1, false) }
        bindKey(KeyEvent.VK_RIGHT, 0) { move(0, 1, false) }
        bindKey(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK) { move(1, 0, true) }
        bindKey(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK) { move(-1, 0, true) }
        bindKey(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK) { move(0, -1, true) }
        bindKey(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK) { move(0, 1, true) }
        bindKey(KeyEvent.VK_HOME, KeyEvent.CTRL_DOWN_MASK) { moveTo(0, 0, false) }
        bindKey(KeyEvent.VK_END, KeyEvent.CTRL_DOWN_MASK) { moveToEnd() }
        bindKey(KeyEvent.VK_ENTER, 0) { move(1, 0, false) }
        bindKey(KeyEvent.VK_TAB, 0) { move(0, 1, false) }
        bindKey(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK) { copySelection() }

        addMouseListener(object : MouseAdapter() {            override fun mousePressed(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event) && !SwingUtilities.isRightMouseButton(event)) return
                requestFocusInWindow()
                val cell = cellAt(event.x, event.y) ?: return
                val region = sheet?.anchorOf(cell.first, cell.second) ?: return
                if (event.isShiftDown && SwingUtilities.isLeftMouseButton(event)) {
                    selectRange(anchorRow, anchorCol, region.startRow, region.startCol, true)
                } else {
                    anchorRow = region.startRow
                    anchorCol = region.startCol
                    selectRange(anchorRow, anchorCol, region.startRow, region.startCol, false)
                }
                dragging = SwingUtilities.isLeftMouseButton(event)
            }

            override fun mouseReleased(event: MouseEvent) {
                dragging = false
            }
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(event: MouseEvent) {
                if (!dragging) return
                val cell = cellAt(event.x, event.y) ?: return
                val region = sheet?.anchorOf(cell.first, cell.second) ?: return
                selectRange(anchorRow, anchorCol, region.startRow, region.startCol, true)
            }
        })
    }

    fun setSheet(sheet: SheetModel, zoom: Float) {
        this.sheet = sheet
        this.zoom = zoom
        fontCache.clear()
        rebuildGeometry()
        anchorRow = 0
        anchorCol = 0
        selection = CellRange.single(0, 0)
        activeRow = 0
        activeCol = 0
        revalidate()
        repaint()
    }

    fun setZoom(zoom: Float) {
        if (this.zoom == zoom) return
        this.zoom = zoom
        fontCache.clear()
        rebuildGeometry()
        revalidate()
        invalidate()
        repaint()
    }

    fun setTheme(theme: XlsTheme) {
        this.theme = theme
        fontCache.clear()
        repaint()
    }

    fun selectCell(row: Int, col: Int) {
        val sheet = sheet ?: return
        val region = sheet.anchorOf(
            row.coerceIn(0, sheet.rowCount - 1),
            col.coerceIn(0, sheet.colCount - 1),
        )
        anchorRow = region.startRow
        anchorCol = region.startCol
        activeRow = region.startRow
        activeCol = region.startCol
        selection = region
        notifySelection(true)
        scrollToActive()
    }

    private fun rebuildGeometry() {
        geometryValue = GridGeometry(sheet ?: SheetModel.empty(), zoom)
    }

    fun rowHeaderWidth(): Int {
        val metrics = getFontMetrics(headerFont())
        val digits = max(2, (sheet?.rowCount ?: 1).toString().length)
        val marker = if (hasDiffMarkers()) markerDiameter(metrics) + 6 else 0
        return metrics.charWidth('0') * digits + 18 + marker
    }

    fun hasDiffMarkers(): Boolean = sheet?.diff != null

    fun markerDiameter(metrics: FontMetrics): Int = max(3, metrics.ascent / 2)

    fun headerFont(): Font = UIUtil.getLabelFont()

    fun headerHeight(): Int = getFontMetrics(headerFont()).height + 8

    fun activeCellModel(): CellModel? {
        val sheet = sheet ?: return null
        return sheet.cell(activeRow, activeCol)
    }

    /** Row under the given y coordinate of the row header (already in grid coordinates). */
    fun rowAtViewportY(y: Int): Int {
        val sheet = sheet ?: return 0
        return geometry.rowAt(toAbsoluteY(y)).coerceIn(0, sheet.rowCount - 1)
    }

    /** Column under the given x coordinate of the column header (already in grid coordinates). */
    fun colAtViewportX(x: Int): Int {
        val sheet = sheet ?: return 0
        return geometry.colAt(toAbsoluteX(x)).coerceIn(0, sheet.colCount - 1)
    }

    fun copySelection() {
        val sheet = sheet ?: return
        val text = buildString {
            for (row in selection.startRow..selection.endRow) {
                if (row > selection.startRow) append('\n')
                for (col in selection.startCol..selection.endCol) {
                    if (col > selection.startCol) append('\t')
                    val value = sheet.cell(row, col)?.text ?: ""
                    append(value.replace('\t', ' ').replace('\n', ' '))
                }
            }
        }
        if (text.isNotEmpty()) {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
        }
    }

    fun scrollToActive() {
        val sheet = sheet ?: return
        if (activeRow < 0 || activeCol < 0) return
        val rect = geometry.rectOf(activeRow, activeCol)
        scrollRectToVisible(Rectangle(rect.x, rect.y, max(rect.width, 1), max(rect.height, 1)))
    }

    // ------------------------------------------------------------------ selection

    private fun selectRange(anchorR: Int, anchorC: Int, row: Int, col: Int, extend: Boolean) {
        val sheet = sheet ?: return
        anchorRow = anchorR.coerceIn(0, sheet.rowCount - 1)
        anchorCol = anchorC.coerceIn(0, sheet.colCount - 1)
        activeRow = row.coerceIn(0, sheet.rowCount - 1)
        activeCol = col.coerceIn(0, sheet.colCount - 1)
        val region = sheet.regionAt(activeRow, activeCol)
        selection = if (extend) {
            CellRange.between(anchorRow, anchorCol, activeRow, activeCol).let { range ->
                if (region != null) CellRange.between(
                    min(range.startRow, region.startRow),
                    min(range.startCol, region.startCol),
                    max(range.endRow, region.endRow),
                    max(range.endCol, region.endCol),
                ) else range
            }
        } else {
            region ?: CellRange.single(activeRow, activeCol)
        }
        notifySelection(true)
        scrollToActive()
    }

    private fun move(deltaRow: Int, deltaCol: Int, extend: Boolean) {
        val sheet = sheet ?: return
        val region = sheet.regionAt(activeRow, activeCol)
        val startRow = region?.startRow ?: activeRow
        val endRow = region?.endRow ?: activeRow
        val startCol = region?.startCol ?: activeCol
        val endCol = region?.endCol ?: activeCol
        val targetRow = when {
            deltaRow > 0 -> endRow + deltaRow
            deltaRow < 0 -> startRow + deltaRow
            else -> activeRow
        }.coerceIn(0, sheet.rowCount - 1)
        val targetCol = when {
            deltaCol > 0 -> endCol + deltaCol
            deltaCol < 0 -> startCol + deltaCol
            else -> activeCol
        }.coerceIn(0, sheet.colCount - 1)
        if (targetRow == activeRow && targetCol == activeCol) return
        val targetRegion = sheet.regionAt(targetRow, targetCol)
        val newRow = targetRegion?.startRow ?: targetRow
        val newCol = targetRegion?.startCol ?: targetCol
        val anchorR = if (extend) anchorRow else newRow
        val anchorC = if (extend) anchorCol else newCol
        selectRange(anchorR, anchorC, newRow, newCol, extend)
    }

    private fun moveTo(row: Int, col: Int, extend: Boolean) {
        val sheet = sheet ?: return
        val anchorR = if (extend) anchorRow else row
        val anchorC = if (extend) anchorCol else col
        selectRange(anchorR, anchorC, row.coerceIn(0, sheet.rowCount - 1), col.coerceIn(0, sheet.colCount - 1), extend)
    }

    private fun moveToEnd() {
        val sheet = sheet ?: return
        val row = (sheet.usedRows - 1).coerceAtLeast(0)
        val col = (sheet.usedCols - 1).coerceAtLeast(0)
        moveTo(row, col, false)
    }

    private fun notifySelection(scroll: Boolean) {
        if (scroll) scrollToActive()
        onSelectionChanged?.invoke(selection)
        repaint()
    }

    private fun bindKey(keyCode: Int, modifiers: Int, action: () -> Unit) {
        val key = "xls-${keyCode}-${modifiers}-${action.hashCode()}"
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, modifiers), key)
        actionMap.put(key, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = action()
        })
    }

    // ------------------------------------------------------------------ geometry helpers

    private fun cellAt(x: Int, y: Int): Pair<Int, Int>? {
        val sheet = sheet ?: return null
        val geo = geometry
        val absoluteX = toAbsoluteX(x)
        val absoluteY = toAbsoluteY(y)
        val row = geo.rowAt(absoluteY).coerceIn(0, sheet.rowCount - 1)
        val col = geo.colAt(absoluteX).coerceIn(0, sheet.colCount - 1)
        return row to col
    }

    private fun toAbsoluteY(y: Int): Int {
        val sheet = sheet ?: return y
        val geo = geometry
        val frozenRows = min(sheet.freezeRows, geo.rowCount)
        val frozenHeight = geo.rowY[frozenRows]
        if (frozenHeight <= 0) return y
        val visible = visibleRect
        return if (y < visible.y + frozenHeight) (y - visible.y).coerceIn(0, max(geo.totalHeight - 1, 0)) else y
    }

    private fun toAbsoluteX(x: Int): Int {
        val sheet = sheet ?: return x
        val geo = geometry
        val frozenCols = min(sheet.freezeCols, geo.colCount)
        val frozenWidth = geo.colX[frozenCols]
        if (frozenWidth <= 0) return x
        val visible = visibleRect
        return if (x < visible.x + frozenWidth) (x - visible.x).coerceIn(0, max(geo.totalWidth - 1, 0)) else x
    }

    // ------------------------------------------------------------------ painting

    override fun getPreferredSize(): Dimension {
        val geo = geometry
        return Dimension(max(geo.totalWidth, 1), max(geo.totalHeight, 1))
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = theme.sheetBackground
            val visible = visibleRect
            if (visible.isEmpty) return
            g2.fillRect(visible.x, visible.y, visible.width, visible.height)
            paintPasses(g2, visible)
        } finally {
            g2.dispose()
        }
    }

    private fun paintPasses(g2: Graphics2D, visible: Rectangle) {
        val sheet = sheet ?: return
        val geo = geometry
        val frozenRows = min(sheet.freezeRows, geo.rowCount)
        val frozenCols = min(sheet.freezeCols, geo.colCount)
        val frozenHeight = geo.rowY[frozenRows]
        val frozenWidth = geo.colX[frozenCols]
        val bodyLeft = visible.x + frozenWidth
        val bodyTop = visible.y + frozenHeight
        val firstCol = geo.colAt(bodyLeft)
        val lastCol = geo.colAt(visible.x + visible.width)
        val firstRow = geo.rowAt(bodyTop)
        val lastRow = geo.rowAt(visible.y + visible.height)

        if (frozenHeight > 0 && frozenWidth > 0) {
            paintPass(g2, Rectangle(visible.x, visible.y, frozenWidth, frozenHeight), visible.x, visible.y, 0, frozenRows - 1, 0, frozenCols - 1)
        }
        if (frozenHeight > 0) {
            paintPass(g2, Rectangle(bodyLeft, visible.y, visible.width - frozenWidth, frozenHeight), 0, visible.y, 0, frozenRows - 1, firstCol, lastCol)
        }
        if (frozenWidth > 0) {
            paintPass(g2, Rectangle(visible.x, bodyTop, frozenWidth, visible.height - frozenHeight), visible.x, 0, firstRow, lastRow, 0, frozenCols - 1)
        }
        paintPass(g2, Rectangle(bodyLeft, bodyTop, visible.width - frozenWidth, visible.height - frozenHeight), 0, 0, firstRow, lastRow, firstCol, lastCol)
    }

    private fun paintPass(
        g2: Graphics2D,
        clip: Rectangle,
        dx: Int,
        dy: Int,
        firstRow: Int,
        lastRow: Int,
        firstCol: Int,
        lastCol: Int,
    ) {
        if (clip.width <= 0 || clip.height <= 0) return
        val oldClip = g2.clip
        g2.clipRect(clip.x, clip.y, clip.width, clip.height)
        g2.translate(dx, dy)
        paintCells(g2, firstRow, lastRow, firstCol, lastCol)
        g2.translate(-dx, -dy)
        g2.clip = oldClip
    }

    private fun paintCells(g2: Graphics2D, firstRow: Int, lastRow: Int, firstCol: Int, lastCol: Int) {
        val sheet = sheet ?: return
        val painted = HashSet<Long>(64)
        if (sheet.merged.isNotEmpty() && sheet.merged.size <= MAX_PREPAINTED_REGIONS) {
            for (region in sheet.merged) {
                if (region.endRow < firstRow || region.startRow > lastRow) continue
                if (region.endCol < firstCol || region.startCol > lastCol) continue
                val insidePass = region.startRow in firstRow..lastRow && region.startCol in firstCol..lastCol
                if (insidePass) continue
                paintCell(g2, region.startRow, region.startCol, region)
                painted.add(region.anchorKey())
            }
        }
        for (row in firstRow..lastRow) {
            if (sheet.isHidden(row)) continue
            for (col in firstCol..lastCol) {
                if (sheet.isHiddenColumn(col)) continue
                val region = sheet.regionAt(row, col)
                if (region != null) {
                    if (region.startRow != row || region.startCol != col) continue
                    if (!painted.add(region.anchorKey())) continue
                    paintCell(g2, row, col, region)
                } else {
                    paintCell(g2, row, col, null)
                }
            }
        }
    }

    private fun paintCell(g2: Graphics2D, row: Int, col: Int, region: CellRange?) {
        val sheet = sheet ?: return
        val geo = geometry
        val endRow = (region?.endRow ?: row).coerceAtMost(geo.rowCount - 1)
        val endCol = (region?.endCol ?: col).coerceAtMost(geo.colCount - 1)
        val rect = geo.rectOf(row, col, endRow, endCol)
        if (rect.width <= 0 || rect.height <= 0) return

        val diff = sheet.diff
        if (diff != null && (diff.isPlaceholderRow(row) || diff.isPlaceholderCol(col))) {
            paintPlaceholder(g2, rect)
            return
        }

        val cell = sheet.cell(row, col)
        val style = sheet.styles[cell?.styleId ?: 0]

        val fill = style.fillColor
        if (fill != null) {
            g2.color = Color(fill, true)
            g2.fillRect(rect.x, rect.y, rect.width, rect.height)
        }

        val bandMark = strongestMark(diff?.rowMark(row), diff?.colMark(col))
        if (bandMark != null) {
            g2.color = theme.markTint(bandMark)
            g2.fillRect(rect.x, rect.y, rect.width, rect.height)
        }

        if (isSelected(row, col, region)) {
            g2.color = theme.selectionFill
            g2.fillRect(rect.x, rect.y, rect.width, rect.height)
        }

        if (gridlinesVisible()) {
            g2.color = theme.gridLine
            val right = rect.x + rect.width - 1
            val bottom = rect.y + rect.height - 1
            if (!sameRegion(row, col, row, endCol + 1)) g2.drawLine(right, rect.y, right, bottom)
            if (!sameRegion(row, col, endRow + 1, col)) g2.drawLine(rect.x, bottom, right, bottom)
        }

        if (style.borders.visible) paintBorders(g2, rect, style.borders)

        if (cell != null && cell.text.isNotEmpty()) {
            paintText(g2, cell, style, rect)
        }

        val outlineMark = diff?.cellMark(row, col)
        if (outlineMark != null) {
            g2.color = theme.markColor(outlineMark)
            g2.stroke = BasicStroke(MARK_OUTLINE_WIDTH)
            g2.drawRect(rect.x + 1, rect.y + 1, max(rect.width - 3, 0), max(rect.height - 3, 0))
            g2.stroke = BasicStroke(1f)
        }

        // Full rectangle around the selected range, drawn only for cells that actually belong to
        // it (otherwise the sides would run along the whole column/row outside the selection).
        val selectedRegion = region != null &&
            !(selection.endRow < region.startRow || selection.startRow > region.endRow ||
                selection.endCol < region.startCol || selection.startCol > region.endCol)
        val insideSelection = selection.contains(row, col) || selectedRegion
        if (selection.rowCount * selection.colCount > 1 && insideSelection) {
            val topEdge = row == selection.startRow
            val bottomEdge = endRow == selection.endRow
            val leftEdge = col == selection.startCol
            val rightEdge = endCol == selection.endCol
            if (topEdge || bottomEdge || leftEdge || rightEdge) {
                g2.color = theme.activeCellOutline
                g2.stroke = BasicStroke(1.5f)
                val inset = 1
                val x1 = rect.x + inset
                val y1 = rect.y + inset
                val x2 = rect.x + rect.width - 1 - inset
                val y2 = rect.y + rect.height - 1 - inset
                if (topEdge) g2.drawLine(x1, y1, x2, y1)
                if (bottomEdge) g2.drawLine(x1, y2, x2, y2)
                if (leftEdge) g2.drawLine(x1, y1, x1, y2)
                if (rightEdge) g2.drawLine(x2, y1, x2, y2)
                g2.stroke = BasicStroke(1f)
            }
        }

        if (diff != null) {
            val gapStroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(2f, 2f), 0f)
            if (diff.startsRowRange(row)) {
                g2.color = theme.gapLine
                g2.stroke = gapStroke
                g2.drawLine(rect.x, rect.y, rect.x + rect.width - 1, rect.y)
                g2.stroke = BasicStroke(1f)
            }
            if (diff.startsColRange(col)) {
                g2.color = theme.gapLine
                g2.stroke = gapStroke
                g2.drawLine(rect.x, rect.y, rect.x, rect.y + rect.height - 1)
                g2.stroke = BasicStroke(1f)
            }
        }

        // Bold adaptive outline of the active cell, drawn last so it stays visible on top of
        // selection tint, diff outlines and gap markers.
        if (row == activeRow && col == activeCol) {
            g2.color = theme.activeCellOutline
            g2.stroke = BasicStroke(3f)
            g2.drawRect(rect.x + 2, rect.y + 2, max(rect.width - 4, 0), max(rect.height - 4, 0))
            g2.stroke = BasicStroke(1f)
        }
    }

    private fun isSelected(row: Int, col: Int, region: CellRange?): Boolean {        if (region == null) return selection.contains(row, col)
        return !(selection.endRow < region.startRow ||
            selection.startRow > region.endRow ||
            selection.endCol < region.startCol ||
            selection.startCol > region.endCol)
    }

    private fun sameRegion(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        val sheet = sheet ?: return false
        if (r2 < 0 || c2 < 0 || r2 >= sheet.rowCount || c2 >= sheet.colCount) return false
        val current = sheet.regionAt(r1, c1) ?: return false
        val other = sheet.regionAt(r2, c2) ?: return false
        return current == other
    }

    private fun paintBorders(g2: Graphics2D, rect: Rectangle, borders: Borders) {
        val right = rect.x + rect.width - 1
        val bottom = rect.y + rect.height - 1
        if (borders.top.visible) drawBorderSide(g2, borders.top, rect.x, rect.y, right, rect.y)
        if (borders.bottom.visible) drawBorderSide(g2, borders.bottom, rect.x, bottom, right, bottom)
        if (borders.left.visible) drawBorderSide(g2, borders.left, rect.x, rect.y, rect.x, bottom)
        if (borders.right.visible) drawBorderSide(g2, borders.right, right, rect.y, right, bottom)
    }

    /** Diagonal hatching used for the side that does not contain a row or column. */
    private fun paintPlaceholder(g2: Graphics2D, rect: Rectangle) {
        g2.color = theme.placeholderBackground
        g2.fillRect(rect.x, rect.y, rect.width, rect.height)
        val oldClip = g2.clip
        g2.clipRect(rect.x, rect.y, rect.width, rect.height)
        g2.color = theme.placeholderStripe
        val step = max(4, (8 * zoom).toInt())
        var x = rect.x - rect.height
        while (x < rect.x + rect.width) {
            g2.drawLine(x, rect.y + rect.height - 1, x + rect.height, rect.y)
            x += step
        }
        g2.clip = oldClip
    }

    /** ADDED/DELETED/CHANGED: the most "severe" mark wins when a row and a column are both marked. */
    private fun strongestMark(rowMark: DiffMark?, colMark: DiffMark?): DiffMark? {
        if (rowMark == null) return colMark
        if (colMark == null) return rowMark
        return if (rowMark.ordinal >= colMark.ordinal) rowMark else colMark
    }

    private fun drawBorderSide(g2: Graphics2D, side: BorderSide, x1: Int, y1: Int, x2: Int, y2: Int) {
        g2.color = theme.borderColor(side.color)
        val width = max(1, side.kind.lineWidth)
        if (side.kind == BorderKind.DOUBLE) {
            g2.stroke = BasicStroke(1f)
            g2.drawLine(x1, y1, x2, y2)
            if (y1 == y2) g2.drawLine(x1, y1 + 2, x2, y2 + 2) else g2.drawLine(x1 + 2, y1, x2 + 2, y2)
            return
        }
        g2.stroke = if (side.kind.isDashed) {
            BasicStroke(width.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(2f, 2f), 0f)
        } else {
            BasicStroke(width.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        }
        g2.drawLine(x1, y1, x2, y2)
        g2.stroke = BasicStroke(1f)
    }

    private fun paintText(g2: Graphics2D, cell: CellModel, style: CellStyleModel, rect: Rectangle) {
        val color = theme.textColor(style)
        val oldClip = g2.clip
        g2.clipRect(rect.x + 1, rect.y + 1, max(rect.width - 2, 1), max(rect.height - 2, 1))
        try {
            if (style.rotation == 90) {
                paintRotatedText(g2, cell.text, style, color, rect)
            } else {
                paintHorizontalText(g2, cell.text, cell.kind, style, color, rect)
            }
        } finally {
            g2.clip = oldClip
        }
    }

    private fun paintHorizontalText(
        g2: Graphics2D,
        text: String,
        kind: CellKind,
        style: CellStyleModel,
        color: Color,
        rect: Rectangle,
    ) {
        var font = fontFor(style.font)
        g2.font = font
        var metrics = g2.fontMetrics
        val padding = CELL_PADDING
        val innerX = rect.x + padding
        val innerY = rect.y + 1
        val innerWidth = max(rect.width - padding * 2, 1)
        val innerHeight = max(rect.height - 2, 1)

        var lines = if (style.wrap) wrapText(text, metrics, innerWidth) else text.split('\n')
        if (style.shrinkToFit && lines.size == 1) {
            var size = font.size
            while (size > MIN_FONT_SIZE && metrics.stringWidth(lines[0]) > innerWidth) {
                size--
                font = Font(font.name, font.style, size)
                g2.font = font
                metrics = g2.fontMetrics
            }
        }

        val lineHeight = metrics.height
        val blockHeight = lines.size * lineHeight
        val startY = when (style.vAlign) {
            VAlign.TOP, VAlign.JUSTIFY, VAlign.DISTRIBUTED -> innerY
            VAlign.CENTER -> innerY + (innerHeight - blockHeight) / 2
            VAlign.BOTTOM -> innerY + innerHeight - blockHeight
        }
        val align = effectiveAlign(style, kind)
        val indent = style.indent * INDENT_STEP
        g2.color = color
        for ((index, line) in lines.withIndex()) {
            if (line.isEmpty()) continue
            val lineWidth = metrics.stringWidth(line)
            val x = when (align) {
                HAlign.RIGHT -> innerX + innerWidth - lineWidth
                HAlign.CENTER, HAlign.CENTER_ACROSS -> innerX + (innerWidth - lineWidth) / 2
                else -> innerX + indent
            }
            val baseline = startY + index * lineHeight + metrics.ascent + (lineHeight - metrics.height) / 2
            g2.drawString(line, x, baseline)
            if (style.font.underline) {
                g2.drawLine(x, baseline + 1, x + lineWidth, baseline + 1)
            }
            if (style.font.strikeout) {
                g2.drawLine(x, baseline - metrics.ascent / 2, x + lineWidth, baseline - metrics.ascent / 2)
            }
        }
    }

    private fun paintRotatedText(g2: Graphics2D, text: String, style: CellStyleModel, color: Color, rect: Rectangle) {
        val font = fontFor(style.font)
        val oldTransform = g2.transform
        val centerX = rect.x + rect.width / 2.0
        val centerY = rect.y + rect.height / 2.0
        g2.rotate(-Math.PI / 2, centerX, centerY)
        g2.font = font
        g2.color = color
        val metrics = g2.fontMetrics
        val lineWidth = metrics.stringWidth(text)
        val x = (centerX - lineWidth / 2.0).toFloat()
        val y = (centerY + (metrics.ascent - metrics.descent) / 2.0).toFloat()
        g2.drawString(text, x, y)
        g2.transform = oldTransform
    }

    private fun wrapText(text: String, metrics: FontMetrics, maxWidth: Int): List<String> {
        val result = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            if (paragraph.isEmpty()) {
                result.add("")
                continue
            }
            var current = StringBuilder()
            for (word in paragraph.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isEmpty() || metrics.stringWidth(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    result.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            result.add(current.toString())
        }
        return result
    }

    private fun effectiveAlign(style: CellStyleModel, kind: CellKind): HAlign {
        if (style.hAlign != HAlign.GENERAL) return style.hAlign
        return when (kind) {
            CellKind.NUMBER, CellKind.DATE, CellKind.BOOLEAN, CellKind.ERROR -> HAlign.RIGHT
            else -> HAlign.LEFT
        }
    }

    private fun fontFor(font: FontModel): Font {
        val size = max(1, (font.sizePt * PX_PER_POINT * zoom).roundToInt())
        val key = FontKey(font.name, size, font.bold, font.italic)
        return fontCache.getOrPut(key) {
            val style = (if (font.bold) Font.BOLD else Font.PLAIN) or (if (font.italic) Font.ITALIC else Font.PLAIN)
            Font(font.name, style, size)
        }
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val sheet = sheet ?: return null
        val cell = cellAt(event.x, event.y) ?: return null
        val model = sheet.cell(cell.first, cell.second) ?: return null
        val formula = model.formula
        if (!formula.isNullOrEmpty()) return "=" + escapeHtml(formula)
        if (model.text.length > 40 || model.text.contains('\n')) {
            return "<html><body style='width:320px'>" + escapeHtml(model.text).replace("\n", "<br>") + "</body></html>"
        }
        return null
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ------------------------------------------------------------------ Scrollable

    override fun getPreferredScrollableViewportSize(): Dimension = Dimension(JBUI.scale(700), JBUI.scale(400))

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) {
            max(8, geometry.rowHeight(geometry.rowAt(visibleRect.y)))
        } else {
            max(8, geometry.colWidth(geometry.colAt(visibleRect.x)))
        }

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) {
            max(16, visibleRect.height - 32)
        } else {
            max(16, visibleRect.width - 32)
        }

    override fun getScrollableTracksViewportWidth(): Boolean = false

    override fun getScrollableTracksViewportHeight(): Boolean = false

    private data class FontKey(val name: String, val size: Int, val bold: Boolean, val italic: Boolean)

    private companion object {
        const val PX_PER_POINT = 96f / 72f
        const val CELL_PADDING = 3
        const val INDENT_STEP = 8
        const val MIN_FONT_SIZE = 5
        const val MAX_PREPAINTED_REGIONS = 4_000
        const val MARK_OUTLINE_WIDTH = 2f
    }
}
