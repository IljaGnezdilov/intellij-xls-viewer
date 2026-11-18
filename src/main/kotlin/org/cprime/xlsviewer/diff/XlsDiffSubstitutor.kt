package org.cprime.xlsviewer.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffTool
import com.intellij.diff.impl.DiffToolSubstitutor
import com.intellij.diff.requests.DiffRequest

/**
 * Replaces the generic binary/text diff viewer with [XlsDiffTool] for `.xls` pairs.
 *
 * The platform selects a tool from `com.intellij.diff.DiffTool` ordered by plugin load order,
 * where the built-in binary viewer comes first; a substitutor is the documented way to
 * override that choice for specific requests.
 */
class XlsDiffSubstitutor : DiffToolSubstitutor {

    override fun getReplacement(tool: DiffTool, context: DiffContext, request: DiffRequest): DiffTool? {
        if (tool is XlsDiffTool) return null
        if (!isGenericTool(tool)) return null
        return if (XlsDiffContents.canCompare(request)) XlsDiffTool() else null
    }

    /** Do not override a specialized viewer the user may have chosen explicitly. */
    private fun isGenericTool(tool: DiffTool): Boolean {
        val name = tool.javaClass.name
        return name.endsWith("BinaryDiffTool") ||
            name.endsWith("SimpleDiffTool") ||
            name.endsWith("ErrorDiffTool")
    }
}
