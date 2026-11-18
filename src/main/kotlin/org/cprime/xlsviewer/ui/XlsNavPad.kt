package org.cprime.xlsviewer.ui

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.UIManager

/**
 * Compact 2x2 navigation control: up/down on the top row, left/right on the bottom one.
 *
 * It is a single component drawn by hand instead of four [javax.swing.JButton]s: the IntelliJ
 * button UI paints its background with its own inner padding, so separate buttons always look
 * spaced even with zero layout gaps.
 */
class XlsNavPad(
    private val tooltips: List<String>,
    private val actions: List<() -> Unit>,
) : JComponent() {

    private var hoverIndex = -1
    private var pressedIndex = -1
    private val cellSize = JBUI.scale(25)

    init {
        isOpaque = false
        preferredSize = Dimension(cellSize * 2, cellSize * 2)
        ToolTipManager.sharedInstance().registerComponent(this)

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(event)) return
                val index = indexAt(event.x, event.y) ?: return
                pressedIndex = index
                repaint()
            }

            override fun mouseReleased(event: MouseEvent) {
                val released = indexAt(event.x, event.y)
                val pressed = pressedIndex
                pressedIndex = -1
                repaint()
                if (pressed >= 0 && pressed == released) {
                    actions.getOrNull(pressed)?.invoke()
                }
            }

            override fun mouseExited(event: MouseEvent) {
                hoverIndex = -1
                repaint()
            }
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                val index = indexAt(event.x, event.y) ?: -1
                if (hoverIndex != index) {
                    hoverIndex = index
                    repaint()
                }
            }
        })
    }

    override fun getToolTipText(event: MouseEvent): String? =
        indexAt(event.x, event.y)?.let { tooltips.getOrNull(it) }

    private fun indexAt(x: Int, y: Int): Int? {
        if (x < 0 || y < 0 || x >= width || y >= height) return null
        val left = x < width / 2
        val top = y < height / 2
        return when {
            top && left -> 0
            top -> 1
            left -> 2
            else -> 3
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val base = UIManager.getColor("Button.background") ?: UIUtil.getPanelBackground()
            val foreground = UIUtil.getLabelForeground()
            val labels = listOf("\u25B2", "\u25BC", "\u25C0", "\u25B6")
            g2.font = UIUtil.getLabelFont()
            val metrics = g2.fontMetrics
            val width = width
            val height = height

            for (index in 0 until 4) {
                val x = if (index % 2 == 0) 0 else width / 2
                val y = if (index < 2) 0 else height / 2
                val cellWidth = if (index % 2 == 0) width / 2 else width - width / 2
                val cellHeight = if (index < 2) height / 2 else height - height / 2

                g2.color = when {
                    pressedIndex == index -> blend(base, foreground, 0.22)
                    hoverIndex == index -> blend(base, foreground, 0.10)
                    else -> base
                }
                g2.fillRect(x, y, cellWidth, cellHeight)

                val label = labels[index]
                g2.color = foreground
                g2.drawString(
                    label,
                    x + (cellWidth - metrics.stringWidth(label)) / 2,
                    y + (cellHeight + metrics.ascent - metrics.descent) / 2,
                )
            }

            val border = UIManager.getColor("Borders.color") ?: blend(base, foreground, 0.35)
            g2.color = border
            g2.drawRect(0, 0, width - 1, height - 1)
            g2.drawLine(width / 2, 0, width / 2, height - 1)
            g2.drawLine(0, height / 2, width - 1, height / 2)
        } finally {
            g2.dispose()
        }
    }

    private fun blend(a: Color, b: Color, ratio: Double): Color = Color(
        (a.red + (b.red - a.red) * ratio).toInt().coerceIn(0, 255),
        (a.green + (b.green - a.green) * ratio).toInt().coerceIn(0, 255),
        (a.blue + (b.blue - a.blue) * ratio).toInt().coerceIn(0, 255),
    )
}
