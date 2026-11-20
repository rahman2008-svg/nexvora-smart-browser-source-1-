package com.nexvora.smartbrowser.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.browser.UrlUtils
import com.nexvora.smartbrowser.data.Bookmark
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current
    val all by app.repo.bookmarks.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<String?>(null) }
    var sortAz by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Bookmark?>(null) }
    var adding by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val json = remember { Json { prettyPrint = true; ignoreUnknownKeys = true } }
    val ser = remember { ListSerializer(Bookmark.serializer()) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { ctx.contentResolver.openOutputStream(it)!!.use { o -> o.write(json.encodeToString(ser, all).toByteArray()) }; Toast.makeText(ctx, "Exported ${all.size} bookmarks", Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(ctx, "Export failed", Toast.LENGTH_SHORT).show() } }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runCatching {
            val list = json.decodeFromString(ser, ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText())
            val have = app.repo.bookmarks.items.value.map { b -> b.url }.toSet()
            list.filter { b -> b.url !in have }.forEachIndexed { i, b -> app.repo.addBookmark(b.title, b.url, b.folder) }
            Toast.makeText(ctx, "Imported bookmarks", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(ctx, "That file isn't a NexVora bookmarks export", Toast.LENGTH_LONG).show() } }
    }

    val folders = all.map { it.folder }.distinct().sorted()
    val list = all.filter { (folder == null || it.folder == folder) && (query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true)) }
        .let { if (sortAz) it.sortedBy { b -> b.title.lowercase() } else it.sortedByDescending { b -> b.added } }

    Column(Modifier.fillMaxSize()) {
        NxTopBar("Bookmarks", { nav.pop() }) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add bookmark") }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    DropdownMenuItem(text = { Text(if (sortAz) "Sort by newest" else "Sort A–Z") }, onClick = { sortAz = !sortAz; sortMenu = false })
                    DropdownMenuItem(text = { Text("Export to file") }, onClick = { sortMenu = false; export.launch("nexvora-bookmarks.json") })
                    DropdownMenuItem(text = { Text("Import from file") }, onClick = { sortMenu = false; importFile.launch(arrayOf("application/json", "text/plain", "*/*")) })
                }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Search bookmarks") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true)
        if (folders.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(folder == null, { folder = null }, { Text("All") })
            folders.forEach { f -> FilterChip(folder == f, { folder = if (folder == f) null else f }, { Text(f) }) }
        }
        if (list.isEmpty()) EmptyState(Icons.Filled.Star, if (all.isEmpty()) "No bookmarks yet" else "No matches", if (all.isEmpty()) "Tap the star in the browser toolbar to save a page." else "Try a different search or folder.")
        else LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { b ->
                var m by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { openInput(app, nav, b.url) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SiteAvatar(b.title, b.url, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${b.folder} · ${UrlUtils.host(b.url)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Box {
                        IconButton(onClick = { m = true }) { Icon(Icons.Filled.MoreVert, "Options for ${b.title}") }
                        DropdownMenu(m, { m = false }) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { m = false; edit = b })
                            DropdownMenuItem(text = { Text("Share") }, onClick = { m = false; shareText(ctx, b.url, b.title) })
                            DropdownMenuItem(text = { Text("Add to Home") }, onClick = { m = false; app.repo.addShortcut(b.title, b.url); Toast.makeText(ctx, "Added to Home", Toast.LENGTH_SHORT).show() })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { m = false; app.repo.removeBookmark(b.id) })
                        }
                    }
                }
            }
        }
    }

    if (adding || edit != null) {
        val b = edit
        var t by remember(b) { mutableStateOf(b?.title ?: "") }; var u by remember(b) { mutableStateOf(b?.url ?: "") }; var f by remember(b) { mutableStateOf(b?.folder ?: (folder ?: "Unsorted")) }
        AlertDialog(onDismissRequest = { adding = false; edit = null }, title = { Text(if (b == null) "Add bookmark" else "Edit bookmark") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(u, { u = it }, label = { Text("Address") }, singleLine = true)
                OutlinedTextField(f, { f = it }, label = { Text("Folder") }, singleLine = true)
            } },
            confirmButton = { TextButton(enabled = u.isNotBlank(), onClick = {
                val url = UrlUtils.resolve(u, "https://www.google.com/search?q=%s")
                if (b == null) app.repo.addBookmark(t, url, f.ifBlank { "Unsorted" }) else app.repo.editBookmark(b.copy(title = t.ifBlank { url }, url = url, folder = f.ifBlank { "Unsorted" }))
                adding = false; edit = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { adding = false; edit = null }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val app = LocalApp.current; val nav = LocalNav.current
    val all by app.repo.history.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = all.filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
    val df = remember { DateFormat.getDateInstance(DateFormat.FULL) }; val tf = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val grouped = list.groupBy { df.format(Date(it.time)) }

    Column(Modifier.fillMaxSize()) {
        NxTopBar(if (selected.isEmpty()) "History" else "${selected.size} selected", { if (selected.isNotEmpty()) selected = emptySet() else nav.pop() }) {
            if (selected.isNotEmpty()) IconButton(onClick = { app.repo.removeHistory(selected); selected = emptySet() }) { Icon(Icons.Filled.Delete, "Delete selected") }
            else if (all.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Clear history") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Search history") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true)
        if (list.isEmpty()) EmptyState(Icons.Filled.History, if (all.isEmpty()) "No history" else "No matches", if (all.isEmpty()) "Pages you visit are listed here. Private tabs are never saved." else "Try a different search.")
        else LazyColumn(Modifier.weight(1f)) {
            grouped.forEach { (day, entries) ->
                item(key = "h$day") { Text(day, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
                items(entries, key = { it.id }) { h ->
                    val sel = h.id in selected
                    Row(Modifier.fillMaxWidth().combinedClickable(
                        onClick = { if (selected.isNotEmpty()) selected = if (sel) selected - h.id else selected + h.id else openInput(app, nav, h.url) },
                        onLongClick = { selected = selected + h.id }).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (selected.isNotEmpty()) Checkbox(sel, { selected = if (sel) selected - h.id else selected + h.id })
                        Column(Modifier.weight(1f)) {
                            Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${UrlUtils.host(h.url)} · ${tf.format(Date(h.time))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected.isEmpty()) IconButton(onClick = { app.repo.removeHistory(setOf(h.id)) }) { Icon(Icons.Filled.Close, "Delete ${h.title} from history") }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear all history?") }, text = { Text("This removes every saved page visit. Bookmarks and downloads aren't affected.") },
        confirmButton = { TextButton(onClick = { app.repo.clearHistory(); confirmClear = false }) { Text("Clear history", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
