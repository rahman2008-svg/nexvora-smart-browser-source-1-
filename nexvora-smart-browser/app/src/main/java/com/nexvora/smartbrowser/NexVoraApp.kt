package com.nexvora.smartbrowser

import android.app.Application
import com.nexvora.smartbrowser.ai.AiController
import com.nexvora.smartbrowser.ai.AiEngine
import com.nexvora.smartbrowser.browser.AdBlocker
import com.nexvora.smartbrowser.browser.Downloads
import com.nexvora.smartbrowser.browser.TabManager
import com.nexvora.smartbrowser.data.Repository
import com.nexvora.smartbrowser.data.Settings
import com.nexvora.smartbrowser.data.SitePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container: one instance of each service for the whole process. */
class NexVoraApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settings: Settings; private set
    lateinit var repo: Repository; private set
    lateinit var blocker: AdBlocker; private set
    lateinit var tabs: TabManager; private set
    lateinit var downloads: Downloads; private set
    lateinit var engine: AiEngine; private set
    lateinit var ai: AiController; private set
    lateinit var sitePerms: SitePermissions; private set

    override fun onCreate() {
        super.onCreate()
        android.webkit.WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)   // never enabled in release
        settings = Settings(this)
        repo = Repository(this, scope)
        blocker = AdBlocker(this, settings)
        tabs = TabManager(this, settings, repo, blocker)
        downloads = Downloads(this)
        engine = AiEngine(this, settings, scope)
        ai = AiController(engine, repo, settings)
        sitePerms = SitePermissions(this)
        if (!settings.aiSaveHistory.value) repo.clearChat()
    }
}
