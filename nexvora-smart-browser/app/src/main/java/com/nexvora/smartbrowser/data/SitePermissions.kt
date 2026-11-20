package com.nexvora.smartbrowser.data

import android.content.Context

/** Remembered per-site camera / microphone / location decisions. Keys look like "https://example.com|camera". */
class SitePermissions(ctx: Context) {
    private val sp = ctx.getSharedPreferences("site_permissions", Context.MODE_PRIVATE)
    fun get(origin: String, kind: String): Boolean? = if (sp.contains("$origin|$kind")) sp.getBoolean("$origin|$kind", false) else null
    fun set(origin: String, kind: String, allow: Boolean) = sp.edit().putBoolean("$origin|$kind", allow).apply()
    fun all(): List<Triple<String, String, Boolean>> = sp.all.mapNotNull { (k, v) ->
        val p = k.split('|'); if (p.size == 2 && v is Boolean) Triple(p[0], p[1], v) else null
    }.sortedBy { it.first }
    fun remove(origin: String, kind: String) = sp.edit().remove("$origin|$kind").apply()
    fun clear() = sp.edit().clear().apply()
}
