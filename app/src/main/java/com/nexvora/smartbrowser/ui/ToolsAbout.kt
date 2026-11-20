package com.nexvora.smartbrowser.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.browser.UrlUtils

@Composable
fun ToolsScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val ctx = LocalContext.current
    val tab = app.tabs.current?.takeIf { !it.isHome }
    var clear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        NxTopBar("Tools", { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (tab == null) Text("Open a page to use the page tools below.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            @Composable fun T(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) =
                SettingRow(label, trailing = { Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline) }, onClick = if (enabled) onClick else null)
            T(Icons.Filled.Share, "Share page", tab != null) { shareText(ctx, tab!!.url, tab.title) }
            T(Icons.Filled.ContentCopy, "Copy link", tab != null) { copyText(ctx, tab!!.url) }
            T(Icons.Filled.AutoStories, "Reader mode", tab != null) { nav.push(Screen.Reader) }
            T(Icons.Filled.DesktopWindows, "Toggle desktop site", tab != null) { app.tabs.toggleDesktop(tab!!); nav.root(Screen.Browser) }
            T(Icons.Filled.OpenInNew, "Open in another app", tab != null) { runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(tab!!.url)).addCategory(Intent.CATEGORY_BROWSABLE), "Open with")) } }
            T(Icons.Filled.Info, "Find, zoom, QR code and page info", tab != null) { nav.root(Screen.Browser) }
            T(Icons.Filled.QrCodeScanner, "Scan a QR code") { nav.push(Screen.QrScanner) }
            T(Icons.Filled.Download, "Downloads") { nav.push(Screen.Downloads) }
            T(Icons.Filled.Star, "Bookmarks") { nav.push(Screen.Bookmarks) }
            T(Icons.Filled.DeleteSweep, "Clear browsing data") { nav.push(Screen.Privacy) }
        }
    }
}

@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    var licenses by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        NxTopBar("About", { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NxLogo(96.dp)
            Text("NexVora Smart AI Browser", style = MaterialTheme.typography.titleLarge)
            Text("Browse • Search • Ask AI • Explore More.", color = MaterialTheme.colorScheme.secondary)
            Text("Version 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Developed by Prince AR Abdur Rahman\nPublished by NexVora Lab's Ofc", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("© 2026 NexVora Lab's Ofc. All Rights Reserved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            NxCard(Modifier.fillMaxWidth()) {
                Text("Privacy", Modifier.padding(start = 16.dp, top = 14.dp), style = MaterialTheme.typography.titleMedium)
                Text("Bookmarks, history, shortcuts and AI chats stay on this device. NexVora has no account, analytics or advertising. Local AI runs on-device; the only AI-related network use is a model download you start yourself. Pages you visit are, of course, loaded from their websites.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            NxCard(Modifier.fillMaxWidth(), onClick = { licenses = !licenses }) {
                Text(if (licenses) "Open-source licenses ▲" else "Open-source licenses ▼", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                if (licenses) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Third-party libraries", style = MaterialTheme.typography.labelLarge)
                    Text("AndroidX (WebKit, CameraX, Activity, Lifecycle, Core), Jetpack Compose and Material 3, Kotlin, kotlinx.serialization and kotlinx.coroutines, ZXing core (QR codes) and Google MediaPipe LLM Inference (on-device AI) are all licensed under Apache 2.0. NexVora’s own code is © 2026 NexVora Lab’s Ofc. Any AI model you import has its own license; check it before use. Full texts: see THIRD_PARTY_NOTICES.md in the source repository.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
