package com.nexvora.smartbrowser.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.browser.BrowserTab
import com.nexvora.smartbrowser.browser.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val tm = app.tabs
    var privateView by remember { mutableStateOf(tm.current?.isPrivate == true) }
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = tm.tabs.filter { it.isPrivate == privateView && (query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true)) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tabs (${shown.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Tab options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("New private tab") }, onClick = { menu = false; val t = tm.newTab(isPrivate = true); tm.select(t.id); nav.root(Screen.Home); nav.focusAddress = true })
                    if (tm.canRestore) DropdownMenuItem(text = { Text("Reopen closed tab") }, onClick = { menu = false; tm.restoreClosed()?.let { nav.root(Screen.Browser) } })
                    DropdownMenuItem(text = { Text("Close all tabs") }, onClick = { menu = false; tm.closeAll() })
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SegmentedButton(!privateView, { privateView = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Normal (${tm.tabs.count { !it.isPrivate }})") }
            SegmentedButton(privateView, { privateView = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Private (${tm.privateCount})") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), placeholder = { Text("Search tabs") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true)
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { t -> TabCard(t, t.id == tm.currentId, onOpen = { tm.select(t.id); if (t.isHome) { nav.root(Screen.Home) } else nav.root(Screen.Browser) }, onClose = { tm.close(t.id) }) }
            item {
                Box(Modifier.aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .clickable { val t = tm.newTab(isPrivate = privateView); tm.select(t.id); nav.root(Screen.Home); nav.focusAddress = true }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.Add, null, Modifier.size(32.dp)); Text(if (privateView) "New private tab" else "New tab") }
                }
            }
        }
        if (shown.isNotEmpty()) TextButton(onClick = { tm.closeAll(privateOnly = privateView) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Filled.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text(if (privateView) "Close all private tabs" else "Close all tabs")
        }
    }
}

@Composable
private fun TabCard(t: BrowserTab, selected: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    NxCard(Modifier.aspectRatio(0.8f).border(if (selected) 2.dp else 0.dp, if (selected) Nx.Cyan else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(18.dp)), onClick = onOpen) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (t.isPrivate) Icon(Icons.Filled.VisibilityOff, "Private tab", Modifier.size(16.dp), tint = Nx.Purple2)
            else t.favicon?.let { Image(it.asImageBitmap(), null, Modifier.size(16.dp)) }
            Text(t.title, Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onClose, Modifier.size(40.dp)) { Icon(Icons.Filled.Close, "Close tab ${t.title}", Modifier.size(18.dp)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val th = t.thumb
            if (th != null) Image(th.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            else SiteAvatar(t.title, t.url, 64.dp)
        }
        Text(if (t.isHome) "New tab" else UrlUtils.host(t.url).ifEmpty { t.url }, Modifier.padding(10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
