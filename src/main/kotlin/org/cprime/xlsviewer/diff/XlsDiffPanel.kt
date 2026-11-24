package org.cprime.xlsviewer.diff

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.FileContent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.cprime.xlsviewer.XlsViewerBundle
import org.cprime.xlsviewer.XlsViewerSettings
import org.cprime.xlsviewer.model.DiffMark
import org.cprime.xlsviewer.model.SheetTab
import org.cprime.xlsviewer.model.XlsDocument
import org.cprime.xlsviewer.parse.LfsPointer
import org.cprime.xlsviewer.parse.NotAnXlsFileException
import org.cprime.xlsviewer.parse.XlsEncryptedException
import org.cprime.xlsviewer.parse.XlsLfsPointerException
import org.cprime.xlsviewer.parse.XlsParseException
import org.cprime.xlsviewer.parse.XlsParser
import org.cprime.xlsviewer.ui.XlsColumnHeader
import org.cprime.xlsviewer.ui.XlsCorner
import org.cprime.xlsviewer.ui.XlsGridComponent
import org.cprime.xlsviewer.ui.XlsNavPad
import org.cprime.xlsviewer.ui.XlsRowHeader
import org.cprime.xlsviewer.ui.XlsSheetTabs
import org.cprime.xlsviewer.ui.XlsTheme
import org.cprime.xlsviewer.ui.cellAddress
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.KeyStroke
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

/**
 * Side-by-side XLS diff: two aligned grids, diff markers on rows/columns/cells and
 * Excel-like sheet tabs with markers for added/removed/modified worksheets.
 */
