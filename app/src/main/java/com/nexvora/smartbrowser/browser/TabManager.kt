package com.nexvora.smartbrowser.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.view.View
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.nexvora.smartbrowser.data.Repository
import com.nexvora.smartbrowser.data.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/** Implemented by MainActivity: things a WebView needs from an Activity. */
interface BrowserHost {
    val activity: android.app.Activity?
    fun chooseFile(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun requestWebPermission(origin: String, resources: Array<String>, grant: (Boolean) -> Unit)
    fun requestGeolocation(origin: String, cb: GeolocationPermissions.Callback)
    fun onDownloadRequested(tab: BrowserTab, url: String, ua: String?, cd: String?, mime: String?)
}

class BrowserTab(val id: Long, val isPrivate: Boolean, var pendingUrl: String?) {
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var favicon by mutableStateOf<Bitmap?>(null)
    var thumb by mutableStateOf<Bitmap?>(null)
    var zoomPercent by mutableIntStateOf(100)
    var webView: WebView? = null
    val isHome get() = webView == null && pendingUrl == null && url.isEmpty()
}

@Serializable data class SavedTab(val url: String, val title: String)

data class ClosedTab(val url: String, val title: String, val isPrivate: Boolean)

private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val PRIVATE_PROFILE = "nexvora_private"

class TabManager(
    private val app: Context, private val settings: Settings, private val repo: Repository,
    private val blocker: AdBlocker,
) {
    var host: BrowserHost? = null
    val tabs: SnapshotStateList<BrowserTab> = mutableStateListOf()
    var currentId by mutableStateOf<Long?>(null)
    var customView by mutableStateOf<View?>(null)
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val closed = ArrayDeque<ClosedTab>()
    var canRestore by mutableStateOf(false)
    private var seq = System.currentTimeMillis()

    val current: BrowserTab? get() = tabs.firstOrNull { it.id == currentId }
    val privateCount get() = tabs.count { it.isPrivate }

    /** True when this device's WebView supports separate profiles, giving private tabs their own cookies/storage. */
    val privateIsolated: Boolean by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }

    fun newTab(url: String? = null, isPrivate: Boolean = false, select: Boolean = true): BrowserTab {
        val t = BrowserTab(++seq, isPrivate, url)
        t.desktop = settings.desktopDefault.value
        if (url != null) t.url = url
        tabs += t
        if (select) currentId = t.id
        return t
    }

    fun select(id: Long) { if (tabs.any { it.id == id }) { current?.let { capture(it); it.webView?.onPause() }; currentId = id; current?.webView?.onResume() } }

    fun close(id: Long) {
        val i = tabs.indexOfFirst { it.id == id }; if (i < 0) return
        val t = tabs[i]
        if (!t.isPrivate && t.url.isNotEmpty()) { closed.addLast(ClosedTab(t.url, t.title, false)); if (closed.size > 15) closed.removeFirst(); canRestore = true }
        destroy(t); tabs.removeAt(i)
        if (currentId == id) currentId = tabs.getOrNull(minOf(i, tabs.size - 1))?.id
        if (t.isPrivate && privateCount == 0) wipePrivateSession()
        persist()
    }

    fun closeAll(privateOnly: Boolean = false) {
        tabs.filter { !privateOnly || it.isPrivate }.map { it.id }.forEach { close(it) }
    }

    fun restoreClosed(): BrowserTab? {
        val c = closed.removeLastOrNull() ?: return null
        canRestore = closed.isNotEmpty()
        return newTab(c.url, c.isPrivate)
    }

    private fun destroy(t: BrowserTab) {
        t.webView?.let { w ->
            runCatching {
                w.stopLoading(); w.webViewClient = WebViewClient(); w.webChromeClient = WebChromeClient()
                (w.parent as? android.view.ViewGroup)?.removeView(w)
                w.clearHistory(); w.removeAllViews(); w.destroy()
            }
        }
        t.webView = null; t.thumb = null; t.favicon = null
    }

