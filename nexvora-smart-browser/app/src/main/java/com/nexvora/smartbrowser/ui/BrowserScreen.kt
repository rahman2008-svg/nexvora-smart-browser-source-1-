package com.nexvora.smartbrowser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nexvora.smartbrowser.ai.PageContext
import com.nexvora.smartbrowser.browser.BrowserTab
import com.nexvora.smartbrowser.browser.UrlUtils
import kotlinx.coroutines.launch

private fun qrBitmap(text: String, size: Int = 640): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) b.setPixel(x, y, if (m[x, y]) AColor.BLACK else AColor.WHITE)
    return b
}

fun shareText(ctx: Context, text: String, subject: String? = null) =
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).also { if (subject != null) it.putExtra(Intent.EXTRA_SUBJECT, subject) }, "Share"))

fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("NexVora", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val tm = app.tabs
    val tab = tm.current
    if (tab == null) { LaunchedEffect(Unit) { nav.root(Screen.Home) }; return }
    val marks by app.repo.bookmarks.items.collectAsState()
    val blocked by app.blocker.blocked.collectAsState()
    val bookmarked = marks.any { it.url == tab.url }
    var editing by remember(tab.id) { mutableStateOf(false) }
    var input by remember(tab.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var find by remember(tab.id) { mutableStateOf(false) }
    var findQ by remember(tab.id) { mutableStateOf("") }
    var dialog by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    fun askPage(action: com.nexvora.smartbrowser.ai.AiAction) = scope.launch {
        val txt = tm.pageText(tab, app.engine.pageBudgetChars())
        if (txt == null) { Toast.makeText(ctx, "This page has no readable text yet.", Toast.LENGTH_SHORT).show(); return@launch }
        nav.pendingAi = PendingAi(app.ai.instruction(action), action.label, PageContext(tab.title, tab.url, txt)); nav.push(Screen.Ai)
    }
    fun askSelection(instruction: String, shown: String) = scope.launch {
        val sel = tm.selectedText(tab)
        if (sel == null) { Toast.makeText(ctx, "Select some text on the page first.", Toast.LENGTH_SHORT).show(); return@launch }
        nav.pendingAi = PendingAi("$instruction", shown, PageContext(tab.title, tab.url, sel.take(app.engine.pageBudgetChars()))); nav.push(Screen.Ai)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- top address bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    AddressField(input, { input = it }, { openOrLoad(app, tab, it); editing = false }, Modifier.weight(1f), focus = focus, showEngine = false,
                        leading = { IconButton(onClick = { editing = false }) { Icon(Icons.Filled.Close, "Cancel editing") } })
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                } else {
                    Row(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (tab.isPrivate) Nx.Purple2 else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                            .clickable { input = tab.url; editing = true }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (tab.isPrivate) Icon(Icons.Filled.VisibilityOff, "Private tab", tint = Nx.Purple2, modifier = Modifier.size(18.dp))
                        tab.favicon?.let { Image(it.asImageBitmap(), null, Modifier.size(18.dp)) }
                        Icon(if (UrlUtils.isSecure(tab.url)) Icons.Filled.Lock else Icons.Filled.LockOpen, if (UrlUtils.isSecure(tab.url)) "Secure connection" else "Not secure",
                            tint = if (UrlUtils.isSecure(tab.url)) Nx.Success else Nx.Warning, modifier = Modifier.size(18.dp).clickable { dialog = "ssl" })
                        Text(UrlUtils.host(tab.url).ifEmpty { tab.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                IconButton(onClick = { tm.reloadOrStop(tab) }) { Icon(if (tab.loading) Icons.Filled.Close else Icons.Filled.Refresh, if (tab.loading) "Stop loading" else "Reload") }
                IconButton(onClick = { tm.capture(tab); nav.root(Screen.Tabs) }) {
                    Box(Modifier.size(26.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Text("${tm.tabs.size}", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (tab.loading) LinearProgressIndicator(progress = { tab.progress / 100f }, Modifier.fillMaxWidth().height(2.dp), color = Nx.Cyan)
            else Spacer(Modifier.height(2.dp))

            // ---- page
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(tab.id) {
                    AndroidView(
                        factory = { FrameLayout(it) },
                        update = { frame ->
                            val w = tm.ensureWebView(tab)
                            if (w.parent !== frame) {
                                (w.parent as? ViewGroup)?.removeView(w); frame.removeAllViews(); tm.attach(w)
                                frame.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); w.onResume()
                            }
                        },
                        onRelease = { frame -> (frame.getChildAt(0) as? android.webkit.WebView)?.let { tm.detach(it) }; frame.removeAllViews() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                tab.error?.let { err ->
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.CloudOff, null, Modifier.size(56.dp), tint = Nx.Warning)
                        Spacer(Modifier.height(12.dp)); Text("Can't open this page", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp)); Text(err, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp)); GradientButton("Try again") { tm.reloadOrStop(tab) }
                    }
                }
                if (editing && input.isNotBlank()) Box(Modifier.fillMaxWidth().padding(8.dp)) { SuggestionList(input) { openOrLoad(app, tab, it); editing = false } }
            }

            // ---- find in page
            if (find) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(findQ, { findQ = it; tm.findAll(tab, it) }, singleLine = true, modifier = Modifier.weight(1f).padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(Nx.Cyan),
                    decorationBox = { inner -> if (findQ.isEmpty()) Text("Find in page", color = MaterialTheme.colorScheme.outline); inner() })
                IconButton(onClick = { tm.findNext(tab, false) }) { Icon(Icons.Filled.KeyboardArrowUp, "Previous match") }
                IconButton(onClick = { tm.findNext(tab, true) }) { Icon(Icons.Filled.KeyboardArrowDown, "Next match") }
                IconButton(onClick = { find = false; tm.clearFind(tab) }) { Icon(Icons.Filled.Close, "Close find") }
            }

            // ---- bottom toolbar
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    TBtn(Icons.Filled.ArrowBack, "Back", tab.canBack, Modifier.weight(1f)) { tm.goBack(tab) }
                    TBtn(Icons.Filled.ArrowForward, "Forward", tab.canForward, Modifier.weight(1f)) { tm.goForward(tab) }
                    TBtn(Icons.Filled.Home, "Home", true, Modifier.weight(1f)) { nav.root(Screen.Home) }
                    TBtn(if (bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder, if (bookmarked) "Remove bookmark" else "Add bookmark", tab.url.isNotEmpty(), Modifier.weight(1f)) { app.repo.toggleBookmark(tab.title, tab.url) }
                    TBtn(Icons.Filled.MoreVert, "More", true, Modifier.weight(1f)) { menu = true }
                }
            }
        }

        tm.customView?.let { v ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { (v.parent as? ViewGroup)?.removeView(v); v }, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (menu) ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = MaterialTheme.colorScheme.surface) {
        val host = UrlUtils.host(tab.url)
        val allow = app.blocker.isAllowlisted(host)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            @Composable fun Item(icon: ImageVector, label: String, sub: String? = null, action: () -> Unit) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { menu = false; action() }.padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
                    Column { Text(label); if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Item(Icons.Filled.Add, "New tab") { val t = tm.newTab(); tm.select(t.id); nav.root(Screen.Home); nav.focusAddress = true }
            Item(Icons.Filled.VisibilityOff, "New private tab") { val t = tm.newTab(isPrivate = true); tm.select(t.id); nav.root(Screen.Home); nav.focusAddress = true }
            if (tm.canRestore) Item(Icons.Filled.Restore, "Reopen closed tab") { tm.restoreClosed()?.let { nav.root(Screen.Browser) } }
            Item(Icons.Filled.Share, "Share page") { shareText(ctx, tab.url, tab.title) }
            Item(Icons.Filled.ContentCopy, "Copy link") { copyText(ctx, tab.url) }
            Item(Icons.Filled.Search, "Find in page") { find = true }
            Item(Icons.Filled.DesktopWindows, if (tab.desktop) "Request mobile site" else "Request desktop site") { tm.toggleDesktop(tab) }
            Item(Icons.Filled.ZoomIn, "Text zoom", "${tab.zoomPercent}%") { dialog = "zoom" }
            Item(Icons.Filled.AutoStories, "Reader mode") { nav.push(Screen.Reader) }
            Item(Icons.Filled.AutoAwesome, "Summarize with AI") { askPage(com.nexvora.smartbrowser.ai.AiAction.SUMMARIZE) }
            Item(Icons.Filled.QuestionAnswer, "Explain this page") { askPage(com.nexvora.smartbrowser.ai.AiAction.EXPLAIN) }
            Item(Icons.Filled.Translate, "Translate page to Bangla") { askPage(com.nexvora.smartbrowser.ai.AiAction.TRANSLATE_BN) }
            Item(Icons.Filled.TextFields, "Explain selected text") { askSelection("Explain the following text clearly and simply.", "Explain selected text") }
            Item(Icons.Filled.Translate, "Translate selected text to English") { askSelection("Translate the following text into English.", "Translate selection to English") }
            Item(Icons.Filled.Shield, if (allow) "Turn tracker blocking on for this site" else "Turn tracker blocking off for this site", "$blocked requests blocked this session") { app.blocker.toggleAllowlist(host); tab.webView?.reload() }
            Item(Icons.Filled.Info, "Page and connection info") { dialog = "ssl" }
            Item(Icons.Filled.QrCode, "QR code for this page") { dialog = "qr" }
            Item(Icons.Filled.Bookmarks, "Add to reading list") { app.repo.addBookmark(tab.title, tab.url, "Reading list"); Toast.makeText(ctx, "Saved to Reading list (in Bookmarks)", Toast.LENGTH_SHORT).show() }
            Item(Icons.Filled.PushPin, "Add to home shortcuts") { app.repo.addShortcut(tab.title, tab.url); Toast.makeText(ctx, "Added to Home", Toast.LENGTH_SHORT).show() }
            Item(Icons.Filled.OpenInNew, "Open in another app") { runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)).addCategory(Intent.CATEGORY_BROWSABLE), "Open with")) } }
            Item(Icons.Filled.Download, "Downloads") { nav.push(Screen.Downloads) }
            Item(Icons.Filled.Settings, "Settings") { nav.push(Screen.Settings()) }
        }
    }

    when (dialog) {
        "ssl" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(UrlUtils.host(tab.url).ifEmpty { "Page info" }) },
            text = { Text("${tab.title}\n${tab.url}\n\n" + tm.sslInfo(tab)) }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
        "qr" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("QR code") },
            text = { val bmp = remember(tab.url) { runCatching { qrBitmap(tab.url) }.getOrNull() }
                if (bmp != null) Image(bmp.asImageBitmap(), "QR code for ${tab.url}", Modifier.fillMaxWidth().aspectRatio(1f)) else Text("This address is too long to encode.") },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
        "zoom" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Text zoom: ${tab.zoomPercent}%") },
            text = { Slider(tab.zoomPercent.toFloat(), { tm.setZoom(tab, it.toInt() / 10 * 10) }, valueRange = 50f..200f) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } }, dismissButton = { TextButton(onClick = { tm.setZoom(tab, 100) }) { Text("Reset") } })
    }
}

private fun openOrLoad(app: com.nexvora.smartbrowser.NexVoraApp, tab: BrowserTab, input: String) {
    val url = UrlUtils.resolve(input, app.settings.engineTemplate()); if (url.isNotEmpty()) app.tabs.load(tab, url)
}

@Composable
private fun TBtn(icon: ImageVector, desc: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick, modifier.fillMaxHeight(), enabled = enabled) { Icon(icon, desc) }
}
