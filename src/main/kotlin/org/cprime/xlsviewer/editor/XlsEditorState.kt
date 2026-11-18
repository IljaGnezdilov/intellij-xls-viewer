package org.cprime.xlsviewer.editor

import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel

/** In-session editor state: selected sheet, caret and zoom. */
class XlsEditorState(
    val sheet: Int,
    val row: Int,
    val col: Int,
    val zoom: Float,
) : FileEditorState, java.io.Serializable {
    override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean = false
}
