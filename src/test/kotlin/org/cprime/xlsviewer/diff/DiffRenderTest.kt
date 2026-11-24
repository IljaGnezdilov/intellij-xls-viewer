package org.cprime.xlsviewer.diff

import com.intellij.ui.components.JBScrollPane
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.cprime.xlsviewer.model.SheetTab
import org.cprime.xlsviewer.parse.WorkbookParser
import org.cprime.xlsviewer.tools.createDiffPair
import org.cprime.xlsviewer.tools.createDiffPairXlsx
import org.cprime.xlsviewer.tools.createEmptyChangesPair
import org.cprime.xlsviewer.ui.XlsColumnHeader
import org.cprime.xlsviewer.ui.XlsCorner
import org.cprime.xlsviewer.ui.XlsGridComponent
import org.cprime.xlsviewer.ui.XlsRowHeader
import org.cprime.xlsviewer.ui.XlsSheetTabs
import org.cprime.xlsviewer.ui.XlsTheme
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Offscreen render of the diff panes: verifies markers, placeholders, tints and zoom without
 * launching the IDE. PNG artifacts go to build/reports/render.
 */
class DiffRenderTest {

    @Test
    fun `renders side by side diff with markers`() = render(zoom = 1f, name = "diff")

    @Test
    fun `renders xlsx diff`() {
        val (oldBytes, newBytes) = createDiffPairXlsx()
        val diff = WorkbookDiffer.compare(
            WorkbookParser.open(oldBytes, Locale.US),
            WorkbookParser.open(newBytes, Locale.US),
        )
        renderDiff(diff, zoom = 1f, name = "diff-xlsx", collapsed = false, context = 1)
    }

    @Test
    fun `renders diff at 150 percent`() = render(zoom = 1.5f, name = "diff-150")

    @Test
    fun `renders collapsed diff`() = render(zoom = 1f, name = "diff-collapsed", collapsed = true, context = 1)

    @Test
    fun `renders collapsed diff with gaps`() {
        val (oldBytes, newBytes) = createGapPair()
        val diff = WorkbookDiffer.compare(
            WorkbookParser.open(oldBytes, Locale.US),
            WorkbookParser.open(newBytes, Locale.US),
        )
        renderDiff(diff, zoom = 1f, name = "diff-collapsed-gaps", collapsed = true, context = 0)
    }

    @Test
    fun `renders empty cells hidden and shown`() {
        val (oldBytes, newBytes) = createEmptyChangesPair()
        val left = WorkbookParser.open(oldBytes, Locale.US)
        val right = WorkbookParser.open(newBytes, Locale.US)
        renderDiff(
            WorkbookDiffer.compare(left, right),
            zoom = 1f,
            name = "diff-empty-hidden",
            collapsed = true,
            context = 1,
        )
        renderDiff(
            WorkbookDiffer.compare(left, right, ignoreEmptyChanges = false),
            zoom = 1f,
            name = "diff-empty-shown",
            collapsed = true,
            context = 1,
        )
    }

    @Test
    fun `renders selected range`() {
        val (oldBytes, newBytes) = createDiffPair()
        val diff = WorkbookDiffer.compare(
            WorkbookParser.open(oldBytes, Locale.US),
            WorkbookParser.open(newBytes, Locale.US),
        )
        renderDiff(
            diff,
            zoom = 1f,
            name = "diff-selection",
            collapsed = false,
            context = 1,
            selection = intArrayOf(1, 2, 3, 3),
        )
    }

    private fun render(zoom: Float, name: String, collapsed: Boolean = false, context: Int = 1) {
        val (oldBytes, newBytes) = createDiffPair()
        val diff = WorkbookDiffer.compare(
            WorkbookParser.open(oldBytes, Locale.US),
            WorkbookParser.open(newBytes, Locale.US),
        )
        renderDiff(diff, zoom, name, collapsed, context)
    }

