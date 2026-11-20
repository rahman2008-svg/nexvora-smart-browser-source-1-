package com.nexvora.smartbrowser.browser

import java.net.URI
import java.net.URLEncoder

/** Pure Kotlin (no Android types) so it can be unit tested on the JVM. */
object UrlUtils {
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val HOSTLIKE = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63})(:\\d{1,5})?([/?#]\\S*)?$"
    )
    private val HOST_FALLBACK = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/:?#@]+@)?([^/:?#]+)")

    fun encodeQuery(q: String): String = URLEncoder.encode(q, "UTF-8")

    /** Turns user input into a loadable URL: a real address stays an address, everything else becomes a search. */
    fun resolve(input: String, searchTemplate: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        if (SCHEME.containsMatchIn(t)) return t
        if (t.startsWith("about:") || t.startsWith("data:")) return t
        if (!t.contains(' ') && HOSTLIKE.matches(t)) return "https://$t"
        return searchTemplate.replace("%s", encodeQuery(t))
    }

    fun host(url: String): String {
        val h = runCatching { URI(url).host }.getOrNull() ?: HOST_FALLBACK.find(url)?.groupValues?.get(2)
        return (h ?: "").removePrefix("www.")
    }

    fun isSecure(url: String) = url.startsWith("https://", ignoreCase = true)
    fun isHttp(url: String) = url.startsWith("http://", ignoreCase = true)
    fun upgradeToHttps(url: String) = if (isHttp(url)) "https://" + url.substring(7) else url
}
