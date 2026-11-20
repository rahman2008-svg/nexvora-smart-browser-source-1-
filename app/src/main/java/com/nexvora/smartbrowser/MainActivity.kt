package com.nexvora.smartbrowser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexvora.smartbrowser.browser.BrowserHost
import com.nexvora.smartbrowser.browser.BrowserTab
import com.nexvora.smartbrowser.browser.UrlUtils
import com.nexvora.smartbrowser.ui.NexVoraRoot
import com.nexvora.smartbrowser.ui.NexVoraTheme

class MainActivity : ComponentActivity(), BrowserHost {
    private val app get() = application as NexVoraApp
    override val activity: Activity get() = this

    private var fileCb: ValueCallback<Array<Uri>>? = null
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)); fileCb = null
    }
    private var permCb: ((Boolean) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
        permCb?.invoke(m.isNotEmpty() && m.values.all { it }); permCb = null
    }
    private var voiceCb: ((String) -> Unit)? = null
    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { voiceCb?.invoke(it) }
        voiceCb = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.tabs.host = this
        setContent {
            val s = app.settings
            val theme by s.theme.flow.collectAsState(); val accent by s.accent.flow.collectAsState(); val fs by s.fontScale.flow.collectAsState()
            NexVoraTheme(theme, accent, fs) { NexVoraRoot(app, this) }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = UrlUtils.resolve(text, app.settings.engineTemplate())
        val t = app.tabs.newTab(url); app.tabs.load(t, url)
        externalOpen = true
    }
    var externalOpen by androidx.compose.runtime.mutableStateOf(false)

    override fun onPause() { app.tabs.current?.webView?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); app.tabs.current?.webView?.onResume() }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_UI_HIDDEN) app.tabs.onTrimMemory() }
    override fun onDestroy() {
        if (isFinishing) app.tabs.destroyAll()
        app.tabs.host = null; super.onDestroy()
    }

    // ----- voice search
    fun voiceSearch(onResult: (String) -> Unit) {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        voiceCb = onResult
        runCatching { voiceLauncher.launch(i) }.onFailure { voiceCb = null; android.widget.Toast.makeText(this, "No speech recognizer available on this device.", android.widget.Toast.LENGTH_LONG).show() }
    }

    // ----- BrowserHost
    override fun chooseFile(cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCb?.onReceiveValue(null); fileCb = cb
        return try { fileLauncher.launch(params.createIntent()); true } catch (e: Exception) { fileCb = null; cb.onReceiveValue(null); false }
    }

    private fun need(perms: List<String>) = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

    override fun requestWebPermission(origin: String, resources: Array<String>, grant: (Boolean) -> Unit) {
        val kinds = resources.map { when (it) { PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "camera"; PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "microphone"; else -> "other" } }
        if (kinds.any { it == "other" }) return grant(false)
        val s = app.settings
        if (("camera" in kinds && s.permCamera.value == "block") || ("microphone" in kinds && s.permMic.value == "block")) return grant(false)
        val remembered = kinds.map { app.sitePerms.get(origin, it) }
        if (remembered.any { it == false }) return grant(false)
        val androidPerms = kinds.map { if (it == "camera") Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO }
        val proceed = {
            val missing = need(androidPerms)
            if (missing.isEmpty()) grant(true) else { permCb = grant; permLauncher.launch(missing.toTypedArray()) }
        }
        if (remembered.all { it == true }) return proceed()
        AlertDialog.Builder(this).setTitle(UrlUtils.host(origin).ifEmpty { origin })
            .setMessage("This site wants to use your ${kinds.joinToString(" and ")}. Your choice is remembered; you can reset it in Privacy Center.")
            .setPositiveButton("Allow") { _, _ -> kinds.forEach { app.sitePerms.set(origin, it, true) }; proceed() }
            .setNegativeButton("Block") { _, _ -> kinds.forEach { app.sitePerms.set(origin, it, false) }; grant(false) }
            .setOnCancelListener { grant(false) }.show()
    }

    override fun requestGeolocation(origin: String, cb: GeolocationPermissions.Callback) {
        if (app.settings.permLocation.value == "block") return cb.invoke(origin, false, false)
        val remembered = app.sitePerms.get(origin, "location")
        if (remembered == false) return cb.invoke(origin, false, false)
        val proceed = {
            val missing = need(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            if (missing.size < 2) cb.invoke(origin, true, false)           // at least one location permission is granted
            else { permCb = { ok -> cb.invoke(origin, ok, false) }; permLauncher.launch(missing.toTypedArray()) }
        }
        if (remembered == true) return proceed()
        AlertDialog.Builder(this).setTitle(UrlUtils.host(origin).ifEmpty { origin })
            .setMessage("This site wants to know your location. Your choice is remembered; you can reset it in Privacy Center.")
            .setPositiveButton("Allow") { _, _ -> app.sitePerms.set(origin, "location", true); proceed() }
            .setNegativeButton("Block") { _, _ -> app.sitePerms.set(origin, "location", false); cb.invoke(origin, false, false) }
            .setOnCancelListener { cb.invoke(origin, false, false) }.show()
    }

    override fun onDownloadRequested(tab: BrowserTab, url: String, ua: String?, cd: String?, mime: String?) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && need(listOf(Manifest.permission.POST_NOTIFICATIONS)).isNotEmpty() && permCb == null)
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))   // only so the completion notification can show; the download proceeds regardless
        val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
        val id = app.downloads.enqueue(url, ua, cd, mime, if (tab.isPrivate) null else cookies)
        android.widget.Toast.makeText(this, if (id != null) "Download started" else "This link can't be downloaded", android.widget.Toast.LENGTH_SHORT).show()
    }
}
