package org.cprime.xlsviewer

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Application level settings: view options and the last viewed position per file.
 */
@State(name = "XlsViewerSettings", storages = [Storage("xlsViewer.xml")])
class XlsViewerSettings : PersistentStateComponent<XlsViewerSettings.State> {

    class State {
        var originalColors: Boolean = false
        var showGridlines: Boolean = true
        var onlyDiffs: Boolean = true
        var diffContext: Int = 1
        var showEmptyChanges: Boolean = false
        var files: MutableList<FileState> = mutableListOf()
    }

    class FileState {
        var path: String = ""
        var sheet: Int = 0
        var row: Int = 0
        var col: Int = 0
        var zoom: Float = 1f
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        myState.diffContext = myState.diffContext.coerceIn(0, 3)
    }

    fun fileState(path: String): FileState? = myState.files.firstOrNull { it.path == path }

    fun updateFile(path: String, sheet: Int, row: Int, col: Int, zoom: Float) {
        val existing = myState.files.firstOrNull { it.path == path }
        if (existing != null) {
            existing.sheet = sheet
            existing.row = row
            existing.col = col
            existing.zoom = zoom
            return
        }
        myState.files += FileState().apply {
            this.path = path
            this.sheet = sheet
            this.row = row
            this.col = col
            this.zoom = zoom
        }
        while (myState.files.size > MAX_TRACKED_FILES) {
            myState.files.removeAt(0)
        }
    }

    companion object {
        private const val MAX_TRACKED_FILES = 200

        fun getInstance(): XlsViewerSettings = service()
    }
}
