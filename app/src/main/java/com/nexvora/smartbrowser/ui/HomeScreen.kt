package com.nexvora.smartbrowser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.browser.UrlUtils
import com.nexvora.smartbrowser.data.SearchEngine
import com.nexvora.smartbrowser.data.Shortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reusable address / search field with engine selector, paste/clear/copy and voice. */
@Composable
fun AddressField(
    value: String, onValue: (String) -> Unit, onSubmit: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "Search or enter URL", focus: FocusRequester? = null, onFocus: (Boolean) -> Unit = {},
    leading: @Composable (() -> Unit)? = null, showEngine: Boolean = true,
) {
    val app = LocalApp.current; val main = LocalMain.current; val ctx = LocalContext.current
    val engineName by app.settings.searchEngine.flow.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp)).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else if (showEngine) Box {
            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(SearchEngine.valueOf(engineName).label.take(2), color = MaterialTheme.colorScheme.secondary)
                Icon(Icons.Filled.ArrowDropDown, "Search engine: ${SearchEngine.valueOf(engineName).label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(menu, { menu = false }) {
                SearchEngine.values().forEach { e ->
                    DropdownMenuItem(
                        text = { Text(e.label + if (e.name == engineName) "  ✓" else "") },
                        onClick = { menu = false; if (e == SearchEngine.CUSTOM && !app.settings.customEngine.value.contains("%s")) android.widget.Toast.makeText(ctx, "Set a custom search URL (with %s) in Settings > Browser first", android.widget.Toast.LENGTH_LONG).show() else app.settings.searchEngine.value = e.name },
                    )
                }
            }
        }
        BasicTextField(
            value, onValue, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(value) }),
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp).let { if (focus != null) it.focusRequester(focus) else it }.onFocusChanged { onFocus(it.isFocused) },
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.outline); inner() } },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("input", value)) }) { Icon(Icons.Filled.ContentCopy, "Copy input", Modifier.size(20.dp)) }
            IconButton(onClick = { onValue("") }) { Icon(Icons.Filled.Close, "Clear input") }
        } else {
            IconButton(onClick = {
                val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (!clip.isNullOrBlank()) onValue(clip.trim())
            }) { Icon(Icons.Filled.ContentPaste, "Paste", Modifier.size(20.dp)) }
        }
        IconButton(onClick = { main.voiceSearch { onValue(it); onSubmit(it) } }) { Icon(Icons.Filled.Mic, "Voice search", tint = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
fun SuggestionList(query: String, onPick: (String) -> Unit) {
    val app = LocalApp.current
    val history by app.repo.history.items.collectAsState(); val marks by app.repo.bookmarks.items.collectAsState()
    val q = query.trim().lowercase()
    if (q.isEmpty()) return
    val items = remember(q, history, marks) {
        (marks.map { it.title to it.url } + history.take(500).map { it.title to it.url })
            .filter { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }.distinctBy { it.second }.take(6)
    }
    NxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { onPick(query) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.secondary); Text("Search or open “$query”", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        items.forEach { (t, u) ->
            Row(Modifier.fillMaxWidth().clickable { onPick(u) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.outline)
                Column { Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium); Text(UrlUtils.host(u), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun SiteAvatar(title: String, url: String, size: androidx.compose.ui.unit.Dp = 52.dp) {
    val letter = (UrlUtils.host(url).ifEmpty { title }).firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Nx.Purple.copy(.45f), Nx.Cyan.copy(.35f)))), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current
    val s = app.settings
    val shortcuts by app.repo.shortcuts.items.collectAsState(); val history by app.repo.history.items.collectAsState(); val marks by app.repo.bookmarks.items.collectAsState()
    val showRecent by s.homeRecent.flow.collectAsState(); val showMarks by s.homeBookmarks.flow.collectAsState(); val wallOn by s.wallpaper.flow.collectAsState()
    val isPrivate = app.tabs.current?.isPrivate == true
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Shortcut?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(nav.focusAddress) { if (nav.focusAddress) { runCatching { focus.requestFocus() }; nav.focusAddress = false } }

    val wallpaper by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, wallOn) {
        value = if (!wallOn) null else withContext(Dispatchers.IO) {
            runCatching { File(ctx.filesDir, "wallpaper.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }.getOrNull()
        }
    }
    val submit = { input: String -> openInput(app, nav, input, isPrivate = isPrivate) }

    Box(Modifier.fillMaxSize()) {
        wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = .72f))) }
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NxLogo(84.dp)
                    Text("NexVora", style = MaterialTheme.typography.headlineMedium)
                    Text("Smart AI Browser", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                    if (isPrivate) AssistChip(onClick = {}, label = { Text("Private tab") }, leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) }, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item { AddressField(text, { text = it }, submit, Modifier.fillMaxWidth(), focus = focus, onFocus = { focused = it }) }
            if (focused && text.isNotBlank()) item { SuggestionList(text, submit) }
            item {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Shortcuts", Modifier.weight(1f))
                        TextButton(onClick = { addDialog = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add") }
                    }
                    if (shortcuts.isEmpty()) {
                        NxCard(Modifier.fillMaxWidth(), onClick = { addDialog = true }) {
                            Text("Add your favourite sites here for one-tap access.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        shortcuts.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                row.forEach { sc ->
                                    Column(Modifier.weight(1f).combinedClickable(onClick = { submit(sc.url) }, onLongClick = { menuFor = sc }).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        SiteAvatar(sc.title, sc.url); Text(sc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Text("Long-press a shortcut to remove or move it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Nx.Purple.copy(.55f), Nx.Blue.copy(.35f)))).clickable { nav.root(Screen.Ai) }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White) }
                        Column(Modifier.weight(1f)) { Text("Ask AI anything", color = Color.White, style = MaterialTheme.typography.titleMedium); Text("Summaries, explanations and notes, on your device", color = Color.White.copy(.8f), style = MaterialTheme.typography.bodySmall) }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                    }
                }
            }
            item {
                SectionTitle("Quick actions")
                val actions = listOf(
                    Triple(Icons.Filled.Download, "Downloads", { nav.push(Screen.Downloads) }), Triple(Icons.Filled.Star, "Bookmarks", { nav.push(Screen.Bookmarks) }),
                    Triple(Icons.Filled.History, "History", { nav.push(Screen.History) }), Triple(Icons.Filled.AutoStories, "Reader mode", { nav.push(Screen.Reader) }), Triple(Icons.Filled.QrCodeScanner, "Scan QR", { nav.push(Screen.QrScanner) }),
                    Triple(Icons.Filled.VisibilityOff, "Private tab", { val t = app.tabs.newTab(isPrivate = true); app.tabs.select(t.id); nav.root(Screen.Home); nav.focusAddress = true }),
                    Triple(Icons.Filled.Settings, "Settings", { nav.push(Screen.Settings()) }),
                )
                actions.chunked(2).forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.forEach { (i, l, a) -> IconTile(i, l, Modifier.weight(1f), onClick = a) }
                    }
                }
            }
            if (showMarks) item {
                SectionTitle("Bookmarks")
                if (marks.isEmpty()) Text("Bookmarks you save appear here.", Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Row(Modifier.fillMaxWidth()) { marks.take(4).forEach { b -> Column(Modifier.weight(1f).clickable { submit(b.url) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) { SiteAvatar(b.title, b.url, 44.dp); Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) } }; repeat(4 - minOf(4, marks.size)) { Spacer(Modifier.weight(1f)) } }
            }
            if (showRecent) item {
                SectionTitle("Recent browsing")
                if (history.isEmpty()) NxCard(Modifier.fillMaxWidth()) { Text("Pages you visit show up here. Private tabs are never listed.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else NxCard(Modifier.fillMaxWidth()) {
                    history.take(5).forEach { h ->
                        Row(Modifier.fillMaxWidth().clickable { submit(h.url) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SiteAvatar(h.title, h.url, 36.dp)
                            Column { Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium); Text(UrlUtils.host(h.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }

    if (addDialog) {
        var t by remember { mutableStateOf("") }; var u by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addDialog = false }, title = { Text("Add shortcut") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(t, { t = it }, label = { Text("Name") }, singleLine = true); OutlinedTextField(u, { u = it }, label = { Text("Website address") }, singleLine = true) } },
            confirmButton = { TextButton(enabled = u.isNotBlank(), onClick = { val url = UrlUtils.resolve(u, "https://www.google.com/search?q=%s"); app.repo.addShortcut(t.ifBlank { UrlUtils.host(url) }, url); addDialog = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } })
    }
    menuFor?.let { sc ->
        AlertDialog(onDismissRequest = { menuFor = null }, title = { Text(sc.title) },
            text = { Column {
                TextButton(onClick = { app.repo.moveShortcut(sc.id, -1); menuFor = null }) { Text("Move earlier") }
                TextButton(onClick = { app.repo.moveShortcut(sc.id, 1); menuFor = null }) { Text("Move later") }
                TextButton(onClick = { app.repo.removeShortcut(sc.id); menuFor = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Close") } })
    }
}
