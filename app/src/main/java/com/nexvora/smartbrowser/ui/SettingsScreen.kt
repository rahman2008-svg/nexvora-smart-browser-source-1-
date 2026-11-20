package com.nexvora.smartbrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.ai.AiState
import com.nexvora.smartbrowser.data.SearchEngine
import com.nexvora.smartbrowser.browser.fmtSize
import java.io.File

@Composable
private fun Group(title: String) = Text(title, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) =
    SettingRow(label, trailing = { RadioButton(selected, null) }, onClick = onClick)

@Composable
fun SettingsScreen(section: String?) {
    val nav = LocalNav.current
    val title = mapOf("appearance" to "Appearance", "browser" to "Browser", "privacy" to "Privacy & security", "ai" to "AI features", "advanced" to "Advanced")[section] ?: "Settings"
    Column(Modifier.fillMaxSize()) {
        NxTopBar(title, { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            when (section) {
                null -> SettingsHome()
                "appearance" -> AppearanceSettings()
                "browser" -> BrowserSettings()
                "privacy" -> PrivacySettings()
                "ai" -> AiSettings()
                "advanced" -> AdvancedSettings()
            }
        }
    }
}

@Composable
private fun SettingsHome() {
    val nav = LocalNav.current
    NxCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NxLogo(48.dp); Column { Text("NexVora Smart AI Browser", style = MaterialTheme.typography.titleMedium); Text("Version 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    listOf(
        Triple("appearance", "Appearance", "Theme, colors, wallpaper, home layout"), Triple("browser", "Browser", "Search engine, startup page, desktop site"),
        Triple("privacy", "Privacy & security", "Cookies, history, tracker blocking"), Triple("ai", "AI features", "Model, preferences, chat history"),
        Triple("advanced", "Advanced", "JavaScript, storage, reset"),
    ).forEach { (k, t, s) -> SettingRow(t, s, trailing = { Icon(Icons.Filled.ChevronRight, null) }, onClick = { nav.push(Screen.Settings(k)) }) }
    SettingRow("Privacy Center", "Clear data, permissions, blocker", trailing = { Icon(Icons.Filled.ChevronRight, null) }, onClick = { nav.push(Screen.Privacy) })
    SettingRow("About", null, trailing = { Icon(Icons.Filled.ChevronRight, null) }, onClick = { nav.push(Screen.About) })
}

@Composable
private fun AppearanceSettings() {
    val app = LocalApp.current; val s = app.settings; val ctx = LocalContext.current
    val theme by s.theme.flow.collectAsState(); val accent by s.accent.flow.collectAsState(); val fs by s.fontScale.flow.collectAsState()
    val recent by s.homeRecent.flow.collectAsState(); val marks by s.homeBookmarks.flow.collectAsState(); val wall by s.wallpaper.flow.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(it)!!.use { i -> BitmapFactory.decodeStream(i, null, opts) }
                var sample = 1; while (opts.outWidth / sample > 1600) sample *= 2
                val bmp = ctx.contentResolver.openInputStream(it)!!.use { i -> BitmapFactory.decodeStream(i, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
                File(ctx.filesDir, "wallpaper.jpg").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, o) }
                s.wallpaper.value = false; s.wallpaper.value = true
            }.onFailure { Toast.makeText(ctx, "Couldn't use that image", Toast.LENGTH_SHORT).show() }
        }
    }
    Group("Theme")
    listOf("system" to "System", "dark" to "Dark", "light" to "Light", "amoled" to "AMOLED black").forEach { (k, l) -> Radio(l, theme == k) { s.theme.value = k } }
    Group("Accent color")
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccentChoices.forEach { c ->
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c)).border(if (c == accent) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { s.accent.value = c })
        }
    }
    Group("Text size")
    Slider(fs, { s.fontScale.value = (it * 20).toInt() / 20f }, Modifier.padding(horizontal = 16.dp), valueRange = 0.85f..1.4f)
    Text("Scales text throughout NexVora on top of your system font size (${(fs * 100).toInt()}%).", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Group("Home page")
    SwitchRow("Recent browsing", null, recent) { s.homeRecent.value = it }
    SwitchRow("Bookmark shortcuts", null, marks) { s.homeBookmarks.value = it }
    SettingRow("Wallpaper", if (wall) "Custom image in use" else "None", trailing = { if (wall) TextButton(onClick = { s.wallpaper.value = false; File(ctx.filesDir, "wallpaper.jpg").delete() }) { Text("Remove") } }, onClick = { pick.launch(arrayOf("image/*")) })
}