    /** Called when the last private tab closes: deletes the private profile (cookies, storage, cache) if supported. */
    private fun wipePrivateSession() {
        if (privateIsolated) runCatching { ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE) }
    }

    private val saveFile = File(app.filesDir, "tabs.json")
    private val saveSer = ListSerializer(SavedTab.serializer())

    /** Saves normal (never private) tabs so they can be restored after the app is closed. */
    fun persist() {
        runCatching {
            val l = tabs.filter { !it.isPrivate && (UrlUtils.isHttp(it.url) || UrlUtils.isSecure(it.url)) }.map { SavedTab(it.url, it.title) }
            saveFile.writeText(Json.encodeToString(saveSer, l))
        }
    }

    /** Re-creates saved tabs (their pages load lazily when first shown). Returns true if any were restored. */
    fun restoreSaved(): Boolean {
        if (tabs.isNotEmpty() || !saveFile.exists()) return false
        val saved = runCatching { Json.decodeFromString(saveSer, saveFile.readText()) }.getOrDefault(emptyList())
        saved.take(30).forEach { newTab(it.url, select = false).title = it.title.ifBlank { it.url } }
        currentId = tabs.lastOrNull()?.id
        return tabs.isNotEmpty()
    }

    fun onTrimMemory() { tabs.forEach { if (it.id != currentId) it.thumb = null } }

    fun destroyAll() { tabs.toList().forEach { destroy(it) }; tabs.clear(); wipePrivateSession() }

    fun hideCustomView() { customView = null; customCb?.onCustomViewHidden(); customCb = null }

    fun attach(w: WebView) { (w.context as? MutableContextWrapper)?.baseContext = host?.activity ?: app }
    fun detach(w: WebView) { (w.context as? MutableContextWrapper)?.baseContext = app }

    // ---------------------------------------------------------------- WebView creation

    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(t: BrowserTab): WebView {
        t.webView?.let { return it }
        val w = WebView(MutableContextWrapper(app))
        if (t.isPrivate && privateIsolated) runCatching { WebViewCompat.setProfile(w, PRIVATE_PROFILE) }
        w.setBackgroundColor(Color.BLACK)
        val s = w.settings
        s.javaScriptEnabled = settings.javascript.value
        s.domStorageEnabled = !t.isPrivate || privateIsolated
        s.databaseEnabled = !t.isPrivate || privateIsolated
        s.cacheMode = if (t.isPrivate && !privateIsolated) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        @Suppress("DEPRECATION") run { s.saveFormData = false }
        s.allowFileAccess = false; s.allowContentAccess = false
        s.setSupportMultipleWindows(false)
        s.mediaPlaybackRequiresUserGesture = true
        s.builtInZoomControls = true; s.displayZoomControls = false
        s.useWideViewPort = true; s.loadWithOverviewMode = true
        s.setGeolocationEnabled(true)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW   // no insecure sub-resources on secure pages
        @Suppress("DEPRECATION") run { s.allowFileAccessFromFileURLs = false; s.allowUniversalAccessFromFileURLs = false }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) WebSettingsCompat.setSafeBrowsingEnabled(s, true)
        applyDesktop(w, t.desktop)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(w, settings.thirdPartyCookies.value)
        w.setDownloadListener { url, ua, cd, mime, _ -> host?.onDownloadRequested(t, url, ua, cd, mime) }
        w.webViewClient = client(t)
        w.webChromeClient = chrome(t)
        t.webView = w
        t.pendingUrl?.let { load(t, it) }; t.pendingUrl = null
        return w
    }

    private fun applyDesktop(w: WebView, on: Boolean) {
        w.settings.userAgentString = if (on) DESKTOP_UA else null
        w.settings.useWideViewPort = true; w.settings.loadWithOverviewMode = true
    }

    fun load(t: BrowserTab, requested: String) {
        val url = if (settings.httpsOnly.value) UrlUtils.upgradeToHttps(requested) else requested
        t.error = null
        val w = t.webView
        if (w == null) { t.pendingUrl = url; t.url = url; return }
        val headers = if (settings.doNotTrack.value) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()
        w.loadUrl(url, headers)
    }

    fun reloadOrStop(t: BrowserTab) { if (t.loading) t.webView?.stopLoading() else { t.error = null; if (t.webView?.url == null && t.url.isNotEmpty()) load(t, t.url) else t.webView?.reload() } }
    fun goBack(t: BrowserTab): Boolean { val w = t.webView ?: return false; return if (w.canGoBack()) { w.goBack(); true } else false }
    fun goForward(t: BrowserTab) { t.webView?.takeIf { it.canGoForward() }?.goForward() }

    fun toggleDesktop(t: BrowserTab) { t.desktop = !t.desktop; t.webView?.let { applyDesktop(it, t.desktop); it.reload() } }
    fun setZoom(t: BrowserTab, percent: Int) { t.zoomPercent = percent.coerceIn(50, 200); t.webView?.settings?.textZoom = t.zoomPercent }

    fun applySettingsToTabs() {
        tabs.forEach { t -> t.webView?.let { w ->
            w.settings.javaScriptEnabled = settings.javascript.value
            CookieManager.getInstance().setAcceptThirdPartyCookies(w, settings.thirdPartyCookies.value)
        } }
    }

    // ---------------------------------------------------------------- find / page data

    fun findAll(t: BrowserTab, q: String) { t.webView?.let { if (q.isEmpty()) it.clearMatches() else it.findAllAsync(q) } }
    fun findNext(t: BrowserTab, forward: Boolean) { t.webView?.findNext(forward) }
    fun clearFind(t: BrowserTab) { t.webView?.clearMatches() }

    private suspend fun eval(t: BrowserTab, js: String): String? = suspendCancellableCoroutine { c ->
        val w = t.webView
        if (w == null) { c.resume(null); return@suspendCancellableCoroutine }
        w.evaluateJavascript(js) { raw ->
            c.resume(runCatching { if (raw == null || raw == "null") null else Json.parseToJsonElement(raw).jsonPrimitive.content }.getOrNull())
        }
    }

    suspend fun pageText(t: BrowserTab, maxChars: Int): String? =
        eval(t, "(function(){return (document.body&&document.body.innerText||'').slice(0,$maxChars)})()")?.takeIf { it.isNotBlank() }

    suspend fun selectedText(t: BrowserTab): String? =
        eval(t, "(function(){return String(window.getSelection()||'')})()")?.takeIf { it.isNotBlank() }

    /** Heuristic article extraction. Returns (title, text) or null if the page has no readable body. */
    suspend fun readerContent(t: BrowserTab): Pair<String, String>? {
        val raw = eval(t, READER_JS) ?: return null
        return runCatching {
            val o = Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
            val text = o["text"]?.jsonPrimitive?.content.orEmpty()
            if (text.length < 200) null else (o["title"]?.jsonPrimitive?.content.orEmpty() to text)
        }.getOrNull()
    }

    fun sslInfo(t: BrowserTab): String {
        val c = t.webView?.certificate ?: return if (t.url.startsWith("https://")) "No certificate details available." else "This page is not using HTTPS. Data you send can be read by others on the network."
        return "Issued to: ${c.issuedTo?.cName}\nIssued by: ${c.issuedBy?.cName}\nValid from: ${c.validNotBeforeDate}\nValid until: ${c.validNotAfterDate}"
    }

    fun capture(t: BrowserTab) {
        val w = t.webView ?: return
        if (w.width <= 0 || w.height <= 0) return
        runCatching {
            val b = Bitmap.createBitmap(w.width / 4, w.height / 4, Bitmap.Config.RGB_565)
            val c = Canvas(b); c.scale(0.25f, 0.25f); w.draw(c); t.thumb = b
        }
    }

    fun clearBrowsingData(cache: Boolean, cookies: Boolean, siteData: Boolean) {
        if (cache) runCatching { WebView(app).apply { clearCache(true); destroy() }; tabs.forEach { it.webView?.clearCache(true) } }
        if (cookies) { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
        if (siteData) { WebStorage.getInstance().deleteAllData(); WebViewDatabase.getInstance(app).clearHttpAuthUsernamePassword() }
    }

    private fun online(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null
    }

    // ---------------------------------------------------------------- clients

    private fun client(t: BrowserTab) = object : WebViewClient() {
        override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
            t.url = url; t.loading = true; t.error = null; t.progress = 0
        }
        override fun onPageFinished(v: WebView, url: String) {
            t.loading = false; t.url = url; t.title = v.title.orEmpty().ifBlank { url }
            t.canBack = v.canGoBack(); t.canForward = v.canGoForward()
            if (!t.isPrivate && settings.saveHistory.value && t.error == null && (url.startsWith("http://") || url.startsWith("https://"))) repo.addHistory(t.title, url)
            persist()
        }
        override fun doUpdateVisitedHistory(v: WebView, url: String, isReload: Boolean) {
            t.url = url; t.canBack = v.canGoBack(); t.canForward = v.canGoForward()
        }
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val u = r.url.toString(); val scheme = r.url.scheme.orEmpty().lowercase()
            if (scheme == "file" || scheme == "content" || scheme == "javascript") return true   // never navigable from web content
            if (scheme == "http" && r.isForMainFrame && settings.httpsOnly.value) { load(t, u); return true }
            if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "blob") return false
            openExternal(u); return true   // tel:, mailto:, intent:, market: ... are handed to the system
        }
        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
            if (r.isForMainFrame) return null
            val pageHost = UrlUtils.host(t.url)
            if (blocker.shouldBlock(r.url.host, pageHost)) {
                blocker.recordBlocked()
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }
        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
            if (!r.isForMainFrame) return
            t.loading = false
            t.error = if (!online()) "You're offline. Check your connection and try again." else "Couldn't open this page (${e.description})."
        }
        override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: android.net.http.SslError) {
            h.cancel() // never bypass certificate validation
            t.loading = false
            t.error = "NexVora blocked this page because its security certificate isn't trusted. Details: ${e.primaryError.let { "code $it" }}."
        }
        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
            val keep = t.url
            destroy(t); t.pendingUrl = keep; t.error = "This tab ran out of memory and was stopped. Reload to continue."
            return true
        }
    }

    private fun openExternal(url: String) {
        val a = host?.activity ?: return
        runCatching {
            if (url.startsWith("intent:")) {
                val i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null
                try { a.startActivity(i) } catch (_: ActivityNotFoundException) {
                    i.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("http") }?.let { load(current ?: return@runCatching, it) }
                }
            } else a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        }
    }

    private fun chrome(t: BrowserTab) = object : WebChromeClient() {
        override fun onProgressChanged(v: WebView, p: Int) { t.progress = p }
        override fun onReceivedTitle(v: WebView, title: String?) { if (!title.isNullOrBlank()) t.title = title }
        override fun onReceivedIcon(v: WebView, icon: Bitmap?) { t.favicon = icon }
        override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean =
            host?.chooseFile(cb, p) ?: false.also { cb.onReceiveValue(null) }
        override fun onPermissionRequest(r: PermissionRequest) {
            val h = host ?: return r.deny()
            h.requestWebPermission(r.origin.toString(), r.resources) { ok -> if (ok) r.grant(r.resources) else r.deny() }
        }
        override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
            host?.requestGeolocation(origin, cb) ?: cb.invoke(origin, false, false)
        }
        override fun onShowCustomView(view: View, cb: CustomViewCallback) { customView = view; customCb = cb }
        override fun onHideCustomView() { customView = null; customCb?.onCustomViewHidden(); customCb = null }
        override fun onJsAlert(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            val a = host?.activity ?: return false.also { r.cancel() }
            AlertDialog.Builder(a).setTitle(UrlUtils.host(url)).setMessage(msg).setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> r.confirm() }.show(); return true
        }
        override fun onJsConfirm(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            val a = host?.activity ?: return false.also { r.cancel() }
            AlertDialog.Builder(a).setTitle(UrlUtils.host(url)).setMessage(msg).setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> r.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> r.cancel() }.show(); return true
        }
    }

    companion object {
        private val READER_JS = """
(function(){try{
var best=document.querySelector('article')||document.querySelector('main')||document.querySelector('[role=main]');
if(!best){var max=0;document.querySelectorAll('div,section').forEach(function(e){var n=0;
 for(var i=0;i<e.children.length;i++){if(e.children[i].tagName==='P')n+=(e.children[i].innerText||'').length}
 if(n>max){max=n;best=e}})}
if(!best)best=document.body;
var out=[];
best.querySelectorAll('h1,h2,h3,p,li,blockquote,pre').forEach(function(e){
 if(e.closest('nav,aside,footer,header,form,script,style,noscript'))return;
 var t=(e.innerText||'').trim(); var h=e.tagName.charAt(0)==='H';
 if(t.length<(h?1:25))return; out.push((h?'## ':'')+t);});
return JSON.stringify({title:document.title,text:out.join('\n\n')});
}catch(e){return JSON.stringify({title:document.title,text:''})}})()
""".trimIndent()
    }
}
