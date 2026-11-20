package com.nexvora.smartbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexvora.smartbrowser.ai.AiAction
import com.nexvora.smartbrowser.ai.AiState
import com.nexvora.smartbrowser.ai.PageContext

@Composable
fun ReaderScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current
    val tab = app.tabs.current
    val engine by app.engine.state.collectAsState()
    var fontSize by remember { mutableFloatStateOf(18f) }
    var spacing by remember { mutableFloatStateOf(1.6f) }
    var width by remember { mutableIntStateOf(1) }       // 0 narrow, 1 normal, 2 wide
    var theme by remember { mutableIntStateOf(0) }       // 0 dark, 1 light, 2 sepia
    var panel by remember { mutableStateOf(false) }

    if (tab == null || tab.isHome) {
        Column(Modifier.fillMaxSize()) { NxTopBar("Reader mode", { nav.pop() }); EmptyState(Icons.Filled.AutoStories, "No page to read", "Open an article in the browser, then choose Reader mode.") }
        return
    }
    val content by produceState<Pair<String, String>?>(null, tab.id, tab.url) { value = app.tabs.readerContent(tab) }
    var loaded by remember(tab.id, tab.url) { mutableStateOf(false) }
    LaunchedEffect(content) { loaded = true }

    val bg = when (theme) { 1 -> Color(0xFFFFFFFF); 2 -> Color(0xFFF4ECD8); else -> Color(0xFF0B1020) }
    val fg = when (theme) { 1 -> Color(0xFF1A1A1A); 2 -> Color(0xFF433422); else -> Color(0xFFE6E8F5) }
    val marks by app.repo.bookmarks.items.collectAsState()

    Column(Modifier.fillMaxSize().background(bg)) {
        NxTopBar("Reader mode", { nav.pop() }) {
            val c = content
            if (c != null) {
                IconButton(onClick = { copyText(ctx, c.first + "\n\n" + c.second) }) { Icon(Icons.Filled.ContentCopy, "Copy text") }
                IconButton(onClick = { shareText(ctx, c.first + "\n" + tab.url + "\n\n" + c.second, c.first) }) { Icon(Icons.Filled.Share, "Share") }
                IconButton(onClick = { app.repo.toggleBookmark(tab.title, tab.url) }) { Icon(if (marks.any { it.url == tab.url }) Icons.Filled.Star else Icons.Filled.StarBorder, "Bookmark") }
                if (engine is AiState.Ready) IconButton(onClick = { nav.pendingAi = PendingAi(app.ai.instruction(AiAction.SUMMARIZE), "Summarize this article", PageContext(c.first, tab.url, c.second.take(app.engine.pageBudgetChars()))); nav.push(Screen.Ai) }) { Icon(Icons.Filled.AutoAwesome, "Summarize with AI") }
            }
            IconButton(onClick = { panel = !panel }) { Icon(Icons.Filled.TextFormat, "Reading settings") }
        }
        if (panel) Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Text size", color = fg); Slider(fontSize, { fontSize = it }, valueRange = 14f..30f)
            Text("Line spacing", color = fg); Slider(spacing, { spacing = it }, valueRange = 1.2f..2.2f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Narrow", "Normal", "Wide").forEachIndexed { i, l -> FilterChip(width == i, { width = i }, { Text(l) }) }
                listOf("Dark", "Light", "Sepia").forEachIndexed { i, l -> FilterChip(theme == i, { theme = i }, { Text(l) }) }
            }
        }
        when {
            !loaded && content == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            content == null -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Reader mode can't read this page", style = MaterialTheme.typography.titleMedium, color = fg)
                Text("It doesn't look like an article, or it hasn't finished loading. Go back to the page and try again.", color = fg.copy(alpha = .7f), modifier = Modifier.padding(top = 8.dp))
            }
            else -> {
                val c = content!!
                val maxW = when (width) { 0 -> 480.dp; 2 -> 760.dp; else -> 620.dp }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = maxW)) {
                        Text(c.first, color = fg, fontSize = (fontSize + 6).sp, fontWeight = FontWeight.Bold, lineHeight = ((fontSize + 6) * 1.25f).sp)
                        Text(tab.url, color = fg.copy(alpha = .55f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                        c.second.split("\n\n").forEach { p ->
                            if (p.startsWith("## ")) Text(p.drop(3), color = fg, fontSize = (fontSize + 2).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                            else Text(p, color = fg, fontSize = fontSize.sp, lineHeight = (fontSize * spacing).sp, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
