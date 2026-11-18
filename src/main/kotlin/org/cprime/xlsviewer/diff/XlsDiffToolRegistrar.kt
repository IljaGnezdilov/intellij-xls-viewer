package org.cprime.xlsviewer.diff

import com.intellij.diff.DiffTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Puts [XlsDiffTool] in front of the built-in `diff.DiffTool` extensions.
 *
 * The platform opens the first tool whose `canShow` accepts the request. The built-in binary
 * viewer is registered by the platform itself, and `diff.DiffTool` does not support an `order`
 * attribute, so a plain `plugin.xml` registration would always come after it. Registering the
 * tool programmatically with [LoadingOrder.FIRST] is the supported way to take precedence.
 */
class XlsDiffToolRegistrar : ProjectActivity {

    override suspend fun execute(project: Project) {
        register()
    }

    /** Registers the tool once; safe to call from tests as well. */
    fun register() {
        val application = ApplicationManager.getApplication() ?: return
        if (registered) return
        registered = true
        DiffTool.EP_NAME.point.registerExtension(XlsDiffTool(), LoadingOrder.FIRST, application)
    }

    private companion object {
        @Volatile
        private var registered = false
    }
}
