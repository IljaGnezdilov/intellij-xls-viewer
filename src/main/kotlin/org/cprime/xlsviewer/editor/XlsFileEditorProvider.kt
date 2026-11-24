package org.cprime.xlsviewer.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

import org.cprime.xlsviewer.parse.isWorkbookExtension

/**
 * Claims `.xls` and `.xlsx` files without registering a competing
 * [com.intellij.openapi.fileTypes.FileType], so other Excel plugins are not disturbed.
 */
class XlsFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        !file.isDirectory && isWorkbookExtension(file.extension)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = XlsFileEditor(project, file)

    override fun getEditorTypeId(): String = "xls-viewer"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}
