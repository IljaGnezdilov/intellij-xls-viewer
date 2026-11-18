package org.cprime.xlsviewer.diff

import com.intellij.diff.DiffManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The built-in binary viewer is registered before any plugin tool, so the plugin has to put its
 * own tool in front of it programmatically. This test guards that ordering.
 */
class XlsDiffToolOrderTest : BasePlatformTestCase() {

    fun testToolIsRegisteredBeforeBinaryViewer() {
        XlsDiffToolRegistrar().register()

        val tools = DiffManagerEx.getInstance().getDiffTools()
        val names = tools.map { it.javaClass.simpleName }
        val ours = tools.indexOfFirst { it is XlsDiffTool }
        assertTrue("XlsDiffTool is not registered: $names", ours >= 0)

        val binary = tools.indexOfFirst { it.javaClass.name.endsWith("BinaryDiffTool") }
        assertTrue("Built-in BinaryDiffTool is missing, the test cannot verify the order: $names", binary >= 0)
        assertTrue("XlsDiffTool must be selected before BinaryDiffTool: $names", ours < binary)
    }
}
