package com.nexvora.smartbrowser.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A persisted value exposed as a StateFlow so Compose can observe it. */
class Pref<T>(
    private val sp: SharedPreferences, private val key: String, private val default: T,
    private val read: (SharedPreferences, String, T) -> T,
    private val write: (SharedPreferences.Editor, String, T) -> Unit,
) {
    private val _flow = MutableStateFlow(read(sp, key, default))
    val flow: StateFlow<T> = _flow
    var value: T
        get() = _flow.value
        set(v) { _flow.value = v; sp.edit().also { write(it, key, v) }.apply() }
    fun reset() { value = default }
}

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nexvora_settings", Context.MODE_PRIVATE)
    private fun b(k: String, d: Boolean) = Pref(sp, k, d, { s, key, def -> s.getBoolean(key, def) }, { e, key, v -> e.putBoolean(key, v) })
    private fun s(k: String, d: String) = Pref(sp, k, d, { s, key, def -> s.getString(key, def) ?: def }, { e, key, v -> e.putString(key, v) })
    private fun i(k: String, d: Int) = Pref(sp, k, d, { s, key, def -> s.getInt(key, def) }, { e, key, v -> e.putInt(key, v) })
    private fun f(k: String, d: Float) = Pref(sp, k, d, { s, key, def -> s.getFloat(key, def) }, { e, key, v -> e.putFloat(key, v) })

    val onboarded = b("onboarded", false)
    val theme = s("theme", "dark")                 // system | dark | light | amoled
    val accent = i("accent", 0xFF852FFF.toInt())
    val fontScale = f("font_scale", 1.0f)
    val searchEngine = s("engine", SearchEngine.GOOGLE.name)
    val customEngine = s("custom_engine", "")      // URL template containing %s
    val startupUrl = s("startup_url", "")          // empty = NexVora home
    val desktopDefault = b("desktop_default", false)
    val javascript = b("javascript", true)
    val thirdPartyCookies = b("third_party_cookies", false)
    val doNotTrack = b("dnt", true)
    val adBlock = b("adblock", true)
    val allowlist = s("allowlist", "")             // comma-separated hosts where blocking is off
    val aiEnabled = b("ai_enabled", true)
    val modelPath = s("model_path", "")
    val aiMaxTokens = i("ai_max_tokens", 2048)
    val aiTemplate = s("ai_template", "gemma")      // gemma | plain
    val aiSaveHistory = b("ai_save_history", true)
    val homeRecent = b("home_recent", true)
    val homeBookmarks = b("home_bookmarks", true)
    val saveHistory = b("save_history", true)
    val httpsOnly = b("https_only", false)
    val restoreTabs = b("restore_tabs", true)
    val permCamera = s("perm_camera", "ask")       // ask | block
    val permMic = s("perm_mic", "ask")
    val permLocation = s("perm_location", "ask")
    val wallpaper = b("wallpaper", false)

    fun resetAll() {
        sp.edit().clear().apply()
        listOf(theme, searchEngine, customEngine, startupUrl, allowlist, modelPath).forEach { it.reset() }
        accent.reset(); fontScale.reset(); desktopDefault.reset(); javascript.reset()
        thirdPartyCookies.reset(); doNotTrack.reset(); adBlock.reset(); aiEnabled.reset(); aiMaxTokens.reset()
        homeRecent.reset(); homeBookmarks.reset(); aiTemplate.reset(); aiSaveHistory.reset(); saveHistory.reset(); httpsOnly.reset(); restoreTabs.reset(); permCamera.reset(); permMic.reset(); permLocation.reset(); wallpaper.reset()
        onboarded.value = true
    }

    fun engineTemplate(): String {
        val e = runCatching { SearchEngine.valueOf(searchEngine.value) }.getOrDefault(SearchEngine.GOOGLE)
        return if (e == SearchEngine.CUSTOM && customEngine.value.contains("%s")) customEngine.value else
            if (e == SearchEngine.CUSTOM) SearchEngine.GOOGLE.template else e.template
    }
}
