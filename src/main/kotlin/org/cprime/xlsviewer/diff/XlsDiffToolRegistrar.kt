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
        // `diff.DiffTool` change listeners require the EDT (an open diff window keeps one, see
        // `DiffRequestProcessor.readToolOrderFromSettings`), while project activities run on a
        // background coroutine. Register on the EDT so a runtime plugin install/update does not
        // throw "Access is allowed from Event Dispatch Thread (EDT) only".
        val action = Runnable {
            DiffTool.EP_NAME.point.registerExtension(XlsDiffTool(), LoadingOrder.FIRST, application)
        }
        if (application.isDispatchThread) action.run() else application.invokeAndWait(action)
    }

    private companion object {
        @Volatile
        private var registered = false
    }
}
