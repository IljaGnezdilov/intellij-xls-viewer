package org.cprime.xlsviewer

import com.intellij.DynamicBundle

/**
 * Localized UI strings. Defaults live in `messages/XlsViewerBundle.properties`,
 * Russian translations in `messages/XlsViewerBundle_ru.properties`.
 */
object XlsViewerBundle {
    private val bundle = DynamicBundle(XlsViewerBundle::class.java, "messages.XlsViewerBundle")

    fun message(key: String): String = bundle.getMessage(key)

    fun message(key: String, vararg params: Any): String = bundle.getMessage(key, *params)
}
