package com.nexvora.smartbrowser

import com.nexvora.smartbrowser.browser.UrlUtils
import org.junit.Assert.*
import org.junit.Test

class UrlUtilsTest {
    private val g = "https://www.google.com/search?q=%s"

    @Test fun bareDomainBecomesHttps() = assertEquals("https://example.com", UrlUtils.resolve("example.com", g))
    @Test fun explicitSchemeIsKept() = assertEquals("http://a.b/c", UrlUtils.resolve("http://a.b/c", g))
    @Test fun domainWithPathAndQuery() = assertEquals("https://example.com/p?x=1", UrlUtils.resolve("example.com/p?x=1", g))
    @Test fun localhostWithPort() = assertEquals("https://localhost:8080/x", UrlUtils.resolve("localhost:8080/x", g))
    @Test fun ipAddress() = assertEquals("https://192.168.1.1", UrlUtils.resolve("192.168.1.1", g))
    @Test fun wordsBecomeSearch() = assertEquals("https://www.google.com/search?q=hello+world", UrlUtils.resolve("hello world", g))
    @Test fun singleWordIsSearch() = assertEquals("https://www.google.com/search?q=cats", UrlUtils.resolve("cats", g))
    @Test fun numberWithDotIsSearch() = assertEquals("https://www.google.com/search?q=3.14", UrlUtils.resolve("3.14", g))
    @Test fun emailLikeIsSearch() = assertTrue(UrlUtils.resolve("user@example.com", g).startsWith("https://www.google.com/search"))
    @Test fun blankStaysBlank() = assertEquals("", UrlUtils.resolve("   ", g))
    @Test fun aboutIsKept() = assertEquals("about:blank", UrlUtils.resolve("about:blank", g))
    @Test fun customTemplate() = assertEquals("https://duckduckgo.com/?q=a+b", UrlUtils.resolve("a b", "https://duckduckgo.com/?q=%s"))
    @Test fun queryIsEncoded() = assertEquals("https://www.google.com/search?q=a%26b%3Dc", UrlUtils.resolve("a&b=c", g))

    @Test fun hostStripsWww() = assertEquals("example.com", UrlUtils.host("https://www.example.com/a"))
    @Test fun hostKeepsSubdomain() = assertEquals("m.example.com", UrlUtils.host("https://m.example.com/"))
    @Test fun hostLocalhost() = assertEquals("localhost", UrlUtils.host("http://localhost:3000/"))
    @Test fun hostOfGarbageIsEmpty() = assertEquals("", UrlUtils.host("not a url"))

    @Test fun secureDetection() { assertTrue(UrlUtils.isSecure("https://a.com")); assertFalse(UrlUtils.isSecure("http://a.com")) }
    @Test fun httpsUpgrade() { assertEquals("https://a.com/x", UrlUtils.upgradeToHttps("http://a.com/x")); assertEquals("https://a.com", UrlUtils.upgradeToHttps("https://a.com")) }
}
