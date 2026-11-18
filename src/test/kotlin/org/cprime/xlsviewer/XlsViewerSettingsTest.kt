package org.cprime.xlsviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XlsViewerSettingsTest {

    @Test
    fun `diff defaults are collapsed with context one`() {
        val state = XlsViewerSettings.State()

        assertTrue(state.onlyDiffs, "only-changes mode should be enabled by default")
        assertEquals(1, state.diffContext, "default context should be one row/column")
    }
}
