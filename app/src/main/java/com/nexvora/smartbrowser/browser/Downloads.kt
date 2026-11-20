package com.nexvora.smartbrowser.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.URLUtil
import androidx.core.content.ContextCompat

data class DownloadItem(
    val id: Long, val title: String, val uri: String?, val mime: String?,
    val status: Int, val total: Long, val done: Long, val reason: Int, val localUri: String?,
) {
    val active get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    val completed get() = status == DownloadManager.STATUS_SUCCESSFUL
    val failed get() = status == DownloadManager.STATUS_FAILED
    val percent get() = if (total > 0) ((done * 100) / total).toInt() else -1
}

/** Thin wrapper over Android's DownloadManager (real progress, notifications, system-managed storage). */
class Downloads(private val ctx: Context) {
    private val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(url: String, userAgent: String?, contentDisposition: String?, mime: String?, cookies: String?): Long? {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return null
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(name).setMimeType(mime)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (!cookies.isNullOrEmpty()) req.addRequestHeader("Cookie", cookies)
        if (!userAgent.isNullOrEmpty()) req.addRequestHeader("User-Agent", userAgent)
        // Android 10+ lets DownloadManager write to public Downloads without a storage permission;
        // on 8/9 we use the app's own folder so no legacy storage permission is needed.
        if (Build.VERSION.SDK_INT >= 29) req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        else req.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, name)
        return runCatching { dm.enqueue(req) }.getOrNull()
    }

    fun list(): List<DownloadItem> {
        val out = ArrayList<DownloadItem>()
        runCatching {
            dm.query(DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED or
                    DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
            )).use { c ->
                while (c.moveToNext()) {
                    fun s(col: String) = c.getColumnIndex(col).let { if (it >= 0) c.getString(it) else null }
                    fun l(col: String) = c.getColumnIndex(col).let { if (it >= 0) c.getLong(it) else 0L }
                    out += DownloadItem(l(DownloadManager.COLUMN_ID), s(DownloadManager.COLUMN_TITLE) ?: "Download", s(DownloadManager.COLUMN_URI),
                        s(DownloadManager.COLUMN_MEDIA_TYPE), l(DownloadManager.COLUMN_STATUS).toInt(),
                        l(DownloadManager.COLUMN_TOTAL_SIZE_BYTES), l(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                        l(DownloadManager.COLUMN_REASON).toInt(), s(DownloadManager.COLUMN_LOCAL_URI))
                }
            }
        }
        return out.sortedByDescending { it.id }
    }

    /** Cancels an active download or deletes a finished one (file included). */
    fun remove(id: Long) { runCatching { dm.remove(id) } }

    fun retry(item: DownloadItem): Long? {
        val u = item.uri ?: return null
        remove(item.id)
        return enqueue(u, null, null, item.mime, null)
    }

    fun openIntent(item: DownloadItem): Intent? {
        val uri = runCatching { dm.getUriForDownloadedFile(item.id) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(item: DownloadItem): Intent? {
        val uri = runCatching { dm.getUriForDownloadedFile(item.id) }.getOrNull() ?: return null
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType(item.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share file")
    }

    fun storageInfo(): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val free = runCatching { dir.usableSpace }.getOrDefault(0L)
        return "Free space: ${fmtSize(free)}"
    }
}

fun fmtSize(b: Long): String = when {
    b <= 0 -> "Unknown size"
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}
