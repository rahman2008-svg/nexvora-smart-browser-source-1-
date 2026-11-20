package com.nexvora.smartbrowser.browser

/** Pure host-list helpers (parsing, normalisation, suffix matching). */
object HostMatcher {
    data class Parsed(val hosts: Set<String>, val invalidLines: Int)

    private val LABELS = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Lower-cases, trims, strips a trailing dot; returns null when the value is not a valid DNS-style hostname. */
    fun normalize(raw: String): String? {
        val h = raw.trim().lowercase().removeSuffix(".")
        if (h.isEmpty() || h.length > 253 || IPV4.matches(h)) return null
        return if (LABELS.matches(h)) h else null
    }

    /**
     * Accepts: one host per line, hosts-file lines ("0.0.0.0 host"), and simple Adblock "||host^" rules.
     * Comment lines (# or !) are ignored. Anything else counts as an invalid line.
     */
    fun parse(text: String): Parsed {
        val out = LinkedHashSet<String>(); var invalid = 0
        text.lineSequence().forEach { line0 ->
            var line = line0.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("[")) return@forEach
            line = line.substringBefore(" #").substringBefore("\t#").trim()
            val candidate = when {
                line.startsWith("||") -> line.removePrefix("||").substringBefore('^').substringBefore('/').substringBefore('$')
                else -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1] else if (parts.size == 1) parts[0] else ""
                }
            }
            val n = normalize(candidate)
            if (n == null) { if (candidate != "localhost") invalid++ } else out.add(n)
        }
        return Parsed(out, invalid)
    }

    /** True if [host] or any parent domain (down to two labels) is in [set]. */
    fun matches(set: Set<String>, host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = host.lowercase().removeSuffix(".")
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }
}
