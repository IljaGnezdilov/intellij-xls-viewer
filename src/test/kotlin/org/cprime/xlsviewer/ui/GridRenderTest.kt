package org.cprime.xlsviewer.ui

import com.intellij.ui.components.JBScrollPane
import org.cprime.xlsviewer.parse.WorkbookParser
import org.cprime.xlsviewer.tools.createFormattingSample
import org.cprime.xlsviewer.tools.createFormattingSampleXlsx
import org.cprime.xlsviewer.tools.createLargeSample
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Renders the grid offscreen. This is both a regression test for painting code and a way
 * to produce PNG artifacts for visual inspection (build/reports/render).
 */
class GridRenderTest {

    @Test
    fun `renders formatting sample`() {
        val document = WorkbookParser.open(createFormattingSample(), Locale.US)
        val image = render(document.sheets.first(), scrollY = 0)
        writeImage("formatting", image)
        assertTrue(distinctColors(image) > 12, "unexpectedly flat rendering")
    }

    @Test
    fun `renders xlsx formatting sample`() {
        val document = WorkbookParser.open(createFormattingSampleXlsx(), Locale.US)
        val image = render(document.sheets.first(), scrollY = 0)
        writeImage("formatting-xlsx", image)
        assertTrue(distinctColors(image) > 12, "unexpectedly flat rendering")
    }

    @Test
    fun `renders scrolled sheet with frozen panes`() {
        val document = WorkbookParser.open(createFormattingSample(), Locale.US)
        val image = render(document.sheets.first(), scrollY = 120)
        writeImage("formatting-scrolled", image)
        assertTrue(distinctColors(image) > 12, "unexpectedly flat rendering")
    }

    @Test
    fun `renders large sheet without loading it all`() {
        val document = WorkbookParser.open(createLargeSample(), Locale.US)
        val sheet = document.sheets.first()
        val started = System.nanoTime()
        val image = render(sheet, scrollY = 200_000)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        writeImage("large", image)
        assertTrue(elapsedMs < 4_000, "rendering a single viewport took ${elapsedMs}ms")
    }

    private fun render(sheet: org.cprime.xlsviewer.model.SheetModel, scrollY: Int): BufferedImage {
        val grid = XlsGridComponent()
        grid.setTheme(XlsTheme.current(false))
        grid.setSheet(sheet, 1f)

        val rowHeader = XlsRowHeader(grid)
        val columnHeader = XlsColumnHeader(grid)
        val corner = XlsCorner(grid)
        val scroll = JBScrollPane(grid)
        scroll.setRowHeaderView(rowHeader)
        scroll.setColumnHeaderView(columnHeader)
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner)

        val tabs = XlsSheetTabs()
        tabs.sheets = listOf(org.cprime.xlsviewer.model.SheetTab(sheet.index, sheet.name, sheet.hidden))
        tabs.selectedIndex = 0
        tabs.theme = grid.theme

        val root = JPanel(BorderLayout())
        root.add(scroll, BorderLayout.CENTER)
        root.add(tabs, BorderLayout.SOUTH)
        root.background = Color.WHITE
        root.setSize(WIDTH, HEIGHT)
        layoutTree(root)
        scroll.viewport.viewPosition = java.awt.Point(0, scrollY)
        layoutTree(root)

        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            root.paint(graphics)
        } finally {
            graphics.dispose()
        }
        return image
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

    private fun writeImage(name: String, image: BufferedImage) {
        val dir = File("build/reports/render")
        dir.mkdirs()
        ImageIO.write(image, "png", File(dir, "$name.png"))
    }

    private companion object {
        const val WIDTH = 1100
        const val HEIGHT = 620
    }
}