@Composable
private fun BrowserSettings() {
    val s = LocalApp.current.settings
    val engine by s.searchEngine.flow.collectAsState(); val custom by s.customEngine.flow.collectAsState()
    val startup by s.startupUrl.flow.collectAsState(); val desktop by s.desktopDefault.flow.collectAsState(); val restore by s.restoreTabs.flow.collectAsState()
    Group("Search engine")
    SearchEngine.values().filter { it != SearchEngine.CUSTOM }.forEach { e -> Radio(e.label, engine == e.name) { s.searchEngine.value = e.name } }
    Radio("Custom", engine == SearchEngine.CUSTOM.name && custom.contains("%s")) { if (custom.contains("%s")) s.searchEngine.value = SearchEngine.CUSTOM.name }
    var c by remember(custom) { mutableStateOf(custom) }
    OutlinedTextField(c, { c = it; if (it.contains("%s") && (it.startsWith("https://") || it.startsWith("http://"))) { s.customEngine.value = it; s.searchEngine.value = SearchEngine.CUSTOM.name } },
        Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("Custom search URL, use %s for the query") }, placeholder = { Text("https://example.com/search?q=%s") }, singleLine = true,
        isError = c.isNotEmpty() && !c.contains("%s"))
    Group("Startup")
    var st by remember(startup) { mutableStateOf(startup) }
    OutlinedTextField(st, { st = it; s.startupUrl.value = it.trim() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("Open this page at launch (empty = NexVora Home)") }, singleLine = true)
    SwitchRow("Restore tabs at launch", "Private tabs are never restored", restore) { s.restoreTabs.value = it }
    Group("Websites")
    SwitchRow("Request desktop site by default", "Applies to tabs you open from now on", desktop) { s.desktopDefault.value = it }
    Group("Downloads")
    Text("Files are saved by Android's download manager. On Android 10+ they go to your Downloads folder.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PrivacySettings() {
    val app = LocalApp.current; val s = app.settings; val nav = LocalNav.current
    val hist by s.saveHistory.flow.collectAsState(); val tp by s.thirdPartyCookies.flow.collectAsState(); val dnt by s.doNotTrack.flow.collectAsState(); val ab by s.adBlock.flow.collectAsState()
    SwitchRow("Save browsing history", "Private tabs are never saved", hist) { s.saveHistory.value = it }
    SwitchRow("Allow third-party cookies", "Off blocks cookies set by other sites embedded in pages", tp) { s.thirdPartyCookies.value = it; app.tabs.applySettingsToTabs() }
    SwitchRow("Send Do Not Track / Global Privacy Control", "Sent with page navigations; sites may ignore it", dnt) { s.doNotTrack.value = it }
    val https by s.httpsOnly.flow.collectAsState()
    SwitchRow("HTTPS-only mode", "Upgrades http:// links to https://. Sites without HTTPS will fail to load.", https) { s.httpsOnly.value = it }
    SwitchRow("Block known ad and tracker hosts", "Real request blocking; see Privacy Center for limits", ab) { s.adBlock.value = it }
    SettingRow("Open Privacy Center", "Clear data, permissions, blocklist", trailing = { Icon(Icons.Filled.ChevronRight, null) }, onClick = { nav.push(Screen.Privacy) })
}

