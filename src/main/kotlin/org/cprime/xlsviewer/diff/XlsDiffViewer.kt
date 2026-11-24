package org.cprime.xlsviewer.diff

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.openapi.project.Project
import org.cprime.xlsviewer.XlsViewerBundle
import javax.swing.JComponent

/**
 * Our frame viewer for two `.xls`/`.xlsx` sides.
 */
class XlsDiffViewer(
    project: Project?,
    request: ContentDiffRequest,
) : FrameDiffTool.DiffViewer {

    private val panel = XlsDiffPanel(
        project = project,
        leftContent = request.contents[0],
        rightContent = request.contents[1],
        leftTitle = request.contentTitles.getOrNull(0) ?: XlsViewerBundle.message("diff.before"),
        rightTitle = request.contentTitles.getOrNull(1) ?: XlsViewerBundle.message("diff.after"),
    )

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel.preferredFocus

    override fun init(): FrameDiffTool.ToolbarComponents = FrameDiffTool.ToolbarComponents()

    override fun dispose() {
        panel.dispose()
    }
}
