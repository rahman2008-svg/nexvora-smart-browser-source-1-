package com.nexvora.smartbrowser.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Tiny JSON-file backed list with a StateFlow. Writes are serialised on a single IO coroutine. */
class JsonStore<T>(private val file: File, itemSer: KSerializer<T>, private val scope: CoroutineScope, private val cap: Int = Int.MAX_VALUE) {
    private val ser = ListSerializer(itemSer)
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow<List<T>>(
        runCatching { if (file.exists()) json.decodeFromString(ser, file.readText()) else emptyList() }.getOrDefault(emptyList())
    )
    val items: StateFlow<List<T>> = _items
    private var saveJob: Job? = null

    fun update(block: (List<T>) -> List<T>) {
        _items.value = block(_items.value).let { if (it.size > cap) it.take(cap) else it }
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(250)
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(ser, _items.value)); tmp.renameTo(file)
            }
        }
    }
}

class Repository(ctx: Context, scope: CoroutineScope) {
    private val dir = File(ctx.filesDir, "data").apply { mkdirs() }
    val bookmarks = JsonStore(File(dir, "bookmarks.json"), Bookmark.serializer(), scope)
    val history = JsonStore(File(dir, "history.json"), HistoryEntry.serializer(), scope, cap = 5000)
    val shortcuts = JsonStore(File(dir, "shortcuts.json"), Shortcut.serializer(), scope)
    val chat = JsonStore(File(dir, "chat.json"), ChatMsg.serializer(), scope, cap = 500)

    private fun id() = System.nanoTime()

    fun isBookmarked(url: String) = bookmarks.items.value.any { it.url == url }
    fun toggleBookmark(title: String, url: String) = bookmarks.update { l ->
        if (l.any { it.url == url }) l.filterNot { it.url == url } else l + Bookmark(id(), title.ifBlank { url }, url)
    }
    fun addBookmark(title: String, url: String, folder: String) = bookmarks.update { it + Bookmark(id(), title.ifBlank { url }, url, folder) }
    fun editBookmark(b: Bookmark) = bookmarks.update { l -> l.map { if (it.id == b.id) b else it } }
    fun removeBookmark(id: Long) = bookmarks.update { l -> l.filterNot { it.id == id } }

    fun addHistory(title: String, url: String) = history.update { l ->
        // collapse immediate repeats of the same URL
        if (l.firstOrNull()?.url == url) listOf(l.first().copy(title = title.ifBlank { l.first().title }, time = System.currentTimeMillis())) + l.drop(1)
        else listOf(HistoryEntry(id(), title.ifBlank { url }, url, System.currentTimeMillis())) + l
    }
    fun removeHistory(ids: Set<Long>) = history.update { l -> l.filterNot { it.id in ids } }
    fun clearHistory() = history.update { emptyList() }

    fun addShortcut(title: String, url: String) = shortcuts.update { it + Shortcut(id(), title.ifBlank { url }, url) }
    fun removeShortcut(id: Long) = shortcuts.update { l -> l.filterNot { it.id == id } }
    fun moveShortcut(id: Long, delta: Int) = shortcuts.update { l ->
        val i = l.indexOfFirst { it.id == id }; val j = i + delta
        if (i < 0 || j !in l.indices) l else l.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun addChat(fromUser: Boolean, text: String): Long { val m = ChatMsg(id(), fromUser, text); chat.update { it + m }; return m.id }
    fun updateChat(id: Long, text: String) = chat.update { l -> l.map { if (it.id == id) it.copy(text = text) else it } }
    fun clearChat() = chat.update { emptyList() }
}