class XlsDiffPanel(
    private val project: Project?,
    private val leftContent: DiffContent,
    private val rightContent: DiffContent,
    private val leftTitle: String,
    private val rightTitle: String,
) : JPanel(BorderLayout()), Disposable {

    private val cards = JPanel(CardLayout())

    private val leftGrid = XlsGridComponent()
    private val rightGrid = XlsGridComponent()
    private val leftRowHeader = XlsRowHeader(leftGrid)
    private val leftColumnHeader = XlsColumnHeader(leftGrid)
    private val leftCorner = XlsCorner(leftGrid)
    private val rightRowHeader = XlsRowHeader(rightGrid)
    private val rightColumnHeader = XlsColumnHeader(rightGrid)
    private val rightCorner = XlsCorner(rightGrid)
    private val leftScroll = JBScrollPane(leftGrid)
    private val rightScroll = JBScrollPane(rightGrid)

    private val tabs = XlsSheetTabs()
    private val sheetCombo = JComboBox<SheetItem>()
    private val onlyDiffsToggle = JCheckBox(XlsViewerBundle.message("diff.onlyDiffs"))
    private val contextCombo = JComboBox(arrayOf(0, 1, 2, 3))
    private val emptyChangesToggle = JCheckBox(XlsViewerBundle.message("diff.showEmptyChanges"))
    private val statsLabel = JBLabel("")
    private val statusLabel = JBLabel("")
    private val zoomLabel = JBLabel("100%")
    private val errorTitle = JBLabel("")
    private val errorText = JBTextArea()
    private val copyCommandButton = JButton(XlsViewerBundle.message("error.lfs.copy"))
    private val openWorkingCopyButton = JButton(XlsViewerBundle.message("diff.openWorkingCopy"))
    private var workingCopyFile: VirtualFile? = null
    private var failureSide = SIDE_NONE
    private val loadingHint = JBLabel("")
    private var slowHintTimer: Timer? = null

    private var leftDocument: XlsDocument? = null
    private var rightDocument: XlsDocument? = null
    private var workbookDiff: WorkbookDiff? = null
    /** Diff per "show empty changes" mode; the comparison itself is deterministic. */
    private val diffCache = HashMap<Boolean, WorkbookDiff>()
    private var sheetIndex = 0
    private var onlyDiffs = XlsViewerSettings.getInstance().state.onlyDiffs
    private var diffContext = XlsViewerSettings.getInstance().state.diffContext.coerceIn(0, 3)
    private var showEmptyChanges = XlsViewerSettings.getInstance().state.showEmptyChanges
    private var zoom = 1f
    private var updatingUi = false
    private var syncingScroll = false
    private var syncingSelection = false
    private var disposed = false
    private val LOG = Logger.getInstance(XlsDiffPanel::class.java)

    init {
        add(createToolbar(), BorderLayout.NORTH)
        add(createCenter(), BorderLayout.CENTER)
        add(createBottom(), BorderLayout.SOUTH)
        installNavigationShortcuts()
        load()
    }

    val preferredFocus: JComponent get() = leftGrid

    // ------------------------------------------------------------------ UI

    private fun createToolbar(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(4, 6)

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        left.add(JBLabel(XlsViewerBundle.message("diff.sheet")))
        sheetCombo.isFocusable = false
        sheetCombo.preferredSize = Dimension(JBUI.scale(240), sheetCombo.preferredSize.height)
        sheetCombo.addActionListener {
            if (updatingUi) return@addActionListener
            val item = sheetCombo.selectedItem as? SheetItem ?: return@addActionListener
            applySheet(item.index)
        }
        left.add(sheetCombo)
        left.add(createNavigationPad())

        onlyDiffsToggle.isFocusable = false
        onlyDiffsToggle.isSelected = onlyDiffs
        onlyDiffsToggle.addActionListener {
            if (updatingUi) return@addActionListener
            onlyDiffs = onlyDiffsToggle.isSelected
            XlsViewerSettings.getInstance().state.onlyDiffs = onlyDiffs
            contextCombo.isEnabled = onlyDiffs
            applyDisplayOptions()
        }
        left.add(onlyDiffsToggle)

        contextCombo.isFocusable = false
        contextCombo.isEditable = false
        contextCombo.selectedItem = diffContext
        contextCombo.isEnabled = onlyDiffs
        contextCombo.toolTipText = XlsViewerBundle.message("diff.context.tooltip")
        contextCombo.addActionListener {
            if (updatingUi) return@addActionListener
            val value = contextCombo.selectedItem as? Int ?: return@addActionListener
            diffContext = value.coerceIn(0, 3)
            XlsViewerSettings.getInstance().state.diffContext = diffContext
            if (onlyDiffs) applyDisplayOptions()
        }
        left.add(JBLabel(XlsViewerBundle.message("diff.context")))
        left.add(contextCombo)

        emptyChangesToggle.isFocusable = false
        emptyChangesToggle.isSelected = showEmptyChanges
        emptyChangesToggle.toolTipText = XlsViewerBundle.message("diff.showEmptyChanges.tooltip")
        emptyChangesToggle.addActionListener {
            if (updatingUi) return@addActionListener
            showEmptyChanges = emptyChangesToggle.isSelected
            XlsViewerSettings.getInstance().state.showEmptyChanges = showEmptyChanges
            val leftDocs = leftDocument ?: return@addActionListener
            val rightDocs = rightDocument ?: return@addActionListener
            workbookDiff = diffFor(leftDocs, rightDocs, showEmptyChanges)
            updateStats()
            applyDisplayOptions()
        }
        left.add(emptyChangesToggle)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
        right.add(createSquareButton("\u2212", XlsViewerBundle.message("diff.zoomOut")) { changeZoom(-1) })
        zoomLabel.horizontalAlignment = javax.swing.SwingConstants.CENTER
        zoomLabel.preferredSize = Dimension(JBUI.scale(52), zoomLabel.preferredSize.height)
        right.add(zoomLabel)
        right.add(createSquareButton("+", XlsViewerBundle.message("diff.zoomIn")) { changeZoom(1) })

        panel.add(left, BorderLayout.WEST)
        panel.add(right, BorderLayout.EAST)
        return panel
    }

    private fun createSquareButton(text: String, tooltip: String?, action: () -> Unit): JButton {
        val button = JButton(text)
        button.isFocusable = false
        button.margin = Insets(0, 0, 0, 0)
        val size = JBUI.scale(25)
        button.preferredSize = Dimension(size, size)
        if (tooltip != null) button.toolTipText = tooltip
        button.addActionListener { action() }
        return button
    }

    /**
     * Compact segmented 2x2 navigation pad: up/down on top, left/right below.
     * Implemented as a single hand-drawn component to avoid the LAF padding that keeps
     * separate buttons visually spaced even when the layout has no gaps.
     */
    private fun createNavigationPad(): JComponent = XlsNavPad(
        tooltips = listOf(
            XlsViewerBundle.message("diff.prevRow"),
            XlsViewerBundle.message("diff.nextRow"),
            XlsViewerBundle.message("diff.prevColumn"),
            XlsViewerBundle.message("diff.nextColumn"),
        ),
        actions = listOf(
            { navigateRow(-1) },
            { navigateRow(1) },
            { navigateColumn(-1) },
            { navigateColumn(1) },
        ),
    )

    private fun createCenter(): JComponent {
        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            createPane(leftScroll, leftRowHeader, leftColumnHeader, leftCorner, leftTitle),
            createPane(rightScroll, rightRowHeader, rightColumnHeader, rightCorner, rightTitle),
        )
        split.resizeWeight = 0.5
        split.dividerSize = JBUI.scale(6)
        split.border = JBUI.Borders.empty()

        val diffCard = JPanel(BorderLayout())
        diffCard.add(split, BorderLayout.CENTER)
        diffCard.add(createStatusBar(), BorderLayout.SOUTH)

        cards.add(createLoadingCard(), CARD_LOADING)
        cards.add(createErrorCard(), CARD_ERROR)
        cards.add(diffCard, CARD_DIFF)
        showCard(CARD_LOADING)

        leftScroll.viewport.addChangeListener(viewportListener(leftScroll))
        rightScroll.viewport.addChangeListener(viewportListener(rightScroll))
        leftGrid.onRowHeaderClick = { row -> goToRow(row) }
        leftGrid.onColumnHeaderClick = { col -> goToColumn(col) }
        rightGrid.onRowHeaderClick = { row -> goToRow(row) }
        rightGrid.onColumnHeaderClick = { col -> goToColumn(col) }
        leftGrid.onSelectionChanged = {
            syncSelection(fromLeft = true)
            updateStatus()
        }
        rightGrid.onSelectionChanged = {
            syncSelection(fromLeft = false)
            updateStatus()
        }
        return cards
    }

    private fun createPane(
        scroll: JBScrollPane,
        rowHeader: XlsRowHeader,
        columnHeader: XlsColumnHeader,
        corner: XlsCorner,
        title: String,
    ): JComponent {
        scroll.setRowHeaderView(rowHeader)
        scroll.setColumnHeaderView(columnHeader)
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner)
        scroll.border = JBUI.Borders.empty()

        val label = JBLabel(title)
        label.border = JBUI.Borders.empty(3, 8)
        label.font = label.font.deriveFont(Font.BOLD)

        val panel = JPanel(BorderLayout())
        panel.add(label, BorderLayout.NORTH)
        panel.add(scroll, BorderLayout.CENTER)
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
        panel.add(statsLabel, BorderLayout.EAST)
        return panel
    }

    private fun createBottom(): JComponent {
        tabs.onSelect = { index -> applySheet(index) }
        tabs.theme = XlsTheme.current(false)
        return tabs
    }

    private fun createLoadingCard(): JComponent {
        val panel = JPanel(BorderLayout())
        val box = JPanel()
        box.layout = BoxLayout(box, BoxLayout.Y_AXIS)
        val label = JBLabel(XlsViewerBundle.message("diff.loading"))
        label.alignmentX = Component.CENTER_ALIGNMENT
        loadingHint.alignmentX = Component.CENTER_ALIGNMENT
        loadingHint.foreground = JBColor.GRAY
        box.add(label)
        box.add(Box.createVerticalStrut(10))
        box.add(loadingHint)
        panel.add(box, BorderLayout.CENTER)
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

        copyCommandButton.isFocusable = false
        copyCommandButton.isVisible = false
        copyCommandButton.addActionListener { copyLfsCommand() }
        openWorkingCopyButton.isFocusable = false
        openWorkingCopyButton.isVisible = false
        openWorkingCopyButton.addActionListener { openWorkingCopy() }
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        buttons.add(copyCommandButton)
        buttons.add(openWorkingCopyButton)

        val panel = JPanel(BorderLayout(0, 12))
        panel.border = JBUI.Borders.empty(28)
        panel.add(body, BorderLayout.CENTER)
        panel.add(buttons, BorderLayout.SOUTH)
        return panel
    }

    private fun copyLfsCommand() {
        CopyPasteManager.getInstance().setContents(StringSelection(LfsPointer.FETCH_COMMAND))
    }

    /**
     * The side that was NOT a pointer may be the real working-tree file (e.g. "Compare with
     * Local" where only the revision is an LFS stub). Then we can offer to open it directly.
     */
    private fun findWorkingCopy(): VirtualFile? {
        if (project == null) return null
        val content = when (failureSide) {
            SIDE_LEFT -> rightContent
            SIDE_RIGHT -> leftContent
            else -> return null
        }
        val file = (content as? FileContent)?.file ?: return null
        if (!file.isInLocalFileSystem || file.isDirectory) return null
        return file
    }

    private fun openWorkingCopy() {
        val file = workingCopyFile ?: return
        val project = project ?: return
        OpenFileDescriptor(project, file).navigate(true)
    }

    private fun startSlowHint() {
        stopSlowHint()
        loadingHint.text = ""
        slowHintTimer = Timer(SLOW_HINT_DELAY_MS) {
            if (!disposed) loadingHint.text = XlsViewerBundle.message("diff.slowHint")
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun stopSlowHint() {
        slowHintTimer?.stop()
        slowHintTimer = null
    }

    private fun showCard(name: String) {
        (cards.layout as CardLayout).show(cards, name)
    }

    private fun viewportListener(scroll: JBScrollPane): ChangeListener = ChangeListener {
        val position = scroll.viewport.viewPosition
        scroll.rowHeader?.viewPosition = Point(0, position.y)
        scroll.columnHeader?.viewPosition = Point(position.x, 0)
        if (!syncingScroll) {
            val other = if (scroll === leftScroll) rightScroll else leftScroll
            syncingScroll = true
            try {
                other.viewport.viewPosition = position
            } finally {
                syncingScroll = false
            }
        }
    }

    private fun syncSelection(fromLeft: Boolean) {
        if (syncingSelection) return
        syncingSelection = true
        try {
            if (fromLeft) {
                rightGrid.selectCell(leftGrid.activeRow, leftGrid.activeCol)
            } else {
                leftGrid.selectCell(rightGrid.activeRow, rightGrid.activeCol)
            }
        } finally {
            syncingSelection = false
        }
    }

    // ------------------------------------------------------------------ loading

    private fun load() {
        showCard(CARD_LOADING)
        failureSide = SIDE_NONE
        workingCopyFile = null
        diffCache.clear()
        openWorkingCopyButton.isVisible = false
        startSlowHint()
        LOG.info("XLS diff started: left=\"$leftTitle\" (${leftContent.javaClass.simpleName}), right=\"$rightTitle\" (${rightContent.javaClass.simpleName})")
        object : Task.Backgroundable(project, XlsViewerBundle.message("diff.loading"), true) {
            private var left: XlsDocument? = null
            private var right: XlsDocument? = null
            private var failure: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val startedAt = System.nanoTime()
                try {
                    failureSide = SIDE_LEFT
                    val leftBytes = XlsDiffContents.readBytes(leftContent)
                    val leftReadAt = System.nanoTime()
                    left = XlsParser.open(leftBytes)
                    val leftParsedAt = System.nanoTime()

                    indicator.text = XlsViewerBundle.message("diff.reading.right")
                    failureSide = SIDE_RIGHT
                    val rightBytes = XlsDiffContents.readBytes(rightContent)
                    val rightReadAt = System.nanoTime()
                    right = XlsParser.open(rightBytes)
                    val rightParsedAt = System.nanoTime()
                    failureSide = SIDE_NONE

                    LOG.info(
                        "XLS diff content: left ${leftBytes.size} B (read ${millis(leftReadAt - startedAt)} ms, " +
                            "parse ${millis(leftParsedAt - leftReadAt)} ms); right ${rightBytes.size} B " +
                            "(read ${millis(rightReadAt - leftParsedAt)} ms, parse ${millis(rightParsedAt - rightReadAt)} ms); " +
                            "total ${millis(rightParsedAt - startedAt)} ms",
                    )
                } catch (throwable: Throwable) {
                    failure = throwable
                    LOG.warn("XLS diff failed to load content (left=\"$leftTitle\", right=\"$rightTitle\")", throwable)
                }
            }

            override fun onSuccess() {
                if (disposed) return
                val leftDocument = left
                val rightDocument = right
                val error = failure
                when {
                    error != null -> showError(error)
                    leftDocument != null && rightDocument != null -> onLoaded(leftDocument, rightDocument)
                }
            }
        }.queue()
    }

    private fun millis(nanos: Long): Long = nanos / 1_000_000

    private fun onLoaded(left: XlsDocument, right: XlsDocument) {
        stopSlowHint()
        leftDocument = left
        rightDocument = right
        val startedAt = System.nanoTime()
        val diff = diffFor(left, right, showEmptyChanges)
        LOG.info(
            "XLS diff ready: ${left.sheets.size}/${right.sheets.size} sheet(s), " +
                "${diff.sheets.size} compared, ${diff.changedCellCount} changed cell(s), " +
                "${diff.ignoredEmptyCellCount} empty hidden, " +
                "diff took ${millis(System.nanoTime() - startedAt)} ms",
        )
        workbookDiff = diff
        populateTabs(diff)
        updateStats()
        val first = diff.sheets.indexOfFirst { it.hasChanges }.let { if (it >= 0) it else 0 }
        applySheet(first)
        showCard(CARD_DIFF)
    }

    private fun diffFor(left: XlsDocument, right: XlsDocument, showEmpty: Boolean): WorkbookDiff =
        diffCache.getOrPut(showEmpty) {
            WorkbookDiffer.compare(left, right, ignoreEmptyChanges = !showEmpty)
        }

    private fun showError(throwable: Throwable) {
        stopSlowHint()
        val isLfs = throwable is XlsLfsPointerException
        copyCommandButton.isVisible = isLfs
        workingCopyFile = if (isLfs) findWorkingCopy() else null
        openWorkingCopyButton.isVisible = workingCopyFile != null
        val title = when (throwable) {
            is XlsLfsPointerException -> XlsViewerBundle.message("error.lfs.title")
            is XlsEncryptedException -> XlsViewerBundle.message("error.encrypted.title")
            is NotAnXlsFileException -> XlsViewerBundle.message("error.notXls.title")
            else -> XlsViewerBundle.message("diff.error.title")
        }
        val text = when (throwable) {
            is XlsLfsPointerException -> XlsViewerBundle.message(
                "error.lfs.text",
                throwable.oid ?: "?",
                throwable.size?.toString() ?: "?",
            )

            is XlsEncryptedException -> XlsViewerBundle.message("error.encrypted.text")
            is NotAnXlsFileException -> XlsViewerBundle.message("error.notXls.text")
            is XlsParseException -> throwable.message ?: XlsViewerBundle.message("error.generic.text")
            else -> throwable.message ?: throwable.javaClass.simpleName
        }
        LOG.warn("XLS diff error: ${throwable.message}", throwable)
        errorTitle.text = title
        errorText.text = text
        errorText.caretPosition = 0
        showCard(CARD_ERROR)
    }

    // ------------------------------------------------------------------ diff state

    private fun applyDisplayOptions() {
        val diff = workbookDiff ?: return
        val currentHasChanges = diff.sheets.getOrNull(sheetIndex)?.hasChanges == true
        val target = if (!onlyDiffs || currentHasChanges) {
            sheetIndex
        } else {
            diff.sheets.indexOfFirst { it.hasChanges }.let { if (it >= 0) it else 0 }
        }
        populateTabs(diff)
        applySheet(target)
    }

    private fun populateTabs(diff: WorkbookDiff) {
        updatingUi = true
        sheetCombo.removeAllItems()
        val visible = DiffNavigation.visibleSheets(diff, onlyDiffs)
        val tabList = ArrayList<SheetTab>(visible.size)
        for (index in visible) {
            val sheet = diff.sheets[index]
            tabList += SheetTab(index, sheet.name, hidden = false, mark = sheet.mark)
            sheetCombo.addItem(SheetItem(index, sheet.name, sheet.mark))
        }
        tabs.sheets = tabList
        updatingUi = false
    }

    private fun applySheet(index: Int) {
        val diff = workbookDiff?.sheets?.getOrNull(index) ?: return
        sheetIndex = index
        val theme = XlsTheme.current(originalColors = false)
        leftGrid.setSheet(DiffSides.build(diff, leftSide = true, collapsed = onlyDiffs, context = diffContext), zoom)
        rightGrid.setSheet(DiffSides.build(diff, leftSide = false, collapsed = onlyDiffs, context = diffContext), zoom)
        leftGrid.setTheme(theme)
        rightGrid.setTheme(theme)
        tabs.theme = theme
        refreshViews()
        tabs.selectedIndex = index
        updatingUi = true
        for (itemIndex in 0 until sheetCombo.itemCount) {
            if (sheetCombo.getItemAt(itemIndex).index == index) {
                sheetCombo.selectedIndex = itemIndex
                break
            }
        }
        updatingUi = false
        selectFirstChange(diff)
        updateStatus()
    }

    private fun selectFirstChange(diff: SheetDiff) {
        val (row, col) = DiffNavigation.firstChange(diff)
        leftGrid.selectCell(row, col)
        rightGrid.selectCell(row, col)
    }

    private fun currentDiff(): SheetDiff? = workbookDiff?.sheets?.getOrNull(sheetIndex)

    private fun navigateChange(delta: Int) {
        val diff = currentDiff() ?: return
        val row = DiffNavigation.nextMarkedRow(diff, leftGrid.activeRow, delta) ?: return
        val (targetRow, targetCol) = DiffNavigation.cellForRow(diff, row)
        selectCell(targetRow, targetCol)
    }

    private fun navigateRow(delta: Int) {
        val diff = currentDiff() ?: return
        val row = DiffNavigation.nextMarkedRow(diff, leftGrid.activeRow, delta) ?: return
        val (targetRow, targetCol) = DiffNavigation.cellForRow(diff, row)
        selectCell(targetRow, targetCol)
    }

    private fun navigateColumn(delta: Int) {
        val diff = currentDiff() ?: return
        val col = DiffNavigation.nextMarkedColumn(diff, leftGrid.activeCol, delta) ?: return
        val (targetRow, targetCol) = DiffNavigation.cellForColumn(diff, col)
        selectCell(targetRow, targetCol)
    }

    private fun goToRow(row: Int) {
        val diff = currentDiff() ?: return
        val (targetRow, targetCol) = DiffNavigation.cellForRow(diff, row)
        selectCell(targetRow, targetCol)
    }

    private fun goToColumn(col: Int) {
        val diff = currentDiff() ?: return
        val (targetRow, targetCol) = DiffNavigation.cellForColumn(diff, col)
        selectCell(targetRow, targetCol)
    }

    private fun selectCell(row: Int, col: Int) {
        leftGrid.selectCell(row, col)
        rightGrid.selectCell(row, col)
        updateStatus()
    }

    private fun changeZoom(direction: Int) {
        val current = ZOOM_LEVELS.indexOfFirst { it == zoom }.let { if (it >= 0) it else ZOOM_LEVELS.indexOfFirst { level -> level == 1f } }
        val next = (current + direction).coerceIn(0, ZOOM_LEVELS.size - 1)
        zoom = ZOOM_LEVELS[next]
        applyZoom()
    }

    private fun applyZoom() {
        zoomLabel.text = "${(zoom * 100).roundToInt()}%"
        leftGrid.setZoom(zoom)
        rightGrid.setZoom(zoom)
        refreshViews()
        leftGrid.scrollToActive()
        updateStatus()
    }

    /**
     * After rebuilding the synthetic sheet models the headers and the corner must be
     * revalidated (their preferred size follows the grid) and repainted explicitly:
     * revalidate alone does not refresh the row numbers after a collapse toggle.
     */
    private fun refreshViews() {
        leftScroll.revalidate()
        rightScroll.revalidate()
        for (component in listOf(
            leftRowHeader,
            leftColumnHeader,
            leftCorner,
            rightRowHeader,
            rightColumnHeader,
            rightCorner,
        )) {
            component.revalidate()
            component.repaint()
        }
        tabs.repaint()
    }

    private fun installNavigationShortcuts() {
        val inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "xls-diff-next")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK), "xls-diff-previous")
        actionMap.put("xls-diff-next", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigateChange(1)
        })
        actionMap.put("xls-diff-previous", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigateChange(-1)
        })

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "xls-diff-prev-row")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "xls-diff-next-row")
        actionMap.put("xls-diff-prev-row", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigateRow(-1)
        })
        actionMap.put("xls-diff-next-row", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigateRow(1)
        })

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "xls-diff-prev-col")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "xls-diff-next-col")
        actionMap.put("xls-diff-prev-col", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigateColumn(-1)
        })
        actionMap.put("xls-diff-next-col", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigateColumn(1)
        })
    }

    private fun updateStats() {
        val diff = workbookDiff ?: return
        val changedRows = diff.sheets.sumOf { it.changedRowCount + it.addedRowCount + it.deletedRowCount }
        val changedColumns = diff.sheets.sumOf { it.changedColCount + it.addedColCount + it.deletedColCount }
        val stats = XlsViewerBundle.message(
            "diff.stats",
            diff.changedCellCount,
            changedRows,
            changedColumns,
            diff.changedSheetCount,
        )
        statsLabel.text = if (diff.ignoredEmptyCellCount > 0) {
            stats + " " + XlsViewerBundle.message("diff.stats.empty", diff.ignoredEmptyCellCount)
        } else {
            stats
        }
    }

    private fun updateStatus() {
        val row = leftGrid.activeRow
        val col = leftGrid.activeCol
        val before = shorten(leftGrid.activeCellModel()?.text)
        val after = shorten(rightGrid.activeCellModel()?.text)
        statusLabel.text = "${cellAddress(row, col)}   ${XlsViewerBundle.message("diff.before")}: $before   " +
            "${XlsViewerBundle.message("diff.after")}: $after"
    }

    private fun shorten(text: String?): String {
        val value = (text ?: "").replace('\n', ' ')
        return if (value.length > 80) value.take(77) + "\u2026" else value
    }

    override fun dispose() {
        disposed = true
        stopSlowHint()
        leftGrid.onSelectionChanged = null
        rightGrid.onSelectionChanged = null
    }

    private data class SheetItem(val index: Int, val name: String, val mark: DiffMark?) {
        override fun toString(): String {
            val escaped = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val mark = mark ?: return escaped
            val color = when (mark) {
                DiffMark.CHANGED -> "#D89E00"
                DiffMark.ADDED -> "#2C9A48"
                DiffMark.DELETED -> "#C93B3B"
            }
            return "<html>$escaped&nbsp;<font color='$color'>\u25CF</font></html>"
        }
    }

    private companion object {
        const val CARD_LOADING = "loading"
        const val CARD_ERROR = "error"
        const val CARD_DIFF = "diff"
        const val SLOW_HINT_DELAY_MS = 10_000
        const val SIDE_NONE = 0
        const val SIDE_LEFT = 1
        const val SIDE_RIGHT = 2

        val ZOOM_LEVELS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
    }
}
