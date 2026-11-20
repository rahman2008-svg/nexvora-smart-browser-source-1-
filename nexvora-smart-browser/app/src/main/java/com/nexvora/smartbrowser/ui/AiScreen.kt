package com.nexvora.smartbrowser.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.ai.*
import com.nexvora.smartbrowser.data.ChatMsg
import kotlinx.coroutines.launch

@Composable
fun AiScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val state by app.engine.state.collectAsState()
    val aiOn by app.settings.aiEnabled.flow.collectAsState()
    val chat by app.repo.chat.items.collectAsState()
    var input by remember { mutableStateOf("") }
    var usePage by remember { mutableStateOf(true) }
    val list = rememberLazyListState()
    val generating = state is AiState.Generating
    val ready = state is AiState.Ready || generating

    LaunchedEffect(chat.size, chat.lastOrNull()?.text?.length) { if (chat.isNotEmpty()) list.scrollToItem(chat.lastIndex) }

    fun send(text: String, page: PageContext?, shown: String = text) {
        if (!ready) { Toast.makeText(ctx, "Load a model first (see the status card).", Toast.LENGTH_SHORT).show(); return }
        app.ai.ask(text, page, shown)
    }
    // requests coming from the browser menu
    LaunchedEffect(nav.pendingAi, ready) {
        val p = nav.pendingAi
        if (p != null && ready && !generating) { nav.pendingAi = null; send(p.prompt, p.page, p.shown) }
    }
    suspend fun currentPage(): PageContext? {
        val t = app.tabs.current ?: return null
        if (t.isHome) return null
        val txt = app.tabs.pageText(t, app.engine.pageBudgetChars()) ?: return null
        return PageContext(t.title, t.url, txt)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Nx.brand), contentAlignment = Alignment.Center) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White) }
            Column(Modifier.weight(1f)) { Text("NexVora AI", style = MaterialTheme.typography.titleMedium); Text("Runs on your device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (chat.isNotEmpty()) IconButton(onClick = { if (generating) app.ai.stop(); app.repo.clearChat() }) { Icon(Icons.Filled.DeleteSweep, "Clear chat") }
        }
        if (!aiOn) {
            EmptyState(Icons.Filled.AutoAwesome, "AI is turned off", "Browsing works as normal. Turn AI on in Settings to use summaries and chat.") { GradientButton("Open AI settings") { nav.push(Screen.Settings("ai")) } }
            return@Column
        }
        ModelStatusCard(state, onSetup = { nav.push(Screen.Settings("ai")) }, onLoad = { app.engine.load() })

        if (chat.isEmpty()) {
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text("Hello! How can I help you today?", style = MaterialTheme.typography.titleMedium)
                Text("Pick an action for the page you're viewing, or ask anything.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                AiAction.values().forEach { a ->
                    OutlinedButton(onClick = { scope.launch { val p = currentPage(); if (p == null) Toast.makeText(ctx, "Open a web page first.", Toast.LENGTH_SHORT).show() else send(app.ai.instruction(a), p, a.label) } },
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 48.dp)) { Text(a.label, Modifier.fillMaxWidth()) }
                }
            }
        } else LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(chat, key = { it.id }) { m -> Bubble(m, streaming = generating && m.id == chat.last().id, onRetry = { app.ai.retry() }, isLast = m.id == chat.last().id && !m.fromUser) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(usePage, { usePage = !usePage }, { Text("Use current page") }, leadingIcon = { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)) })
        }
        Row(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Type your message…") }, maxLines = 4, shape = RoundedCornerShape(24.dp))
            if (generating) FilledIconButton(onClick = { app.ai.stop() }, Modifier.size(48.dp)) { Icon(Icons.Filled.Stop, "Stop generating") }
            else FilledIconButton(onClick = {
                val q = input.trim(); if (q.isEmpty()) return@FilledIconButton
                input = ""
                scope.launch { send(q, if (usePage) currentPage() else null) }
            }, Modifier.size(48.dp)) { Icon(Icons.Filled.Send, "Send message") }
        }
    }
}

