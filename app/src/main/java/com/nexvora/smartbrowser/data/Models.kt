package com.nexvora.smartbrowser.data

import kotlinx.serialization.Serializable

@Serializable data class Bookmark(val id: Long, val title: String, val url: String, val folder: String = "Unsorted", val added: Long = System.currentTimeMillis())
@Serializable data class HistoryEntry(val id: Long, val title: String, val url: String, val time: Long)
@Serializable data class Shortcut(val id: Long, val title: String, val url: String)
@Serializable data class ChatMsg(val id: Long, val fromUser: Boolean, val text: String, val time: Long = System.currentTimeMillis())

enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    CUSTOM("Custom", "")
}
