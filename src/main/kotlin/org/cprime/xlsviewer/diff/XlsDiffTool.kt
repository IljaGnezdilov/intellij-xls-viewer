package org.cprime.xlsviewer.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.project.DumbAware
import org.cprime.xlsviewer.XlsViewerBundle

/**
 * Diff tool for legacy Excel workbooks. Registered as `com.intellij.diff.DiffTool` and
 * additionally substituted over the built-in binary diff tool (see [XlsDiffSubstitutor]).
 */
class XlsDiffTool : FrameDiffTool, DumbAware {

    override fun getName(): String = XlsViewerBundle.message("diff.tool.name")

    override fun canShow(context: DiffContext, request: DiffRequest): Boolean =
        XlsDiffContents.canCompare(request)

    override fun createComponent(context: DiffContext, request: DiffRequest): FrameDiffTool.DiffViewer {
        if (request !is ContentDiffRequest) {
            throw IllegalArgumentException("Unsupported diff request: ${request.javaClass.name}")
        }
        return XlsDiffViewer(context.project, request)
    }
}
