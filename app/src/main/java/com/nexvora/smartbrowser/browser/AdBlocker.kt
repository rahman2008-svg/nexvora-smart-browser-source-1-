package com.nexvora.smartbrowser.browser

import android.content.Context
import com.nexvora.smartbrowser.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host-based request blocker used from WebViewClient.shouldInterceptRequest.
 * It only blocks sub-resource requests whose hostname is on the list. It cannot hide page elements,
 * block first-party ads, or filter content served from a site's own hostname.
 * The counter counts requests that were actually intercepted since the app started (or since reset).
 */
class AdBlocker(private val ctx: Context, private val settings: Settings) {
    @Volatile private var hosts: Set<String> = emptySet()
    private val counter = AtomicInteger(0)
    private val _blocked = MutableStateFlow(0)
    val blocked: StateFlow<Int> = _blocked
    private val _listSize = MutableStateFlow(0)
    val listSize: StateFlow<Int> = _listSize
    private val userFile = File(ctx.filesDir, "blocklist_user.txt")

    init { reload() }

    private fun readUser() = runCatching { if (userFile.exists()) userFile.readText() else "" }.getOrDefault("")

    fun reload() {
        val bundled = runCatching { ctx.assets.open("blocklist.txt").bufferedReader().readText() }.getOrDefault("")
        hosts = HostMatcher.parse(bundled).hosts + HostMatcher.parse(readUser()).hosts
        _listSize.value = hosts.size
    }

    /** Validates and merges a user-chosen list. Fails (changing nothing) if it is too large or has no valid hosts. */
    fun importList(text: String): Result<Int> {
        if (text.length > MAX_IMPORT_CHARS) return Result.failure(IllegalArgumentException("File is too large (limit ${MAX_IMPORT_CHARS / 1_000_000} MB)."))
        val parsed = HostMatcher.parse(text)
        if (parsed.hosts.isEmpty()) return Result.failure(IllegalArgumentException("No valid hostnames found. Use one host per line or a hosts-file format."))
        if (parsed.invalidLines > parsed.hosts.size) return Result.failure(IllegalArgumentException("Most lines aren't valid hostnames (${parsed.invalidLines} invalid), so the file was rejected."))
        val merged = HostMatcher.parse(readUser()).hosts + parsed.hosts
        if (merged.size > MAX_HOSTS) return Result.failure(IllegalArgumentException("Too many entries (limit $MAX_HOSTS)."))
        val before = hosts.size
        runCatching { userFile.writeText(merged.joinToString("\n")) }.onFailure { return Result.failure(it) }
        reload()
        return Result.success(hosts.size - before)
    }

    fun clearImported() { userFile.delete(); reload() }

    private fun allowlist() = settings.allowlist.value.split(',').mapNotNull { HostMatcher.normalize(it) }.toSet()
    fun isAllowlisted(pageHost: String) = HostMatcher.matches(allowlist(), pageHost)

    fun shouldBlock(requestHost: String?, pageHost: String): Boolean {
        if (!settings.adBlock.value) return false
        if (isAllowlisted(pageHost)) return false
        return HostMatcher.matches(hosts, requestHost)
    }

    fun recordBlocked() { _blocked.value = counter.incrementAndGet() }
    fun resetCount() { counter.set(0); _blocked.value = 0 }

    fun toggleAllowlist(host: String) {
        val h = HostMatcher.normalize(host) ?: return
        val cur = allowlist().toMutableSet()
        if (!cur.remove(h)) cur.add(h)
        settings.allowlist.value = cur.sorted().joinToString(",")
    }

    companion object { const val MAX_IMPORT_CHARS = 8_000_000; const val MAX_HOSTS = 400_000 }
}
