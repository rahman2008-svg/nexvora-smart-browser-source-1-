package com.nexvora.smartbrowser

import android.app.DownloadManager
import com.nexvora.smartbrowser.browser.DownloadItem
import com.nexvora.smartbrowser.browser.fmtSize
import org.junit.Assert.*
import org.junit.Test

class DownloadItemTest {
    private fun item(status: Int, total: Long = 100, done: Long = 50) = DownloadItem(1, "f", null, null, status, total, done, 0, null)

    @Test fun runningIsActiveWithPercent() { val d = item(DownloadManager.STATUS_RUNNING); assertTrue(d.active); assertEquals(50, d.percent) }
    @Test fun pendingAndPausedAreActive() { assertTrue(item(DownloadManager.STATUS_PENDING).active); assertTrue(item(DownloadManager.STATUS_PAUSED).active) }
    @Test fun successfulIsCompleted() { val d = item(DownloadManager.STATUS_SUCCESSFUL); assertTrue(d.completed); assertFalse(d.active) }
    @Test fun failedIsFailed() { val d = item(DownloadManager.STATUS_FAILED); assertTrue(d.failed); assertFalse(d.completed) }
    @Test fun unknownTotalGivesNegativePercent() = assertEquals(-1, item(DownloadManager.STATUS_RUNNING, total = 0, done = 10).percent)
    @Test fun sizeFormatting() { assertEquals("Unknown size", fmtSize(0)); assertEquals("512 B", fmtSize(512)); assertEquals("1.0 KB", fmtSize(1024)); assertEquals("2.0 MB", fmtSize(2L * 1048576)) }
}
