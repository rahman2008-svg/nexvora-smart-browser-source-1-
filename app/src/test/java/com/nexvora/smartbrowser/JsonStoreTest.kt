package com.nexvora.smartbrowser

import com.nexvora.smartbrowser.data.Bookmark
import com.nexvora.smartbrowser.data.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JsonStoreTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun tmp() = File.createTempFile("nx", ".json").also { it.delete() }

    @Test fun startsEmptyWhenNoFile() = assertTrue(JsonStore(tmp(), Bookmark.serializer(), scope).items.value.isEmpty())

    @Test fun addEditRemoveInMemory() {
        val s = JsonStore(tmp(), Bookmark.serializer(), scope)
        s.update { it + Bookmark(1, "A", "https://a.com") + Bookmark(2, "B", "https://b.com") }
        s.update { l -> l.map { if (it.id == 1L) it.copy(title = "A2") else it } }
        s.update { l -> l.filterNot { it.id == 2L } }
        assertEquals(listOf("A2"), s.items.value.map { it.title })
    }

    @Test fun persistsToDiskAndReloads() {
        val f = tmp()
        JsonStore(f, Bookmark.serializer(), scope).update { it + Bookmark(7, "Saved", "https://s.com", "Work") }
        Thread.sleep(1000)
        val again = JsonStore(f, Bookmark.serializer(), scope).items.value
        assertEquals(1, again.size); assertEquals("Saved", again[0].title); assertEquals("Work", again[0].folder)
    }

    @Test fun capLimitsSize() {
        val s = JsonStore(tmp(), Bookmark.serializer(), scope, cap = 2)
        s.update { listOf(Bookmark(1, "1", "u1"), Bookmark(2, "2", "u2"), Bookmark(3, "3", "u3")) }
        assertEquals(2, s.items.value.size)
    }

    @Test fun corruptFileFallsBackToEmpty() {
        val f = tmp(); f.writeText("{ not json")
        assertTrue(JsonStore(f, Bookmark.serializer(), scope).items.value.isEmpty())
    }
}
