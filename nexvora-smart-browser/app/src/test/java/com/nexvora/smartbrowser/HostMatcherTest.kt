package com.nexvora.smartbrowser

import com.nexvora.smartbrowser.browser.HostMatcher
import org.junit.Assert.*
import org.junit.Test

class HostMatcherTest {
    @Test fun normalizeTrimsLowersAndStripsDot() = assertEquals("example.com", HostMatcher.normalize("  EXAMPLE.com. "))
    @Test fun normalizeRejectsSingleLabel() = assertNull(HostMatcher.normalize("localhost"))
    @Test fun normalizeRejectsIp() = assertNull(HostMatcher.normalize("1.2.3.4"))
    @Test fun normalizeRejectsUnderscore() = assertNull(HostMatcher.normalize("bad_host.com"))
    @Test fun normalizeRejectsLeadingHyphen() = assertNull(HostMatcher.normalize("-a.com"))

    @Test fun parseSupportedFormats() {
        val p = HostMatcher.parse("# comment\n0.0.0.0 ads.example.com\n||tracker.net^\nplain.org\n!adblock comment\nnot a host line\n127.0.0.1 localhost\n")
        assertEquals(setOf("ads.example.com", "tracker.net", "plain.org"), p.hosts)
        assertEquals(1, p.invalidLines)
    }
    @Test fun parseRemovesDuplicates() = assertEquals(1, HostMatcher.parse("a.com\nA.COM\na.com.\n").hosts.size)
    @Test fun parseGarbageHasNoHosts() { val p = HostMatcher.parse("<html>\nhello world\n"); assertTrue(p.hosts.isEmpty()); assertTrue(p.invalidLines > 0) }

    private val set = setOf("ads.example.com", "tracker.net")
    @Test fun matchesExact() = assertTrue(HostMatcher.matches(set, "ads.example.com"))
    @Test fun matchesSubdomain() = assertTrue(HostMatcher.matches(set, "x.y.tracker.net"))
    @Test fun doesNotMatchParentOfListedHost() = assertFalse(HostMatcher.matches(set, "example.com"))
    @Test fun doesNotMatchLookalike() = assertFalse(HostMatcher.matches(set, "nottracker.net"))
    @Test fun doesNotMatchBareTld() = assertFalse(HostMatcher.matches(set, "net"))
    @Test fun nullOrEmptyNeverMatches() { assertFalse(HostMatcher.matches(set, null)); assertFalse(HostMatcher.matches(set, "")) }
    @Test fun matchIsCaseInsensitive() = assertTrue(HostMatcher.matches(set, "ADS.Example.COM"))
}