@Composable
private fun AiSettings() {
    val app = LocalApp.current; val s = app.settings; val ctx = LocalContext.current
    val on by s.aiEnabled.flow.collectAsState(); val state by app.engine.state.collectAsState()
    val tokens by s.aiMaxTokens.flow.collectAsState(); val tpl by s.aiTemplate.flow.collectAsState(); val keep by s.aiSaveHistory.flow.collectAsState()
    var url by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(app.engine::importModel) }
    SwitchRow("Enable AI features", "Browsing never depends on AI", on) { s.aiEnabled.value = it }
    Group("Model")
    val status = when (state) {
        AiState.NoModel -> "Not installed"; is AiState.Downloading -> "Downloading ${(state as AiState.Downloading).percent}%"; is AiState.Importing -> "Importing ${(state as AiState.Importing).percent}%"
        is AiState.Installed -> "Installed: ${(state as AiState.Installed).name} (${fmtSize((state as AiState.Installed).bytes)}), not loaded"; AiState.Loading -> "Loading…"
        is AiState.Ready -> "Ready: ${(state as AiState.Ready).name}"; AiState.Generating -> "Generating…"; is AiState.Error -> "Error: ${(state as AiState.Error).message}"
    }
    SettingRow("Status", status)
    SettingRow("Storage", "Models use ${fmtSize(app.engine.storageUsed())}. Free space: ${fmtSize(app.engine.freeSpace())}")
    SettingRow("Import model file", "Choose a MediaPipe LLM model (.task or .bin) from your device", onClick = { pick.launch(arrayOf("*/*")) })
    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("Or download from an https:// link") }, singleLine = true)
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { app.engine.downloadModel(url.trim()) }, enabled = url.startsWith("https://")) { Text("Download") }
        if (state is AiState.Downloading || state is AiState.Importing) OutlinedButton(onClick = { app.engine.cancelTransfer() }) { Text("Cancel") }
    }
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state is AiState.Installed || state is AiState.Error) Button(onClick = { app.engine.load() }, enabled = app.engine.modelFile() != null) { Text("Load model") }
        if (state is AiState.Ready) OutlinedButton(onClick = { app.engine.unload() }) { Text("Unload model") }
        if (app.engine.modelFile() != null) OutlinedButton(onClick = { app.engine.deleteModel() }) { Text("Delete model") }
    }
    Text("No model is bundled with NexVora. Use a model in MediaPipe's LLM format (for example a Gemma .task file). Some hosts require you to sign in or accept a license first; download the file in your browser and use Import. Large models need a lot of RAM; if loading fails, try a smaller one.",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Group("Preferences")
    SettingRow("Context size (tokens)", "Applied the next time the model loads. Larger uses more memory.", trailing = {
        Row { listOf(1024, 2048, 4096).forEach { t -> FilterChip(tokens == t, { s.aiMaxTokens.value = t; if (state is AiState.Ready) { app.engine.unload(); app.engine.load() } }, { Text("$t") }, Modifier.padding(start = 4.dp)) } }
    })
    SettingRow("Prompt format", "Use Gemma for Gemma models; Plain for others", trailing = {
        Row { listOf("gemma" to "Gemma", "plain" to "Plain").forEach { (k, l) -> FilterChip(tpl == k, { s.aiTemplate.value = k }, { Text(l) }, Modifier.padding(start = 4.dp)) } }
    })
    SwitchRow("Keep AI chat history", "Off clears chats whenever the app starts", keep) { s.aiSaveHistory.value = it }
    SettingRow("Clear AI chat history", null, onClick = { app.repo.clearChat(); Toast.makeText(ctx, "Chat history cleared", Toast.LENGTH_SHORT).show() })
    Text("Page text is sent to the on-device model only. It never leaves your phone.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AdvancedSettings() {
    val app = LocalApp.current; val s = app.settings; val ctx = LocalContext.current
    val js by s.javascript.flow.collectAsState()
    val hist by app.repo.history.items.collectAsState(); val marks by app.repo.bookmarks.items.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    SwitchRow("JavaScript", "Turning this off breaks many sites. Applies to open tabs immediately.", js) { s.javascript.value = it; app.tabs.applySettingsToTabs(); app.tabs.tabs.forEach { t -> t.webView?.reload() } }
    Group("Storage")
    SettingRow("Saved data", "${marks.size} bookmarks, ${hist.size} history entries, ${app.repo.shortcuts.items.value.size} shortcuts")
    SettingRow("AI models", fmtSize(app.engine.storageUsed()))
    Group("Reset")
    SettingRow("Reset settings", "Restores defaults. Bookmarks, history and downloads are kept.", onClick = { confirm = true })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Reset all settings?") }, text = { Text("Theme, search engine, privacy and AI preferences return to their defaults.") },
        confirmButton = { TextButton(onClick = { s.resetAll(); app.tabs.applySettingsToTabs(); confirm = false }) { Text("Reset", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
