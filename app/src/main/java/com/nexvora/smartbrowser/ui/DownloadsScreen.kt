package com.nexvora.smartbrowser.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.browser.DownloadItem
import com.nexvora.smartbrowser.browser.fmtSize
import kotlinx.coroutines.delay

@Composable
fun DownloadsScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current
    var items by remember { mutableStateOf(emptyList<DownloadItem>()) }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { items = app.downloads.list(); delay(if (items.any { it.active }) 1000 else 3000) } }
    val active = items.filter { it.active }; val done = items.filter { it.completed }; val failed = items.filter { it.failed }
    val shown = when (tab) { 0 -> active; 1 -> done; else -> failed }

    Column(Modifier.fillMaxSize()) {
        NxTopBar("Downloads", { nav.pop() })
        TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Active (${active.size})", "Completed (${done.size})", "Failed (${failed.size})").forEachIndexed { i, l -> Tab(tab == i, { tab = i }, text = { Text(l) }) }
        }
        Text(app.downloads.storageInfo() + ". Android manages downloads, so removing an item here also deletes its file. Pause/resume isn't offered by the system downloader.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (shown.isEmpty()) EmptyState(Icons.Filled.Download, when (tab) { 0 -> "No active downloads"; 1 -> "No completed downloads"; else -> "No failed downloads" }, "Files you download from websites appear here.")
        else LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { d ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (d.active) "${fmtSize(d.done)} / ${fmtSize(d.total)}" + (if (d.percent >= 0) " · ${d.percent}%" else "") else if (d.failed) "Failed (code ${d.reason})" else fmtSize(d.total),
                                style = MaterialTheme.typography.bodySmall, color = if (d.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (d.active) IconButton(onClick = { app.downloads.remove(d.id) }) { Icon(Icons.Filled.Cancel, "Cancel download") }
                        if (d.failed) { IconButton(onClick = { app.downloads.retry(d) }) { Icon(Icons.Filled.Refresh, "Retry download") }; IconButton(onClick = { app.downloads.remove(d.id) }) { Icon(Icons.Filled.Delete, "Remove") } }
                        if (d.completed) {
                            IconButton(onClick = { app.downloads.openIntent(d)?.let { i -> runCatching { ctx.startActivity(i) }.onFailure { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show() } } }) { Icon(Icons.Filled.OpenInNew, "Open file") }
                            IconButton(onClick = { app.downloads.shareIntent(d)?.let { runCatching { ctx.startActivity(it) } } }) { Icon(Icons.Filled.Share, "Share file") }
                            IconButton(onClick = { app.downloads.remove(d.id) }) { Icon(Icons.Filled.Delete, "Delete file") }
                        }
                    }
                    if (d.active) { if (d.percent >= 0) LinearProgressIndicator(progress = { d.percent / 100f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}