    private fun renderDiff(
        diff: WorkbookDiff,
        zoom: Float,
        name: String,
        collapsed: Boolean,
        context: Int,
        selection: IntArray? = null,
    ) {
        val sheet = diff.sheets[0]
        val theme = XlsTheme.current(false)

        val leftGrid = XlsGridComponent().apply {
            setTheme(theme)
            setSheet(DiffSides.build(sheet, leftSide = true, collapsed = collapsed, context = context), zoom)
        }
        val rightGrid = XlsGridComponent().apply {
            setTheme(theme)
            setSheet(DiffSides.build(sheet, leftSide = false, collapsed = collapsed, context = context), zoom)
        }

        if (selection != null) {
            selectRange(leftGrid, selection[0], selection[1], selection[2], selection[3])
            selectRange(rightGrid, selection[0], selection[1], selection[2], selection[3])
        }

        val split = JPanel(GridLayout(1, 2, 6, 0))
        split.add(pane(leftGrid))
        split.add(pane(rightGrid))

        val tabs = XlsSheetTabs().apply {
            sheets = diff.sheets.mapIndexed { index, item -> SheetTab(index, item.name, hidden = false, mark = item.mark) }
            selectedIndex = 0
            this.theme = theme
        }

        val root = JPanel(BorderLayout())
        root.add(split, BorderLayout.CENTER)
        root.add(tabs, BorderLayout.SOUTH)
        root.background = Color.WHITE
        root.setSize(WIDTH, HEIGHT)
        layoutTree(root)

        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            root.paint(graphics)
        } finally {
            graphics.dispose()
        }
        val dir = File("build/reports/render")
        dir.mkdirs()
        ImageIO.write(image, "png", File(dir, "$name.png"))
        assertTrue(distinctColors(image) > 20)
    }

    private fun selectRange(grid: XlsGridComponent, row1: Int, col1: Int, row2: Int, col2: Int) {
        val geometry = grid.geometry
        val x1 = geometry.colX[col1] + 2
        val y1 = geometry.rowY[row1] + 2
        val x2 = geometry.colX[col2] + 2
        val y2 = geometry.rowY[row2] + 2
        val now = System.currentTimeMillis()
        val leftButton = InputEvent.BUTTON1_DOWN_MASK
        grid.dispatchEvent(MouseEvent(grid, MouseEvent.MOUSE_PRESSED, now, leftButton, x1, y1, 1, false, MouseEvent.BUTTON1))
        grid.dispatchEvent(MouseEvent(grid, MouseEvent.MOUSE_DRAGGED, now, leftButton, x2, y2, 0, false, MouseEvent.BUTTON1))
        grid.dispatchEvent(MouseEvent(grid, MouseEvent.MOUSE_RELEASED, now, 0, x2, y2, 1, false, MouseEvent.BUTTON1))
    }

    private fun createGapPair(): Pair<ByteArray, ByteArray> = buildGap(changed = false) to buildGap(changed = true)

    private fun buildGap(changed: Boolean): ByteArray {
        val workbook = HSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Строки")
            val header = sheet.createRow(0)
            listOf("ID", "Название", "Значение", "Комментарий").forEachIndexed { index, title ->
                header.createCell(index).setCellValue(title)
            }
            for (rowIndex in 1..40) {
                val row = sheet.createRow(rowIndex)
                row.createCell(0).setCellValue(rowIndex.toDouble())
                row.createCell(1).setCellValue("Строка $rowIndex")
                row.createCell(2).setCellValue(if (changed && rowIndex == 30) 999.0 else rowIndex * 1.5)
                if (rowIndex % 5 == 0) row.createCell(3).setCellValue("note $rowIndex")
            }
            val buffer = java.io.ByteArrayOutputStream()
            workbook.write(buffer)
            return buffer.toByteArray()
        } finally {
            workbook.close()
        }
    }

    private fun pane(grid: XlsGridComponent): JPanel {
        val scroll = JBScrollPane(grid)
        scroll.setRowHeaderView(XlsRowHeader(grid))
        scroll.setColumnHeaderView(XlsColumnHeader(grid))
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, XlsCorner(grid))
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(WIDTH / 2, HEIGHT)
        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    private fun layoutTree(component: Component) {
        if (component is Container) {
            component.doLayout()
            for (child in component.components) layoutTree(child)
        }
    }

    private fun distinctColors(image: BufferedImage): Int {
        val colors = HashSet<Int>()
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                colors.add(image.getRGB(x, y))
                x += 3
            }
            y += 3
        }
        return colors.size
    }

    private companion object {
        const val WIDTH = 1400
        const val HEIGHT = 620
    }
}