@Composable
private fun ModelStatusCard(state: AiState, onSetup: () -> Unit, onLoad: () -> Unit) {
    val (label, action) = when (state) {
        AiState.NoModel -> "No model installed. Import or download one to start." to ("Set up model" to onSetup)
        is AiState.Downloading -> "Downloading model… ${state.percent}%" to null
        is AiState.Importing -> "Importing model… ${state.percent}%" to null
        is AiState.Installed -> "Model installed: ${state.name}" to ("Load model" to onLoad)
        AiState.Loading -> "Loading model into memory…" to null
        is AiState.Ready -> "Model ready: ${state.name}" to null
        AiState.Generating -> "Generating…" to null
        is AiState.Error -> state.message to ("Model settings" to onSetup)
    }
    if (state is AiState.Ready || state is AiState.Generating) return
    NxCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = if (state is AiState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (state is AiState.Downloading || state is AiState.Importing || state is AiState.Loading) {
                val p = (state as? AiState.Downloading)?.percent ?: (state as? AiState.Importing)?.percent
                if (p != null) LinearProgressIndicator(progress = { p / 100f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            action?.let { (t, f) -> TextButton(onClick = f) { Text(t) } }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg, streaming: Boolean, isLast: Boolean, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val bg = if (m.fromUser) Nx.Purple.copy(alpha = .35f) else MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Column(Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(18.dp)).background(bg).padding(12.dp)) {
            if (m.text.isEmpty() && streaming) LinearProgressIndicator(Modifier.width(80.dp))
            else SelectionContainer { Markdown(m.text) }
        }
        if (!m.fromUser && m.text.isNotEmpty() && !streaming) Row {
            IconButton(onClick = { copyText(ctx, m.text) }, Modifier.size(40.dp)) { Icon(Icons.Filled.ContentCopy, "Copy response", Modifier.size(18.dp)) }
            if (isLast) IconButton(onClick = onRetry, Modifier.size(40.dp)) { Icon(Icons.Filled.Refresh, "Retry", Modifier.size(18.dp)) }
        }
    }
}

/** Small Markdown subset: headings, bullets, numbered lists, **bold**, `code`, ``` fenced code with a copy button. */
@Composable
fun Markdown(text: String) {
    val ctx = LocalContext.current
    val lines = text.lines(); var i = 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        while (i < lines.size) {
            val l = lines[i]
            if (l.trimStart().startsWith("```")) {
                val code = StringBuilder(); i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.appendLine(lines[i]); i++ }
                i++
                val c = code.toString().trimEnd()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .35f)).padding(10.dp)) {
                    Text(c, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { copyText(ctx, c) }) { Text("Copy code") }
                }
                continue
            }
            val t = l.trimStart()
            when {
                t.startsWith("#") -> Text(inline(t.trimStart('#').trim()), style = MaterialTheme.typography.titleMedium)
                t.startsWith("- ") || t.startsWith("* ") -> Text(inline("•  " + t.drop(2)), Modifier.padding(start = 8.dp))
                Regex("^\\d+[.)] ").containsMatchIn(t) -> Text(inline(t), Modifier.padding(start = 8.dp))
                t.isEmpty() -> Spacer(Modifier.height(2.dp))
                else -> Text(inline(l))
            }
            i++
        }
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) && s.indexOf("**", i + 2) > 0 -> { val e = s.indexOf("**", i + 2); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, e)) }; i = e + 2 }
            s[i] == '`' && s.indexOf('`', i + 1) > 0 -> { val e = s.indexOf('`', i + 1); withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Black.copy(alpha = .3f))) { append(s.substring(i + 1, e)) }; i = e + 1 }
            else -> { append(s[i]); i++ }
        }
    }
}
