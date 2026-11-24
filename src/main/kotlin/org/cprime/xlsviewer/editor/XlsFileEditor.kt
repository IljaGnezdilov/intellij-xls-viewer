package org.cprime.xlsviewer.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

/**
 * Read-only spreadsheet editor for a single `.xls` or `.xlsx` file.
 */
class XlsFileEditor(
    project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor, DumbAware {

    private val panel = XlsEditorPanel(project, file)
    private val listeners = PropertyChangeSupport(this)

    override fun getComponent(): JComponent = panel

    override fun getFile(): VirtualFile = file

    override fun getPreferredFocusedComponent(): JComponent = panel.preferredFocus

    override fun getName(): String = "XLS Viewer"

    override fun getState(level: FileEditorStateLevel): FileEditorState = panel.captureState()

    override fun setState(state: FileEditorState) = panel.restoreState(state)

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        listeners.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        listeners.removePropertyChangeListener(listener)
    }

    override fun dispose() {
        panel.dispose()
    }
}
