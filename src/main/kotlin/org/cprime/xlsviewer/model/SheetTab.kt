package org.cprime.xlsviewer.model

/** One tab in the bottom sheet strip. */
data class SheetTab(
    val index: Int,
    val name: String,
    val hidden: Boolean,
    /** Diff marker shown after the name, null in normal (non-diff) editors. */
    val mark: DiffMark? = null,
    /** ARGB strip along the bottom of the tab, as stored by Excel 2007+ workbooks. */
    val tabColor: Int? = null,
)
