package org.cprime.xlsviewer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.Computable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.cprime.xlsviewer.XlsViewerBundle
import org.cprime.xlsviewer.XlsViewerSettings
import org.cprime.xlsviewer.model.SheetTab
import org.cprime.xlsviewer.model.XlsDocument
import org.cprime.xlsviewer.parse.NotAnExcelFileException
import org.cprime.xlsviewer.parse.WorkbookEncryptedException
import org.cprime.xlsviewer.parse.XlsLfsPointerException
import org.cprime.xlsviewer.parse.WorkbookParseException
import org.cprime.xlsviewer.parse.WorkbookParser
import org.cprime.xlsviewer.parse.LfsPointer
import org.cprime.xlsviewer.ui.XlsColumnHeader
import org.cprime.xlsviewer.ui.XlsCorner
import org.cprime.xlsviewer.ui.XlsGridComponent
import org.cprime.xlsviewer.ui.XlsRowHeader
import org.cprime.xlsviewer.ui.XlsSheetTabs
import org.cprime.xlsviewer.ui.XlsTheme
import org.cprime.xlsviewer.ui.cellAddress
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.Locale
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

/**
 * The main editor view: toolbar, spreadsheet grid, sheet tabs and the status bar.
 */
class XlsEditorPanel(
    private val project: Project,
    private val file: VirtualFile,
) : JPanel(BorderLayout()), Disposable {

    private val settings = XlsViewerSettings.getInstance()
    private val grid = XlsGridComponent()
    private val rowHeader = XlsRowHeader(grid)
    private val columnHeader = XlsColumnHeader(grid)
    private val corner = XlsCorner(grid)
    private val scrollPane = JBScrollPane(grid)
    private val tabs = XlsSheetTabs()
    private val sheetCombo = JComboBox<SheetItem>()
    private val statusLabel = JBLabel("")
    private val zoomLabel = JBLabel("100%")
    private val colorsToggle = JCheckBox(XlsViewerBundle.message("toolbar.colors"))
    private val gridlinesToggle = JCheckBox(XlsViewerBundle.message("toolbar.gridlines"))
    private val cards = JPanel(CardLayout())
    private val errorTitle = JBLabel("")
    private val errorText = JBTextArea()
    private val copyCommandButton = JButton(XlsViewerBundle.message("error.lfs.copy"))
    private val headerSync = ChangeListener { syncHeaders() }
    private val LOG = Logger.getInstance(XlsEditorPanel::class.java)

    private var document: XlsDocument? = null
    private var zoom = 1f
    private var updatingUi = false
    private var disposed = false
    private var pendingState: XlsEditorState? = null

    init {
        grid.onSelectionChanged = { updateStatus() }
        add(createToolbar(), BorderLayout.NORTH)
        add(createCards(), BorderLayout.CENTER)
        add(createTabs(), BorderLayout.SOUTH)
        installSheetShortcuts()
        applySettings()
        load()
    }

    val preferredFocus: JComponent get() = grid

    // ------------------------------------------------------------------ UI construction

    private fun createToolbar(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(4, 6)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        left.add(JBLabel(XlsViewerBundle.message("toolbar.sheet")))
        sheetCombo.isFocusable = false
        sheetCombo.preferredSize = Dimension(JBUI.scale(190), sheetCombo.preferredSize.height)
        sheetCombo.addActionListener {
            if (updatingUi) return@addActionListener
            val item = sheetCombo.selectedItem as? SheetItem ?: return@addActionListener
            selectSheet(item.index)
        }
        left.add(sheetCombo)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        right.add(createButton("\u2212") { changeZoom(-1) })
        zoomLabel.horizontalAlignment = SwingConstants.CENTER
        zoomLabel.preferredSize = Dimension(JBUI.scale(52), zoomLabel.preferredSize.height)
        right.add(zoomLabel)
        right.add(createButton("+") { changeZoom(1) })
        right.add(gridlinesToggle)
        right.add(colorsToggle)
        right.add(createButton(XlsViewerBundle.message("toolbar.reload")) { load() })
        right.add(createButton(XlsViewerBundle.message("toolbar.openInSystem")) { openInSystem() })

        panel.add(left, BorderLayout.WEST)
        panel.add(right, BorderLayout.EAST)

        gridlinesToggle.isFocusable = false
        colorsToggle.isFocusable = false
        gridlinesToggle.addActionListener {
            if (updatingUi) return@addActionListener
            grid.gridlinesOverride = gridlinesToggle.isSelected
            rowHeader.repaint()
            columnHeader.repaint()
            persist()
        }
        colorsToggle.addActionListener {
            if (updatingUi) return@addActionListener
            settings.state.originalColors = colorsToggle.isSelected
            applyTheme()
            persist()
        }
        return panel
    }

    private fun createButton(text: String, action: () -> Unit): JButton {
        val button = JButton(text)
        button.isFocusable = false
        button.margin = Insets(2, 8, 2, 8)
        button.addActionListener { action() }
        return button
    }

    private fun createCards(): JComponent {
        scrollPane.setRowHeaderView(rowHeader)
        scrollPane.setColumnHeaderView(columnHeader)
        scrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner)
        scrollPane.border = JBUI.Borders.empty()
        scrollPane.viewport.addChangeListener(headerSync)

        val gridCard = JPanel(BorderLayout())
        gridCard.add(scrollPane, BorderLayout.CENTER)
        gridCard.add(createStatusBar(), BorderLayout.SOUTH)

        cards.add(createLoadingCard(), CARD_LOADING)
        cards.add(createErrorCard(), CARD_ERROR)
        cards.add(gridCard, CARD_GRID)
        showCard(CARD_LOADING)
        return cards
    }

    private fun createLoadingCard(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = JBLabel(XlsViewerBundle.message("loading"))
        label.horizontalAlignment = SwingConstants.CENTER
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    private fun createStatusBar(): JComponent {
        val panel = JPanel(BorderLayout())
        val borderColor = UIManager.getColor("Borders.color") ?: JBColor.GRAY
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor),
            JBUI.Borders.empty(3, 8),
        )
        panel.add(statusLabel, BorderLayout.WEST)
        return panel
    }

    private fun createErrorCard(): JComponent {
        errorTitle.font = errorTitle.font.deriveFont(Font.BOLD, errorTitle.font.size2D + 2f)
        errorText.isEditable = false
        errorText.isOpaque = false
        errorText.lineWrap = true
        errorText.wrapStyleWord = true
        errorText.border = null

        val body = JPanel(BorderLayout(0, 8))
        body.add(errorTitle, BorderLayout.NORTH)
        body.add(errorText, BorderLayout.CENTER)

        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        buttons.add(createButton(XlsViewerBundle.message("toolbar.reload")) { load() })
        buttons.add(createButton(XlsViewerBundle.message("toolbar.openInSystem")) { openInSystem() })
        copyCommandButton.isFocusable = false
        copyCommandButton.isVisible = false
        copyCommandButton.margin = Insets(2, 8, 2, 8)
        copyCommandButton.addActionListener { copyLfsCommand() }
        buttons.add(copyCommandButton)

        val panel = JPanel(BorderLayout(0, 12))
        panel.border = JBUI.Borders.empty(28)
        panel.add(body, BorderLayout.CENTER)
        panel.add(buttons, BorderLayout.SOUTH)
        return panel
    }

    private fun createTabs(): JComponent {
        tabs.onSelect = { index -> selectSheet(index) }
        tabs.theme = currentTheme()
        return tabs
    }

    private fun installSheetShortcuts() {
        val inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK), "xls-next-sheet")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK), "xls-previous-sheet")
        actionMap.put("xls-next-sheet", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = selectRelativeSheet(1)
        })
        actionMap.put("xls-previous-sheet", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = selectRelativeSheet(-1)
        })
    }

    private fun selectRelativeSheet(delta: Int) {
        val loaded = document ?: return
        val visible = loaded.sheets.filter { !it.hidden }
        if (visible.isEmpty()) return
        val current = visible.indexOfFirst { it.index == grid.sheet?.index }.let { if (it < 0) 0 else it }
        val next = Math.floorMod(current + delta, visible.size)
        selectSheet(visible[next].index)
    }

    private fun applySettings() {
        updatingUi = true
        colorsToggle.isSelected = settings.state.originalColors
        gridlinesToggle.isSelected = settings.state.showGridlines
        updatingUi = false
        applyTheme()
    }

    private fun applyTheme() {
        val theme = currentTheme()
        grid.setTheme(theme)
        tabs.theme = theme
        rowHeader.repaint()
        columnHeader.repaint()
        corner.repaint()
        tabs.repaint()
        grid.repaint()
    }

    private fun currentTheme(): XlsTheme = XlsTheme.current(colorsToggle.isSelected)

    private fun showCard(name: String) {
        (cards.layout as CardLayout).show(cards, name)
    }

    private fun syncHeaders() {
        val position = scrollPane.viewport.viewPosition
        scrollPane.rowHeader?.viewPosition = Point(0, position.y)
        scrollPane.columnHeader?.viewPosition = Point(position.x, 0)
    }

    // ------------------------------------------------------------------ loading

    private fun load() {
        showCard(CARD_LOADING)
        object : Task.Backgroundable(project, XlsViewerBundle.message("task.reading"), false) {
            private var result: XlsDocument? = null
            private var failure: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val startedAt = System.nanoTime()
                try {
                    val bytes = ApplicationManager.getApplication()
                        .runReadAction(Computable { file.contentsToByteArray() })
                    val readAt = System.nanoTime()
                    result = WorkbookParser.open(bytes, Locale.getDefault()) { name ->
                        indicator.text = XlsViewerBundle.message("task.sheet", name)
                    }
                    val parsedAt = System.nanoTime()
                    LOG.info(
                        "XLS editor: ${file.path}, ${bytes.size} B, read ${(readAt - startedAt) / 1_000_000} ms, " +
                            "parsed ${(parsedAt - readAt) / 1_000_000} ms, ${result?.sheets?.size ?: 0} sheet(s)",
                    )
                } catch (throwable: Throwable) {
                    failure = throwable
                    LOG.warn("XLS editor failed to read ${file.path}", throwable)
                }
            }

            override fun onSuccess() {
                if (disposed) return
                val loaded = result
                val error = failure
                if (loaded != null) {
                    onDocumentLoaded(loaded)
                } else if (error != null) {
                    showError(error)
                }
            }
        }.queue()
    }

    private fun onDocumentLoaded(loaded: XlsDocument) {
        document = loaded
        updatingUi = true
        sheetCombo.removeAllItems()
        for (sheet in loaded.sheets) {
            sheetCombo.addItem(SheetItem(sheet.index, sheet.name, sheet.hidden))
        }
        updatingUi = false
        tabs.sheets = loaded.sheets.map { SheetTab(it.index, it.name, it.hidden, tabColor = it.tabColor) }
        tabs.theme = currentTheme()

        if (loaded.sheets.isEmpty()) {
            showError(WorkbookParseException(XlsViewerBundle.message("error.empty")))
            return
        }

        val saved = settings.fileState(file.path)
        val pending = pendingState
        zoom = (pending?.zoom ?: saved?.zoom ?: 1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        updateZoomLabel()
        val savedSheet = (pending?.sheet ?: saved?.sheet)?.takeIf { candidate ->
            loaded.sheets.any { it.index == candidate }
        }
        selectSheet(
            savedSheet ?: loaded.firstVisibleSheet,
            pending?.row ?: saved?.row ?: 0,
            pending?.col ?: saved?.col ?: 0,
        )
        pendingState = null
        showCard(CARD_GRID)
    }

    private fun showError(throwable: Throwable) {
        copyCommandButton.isVisible = throwable is XlsLfsPointerException
        val (title, text) = describeError(throwable)
        LOG.warn("XLS editor error: ${throwable.message}", throwable)
        errorTitle.text = title
        errorText.text = text
        errorText.caretPosition = 0
        showCard(CARD_ERROR)
    }

    private fun copyLfsCommand() {
        CopyPasteManager.getInstance().setContents(StringSelection(LfsPointer.FETCH_COMMAND))
    }

    private fun describeError(throwable: Throwable): Pair<String, String> = when (throwable) {
        is XlsLfsPointerException -> XlsViewerBundle.message("error.lfs.title") to
            XlsViewerBundle.message(
                "error.lfs.text",
                throwable.oid ?: "?",
                throwable.size?.toString() ?: "?",
            )

        is WorkbookEncryptedException -> XlsViewerBundle.message("error.encrypted.title") to
            XlsViewerBundle.message("error.encrypted.text")

        is NotAnExcelFileException -> XlsViewerBundle.message("error.notExcel.title") to
            XlsViewerBundle.message("error.notExcel.text")

        else -> XlsViewerBundle.message("error.generic.title") to
            (throwable.message ?: throwable.javaClass.simpleName)
    }

    // ------------------------------------------------------------------ sheets and zoom

    private fun selectSheet(sheetIndex: Int, row: Int = 0, col: Int = 0) {
        val loaded = document ?: run {
            pendingState = XlsEditorState(sheetIndex, row, col, zoom)
            return
        }
        val sheet = loaded.sheets.firstOrNull { it.index == sheetIndex } ?: return
        scrollPane.viewport.viewPosition = Point(0, 0)
        grid.setSheet(sheet, zoom)
        grid.gridlinesOverride = null
        grid.setTheme(currentTheme())
        rowHeader.revalidate()
        columnHeader.revalidate()
        corner.revalidate()
        grid.selectCell(row, col)
        syncTabs(sheet.index)
        syncGridlines()
        updateStatus()
        rowHeader.repaint()
        columnHeader.repaint()
        tabs.repaint()
        persist()
    }

    private fun syncTabs(index: Int) {
        tabs.selectedIndex = index
        updatingUi = true
        for (itemIndex in 0 until sheetCombo.itemCount) {
            if (sheetCombo.getItemAt(itemIndex).index == index) {
                sheetCombo.selectedIndex = itemIndex
                break
            }
        }
        updatingUi = false
    }

    private fun syncGridlines() {
        updatingUi = true
        gridlinesToggle.isSelected = grid.gridlinesVisible()
        updatingUi = false
    }

    private fun changeZoom(direction: Int) {
        val current = ZOOM_LEVELS.indexOfFirst { it == zoom }.let { if (it >= 0) it else ZOOM_LEVELS.indexOfFirst { it == 1f } }
        val next = (current + direction).coerceIn(0, ZOOM_LEVELS.size - 1)
        zoom = ZOOM_LEVELS[next]
        grid.setZoom(zoom)
        updateZoomLabel()
        rowHeader.revalidate()
        columnHeader.revalidate()
        corner.revalidate()
        rowHeader.repaint()
        columnHeader.repaint()
        persist()
    }

    private fun updateZoomLabel() {
        zoomLabel.text = "${(zoom * 100).roundToInt()}%"
    }

    // ------------------------------------------------------------------ status and persistence

    private fun updateStatus() {
        val cell = grid.activeCellModel()
        val address = cellAddress(grid.activeRow, grid.activeCol)
        statusLabel.text = if (cell == null) {
            address
        } else {
            buildString {
                append(address)
                append("  ")
                append(XlsViewerBundle.message("status.kind.${cell.kind.name}"))
                val value = cell.text.replace('\n', ' ')
                if (value.isNotEmpty()) {
                    append(": ")
                    append(if (value.length > 160) value.take(157) + "\u2026" else value)
                }
                val formula = cell.formula
                if (!formula.isNullOrEmpty()) {
                    append("   =")
                    append(formula)
                }
            }
        }
        rowHeader.repaint()
        columnHeader.repaint()
    }

    private fun persist() {
        val sheetIndex = grid.sheet?.index ?: return
        settings.updateFile(file.path, sheetIndex, grid.activeRow, grid.activeCol, zoom)
    }

    fun captureState(): FileEditorState =
        XlsEditorState(grid.sheet?.index ?: 0, grid.activeRow, grid.activeCol, zoom)

    fun restoreState(state: FileEditorState) {
        if (state !is XlsEditorState) return
        zoom = state.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        updateZoomLabel()
        if (document == null) {
            pendingState = state
        } else {
            selectSheet(state.sheet, state.row, state.col)
        }
    }

    private fun openInSystem() {
        try {
            java.awt.Desktop.getDesktop().open(java.io.File(file.path))
        } catch (throwable: Exception) {
            Messages.showErrorDialog(
                project,
                throwable.message ?: throwable.javaClass.simpleName,
                XlsViewerBundle.message("toolbar.openInSystem"),
            )
        }
    }

    override fun dispose() {
        disposed = true
        persist()
        scrollPane.viewport.removeChangeListener(headerSync)
        grid.onSelectionChanged = null
    }

    private data class SheetItem(val index: Int, val name: String, val hidden: Boolean) {
        override fun toString(): String =
            if (hidden) XlsViewerBundle.message("sheet.hidden", name) else name
    }

    private companion object {
        const val CARD_LOADING = "loading"
        const val CARD_ERROR = "error"
        const val CARD_GRID = "grid"

        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 3f

        val ZOOM_LEVELS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
    }
}
