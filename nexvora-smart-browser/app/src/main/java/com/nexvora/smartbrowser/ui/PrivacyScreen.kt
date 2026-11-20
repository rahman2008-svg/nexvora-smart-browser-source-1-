package com.nexvora.smartbrowser.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@Composable
private fun Head(t: String) = Text(t, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)

@Composable
private fun Note(t: String) = Text(t, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun PrivacyScreen() {
    val app = LocalApp.current; val nav = LocalNav.current; val s = app.settings; val ctx = LocalContext.current; val tm = app.tabs
    val ab by s.adBlock.flow.collectAsState(); val tp by s.thirdPartyCookies.flow.collectAsState(); val dnt by s.doNotTrack.flow.collectAsState(); val js by s.javascript.flow.collectAsState()
    val cam by s.permCamera.flow.collectAsState(); val mic by s.permMic.flow.collectAsState(); val loc by s.permLocation.flow.collectAsState()
    val allow by s.allowlist.flow.collectAsState(); val blocked by app.blocker.blocked.collectAsState(); val listSize by app.blocker.listSize.collectAsState()
    var clearDialog by remember { mutableStateOf(false) }
    var siteRefresh by remember { mutableIntStateOf(0) }
    val sites = remember(siteRefresh) { app.sitePerms.all() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val text = runCatching { ctx.contentResolver.openInputStream(it)!!.bufferedReader().use { r -> r.readText() } }.getOrNull()
            if (text == null) Toast.makeText(ctx, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            else app.blocker.importList(text)
                .onSuccess { n -> Toast.makeText(ctx, "Added $n new hosts to the blocklist", Toast.LENGTH_SHORT).show() }
                .onFailure { e -> Toast.makeText(ctx, e.message ?: "Import failed", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NxTopBar("Privacy Center", { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            NxCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(40.dp), tint = Nx.Cyan)
                    Column { Text("Your privacy, your control", style = MaterialTheme.typography.titleMedium); Text("Every switch here changes real browser behavior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Head("Private browsing")
            SettingRow("Open a private tab", "No history saved from private tabs", trailing = { Icon(Icons.Filled.VisibilityOff, null) }, onClick = { val t = tm.newTab(isPrivate = true); tm.select(t.id); nav.root(Screen.Home); nav.focusAddress = true })
            SettingRow("Close all private tabs", "${tm.privateCount} open", onClick = { tm.closeAll(privateOnly = true) })
            Note(if (tm.privateIsolated) "On this device private tabs use a separate WebView profile. Their cookies, storage and cache are deleted when the last private tab closes."
                else "This device's WebView can't separate private cookies from normal ones. Private tabs skip history, caching and local storage, but cookies share the normal store. Use Clear cookies below afterwards if that matters to you.")

            Head("Protection")
            SwitchRow("Block known ad and tracker hosts", "$listSize hosts on the list. $blocked requests blocked since NexVora started.", ab) { s.adBlock.value = it }
            val https by s.httpsOnly.flow.collectAsState()
            SwitchRow("HTTPS-only mode", "Upgrades http:// to https://; sites without HTTPS fail to load", https) { s.httpsOnly.value = it }
            SwitchRow("Allow third-party cookies", null, tp) { s.thirdPartyCookies.value = it; tm.applySettingsToTabs() }
            SwitchRow("Do Not Track / Global Privacy Control", "Sent with page navigations only; sites may ignore it", dnt) { s.doNotTrack.value = it }
            SwitchRow("JavaScript", "Many sites need it", js) { s.javascript.value = it; tm.applySettingsToTabs() }
            SettingRow("Import a blocklist", "A hosts-format or one-host-per-line text file", onClick = { importer.launch(arrayOf("text/*", "*/*")) })
            SettingRow("Reset blocked counter", null, onClick = { app.blocker.resetCount() })
            SettingRow("Remove imported lists", null, onClick = { app.blocker.clearImported() })
            if (allow.isNotBlank()) SettingRow("Sites with blocking off", allow.replace(",", ", "), trailing = { TextButton(onClick = { s.allowlist.value = "" }) { Text("Clear") } })
            Note("Limits: blocking works on request hostnames only. It doesn't hide page elements, can't remove ads served from a site's own domain (such as video ads), and the starter list is small. Import a larger list for wider coverage. Lists are never updated over the network automatically.")

            Head("Website permissions")
            SwitchRow("Camera: let sites ask", "Off blocks every camera request", cam == "ask") { s.permCamera.value = if (it) "ask" else "block" }
            SwitchRow("Microphone: let sites ask", "Off blocks every microphone request", mic == "ask") { s.permMic.value = if (it) "ask" else "block" }
            SwitchRow("Location: let sites ask", "Off blocks every location request", loc == "ask") { s.permLocation.value = if (it) "ask" else "block" }
            Note("Web notifications aren't supported by Android WebView, so sites can't send them through NexVora. Android also asks for the system permission the first time a feature is used.")
            if (sites.isEmpty()) Note("No saved site decisions.") else {
                sites.forEach { (origin, kind, ok) ->
                    SettingRow(origin.removePrefix("https://").removePrefix("http://"), "$kind: ${if (ok) "allowed" else "blocked"}", trailing = { TextButton(onClick = { app.sitePerms.remove(origin, kind); siteRefresh++ }) { Text("Reset") } })
                }
                SettingRow("Reset all site decisions", null, onClick = { app.sitePerms.clear(); siteRefresh++ })
            }

            Head("Security")
            Note("Pages with invalid certificates are blocked and can't be bypassed. Google Safe Browsing is used where the device's WebView supports it. The padlock in the address bar shows the connection type; tap it for certificate details.")

            Head("Clear data")
            SettingRow("Clear browsing data…", "History, cookies, cache and site data", trailing = { Icon(Icons.Filled.DeleteSweep, null) }, onClick = { clearDialog = true })
            Note("Download records live in Android's download manager; remove them from the Downloads screen (this also deletes the files).")
        }
    }

    if (clearDialog) {
        var h by remember { mutableStateOf(true) }; var c by remember { mutableStateOf(true) }; var ca by remember { mutableStateOf(true) }; var sd by remember { mutableStateOf(false) }; var sp by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Clear browsing data") },
            text = { Column {
                @Composable fun Row2(label: String, v: Boolean, set: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(v, set); Text(label) }
                Row2("Browsing history", h) { h = it }; Row2("Cookies (signs you out of sites)", c) { c = it }; Row2("Cache", ca) { ca = it }
                Row2("Site storage (local data)", sd) { sd = it }; Row2("Saved site permissions", sp) { sp = it }
            } },
            confirmButton = { TextButton(onClick = {
                if (h) { app.repo.clearHistory(); tm.tabs.forEach { it.webView?.clearHistory() } }
                tm.clearBrowsingData(cache = ca, cookies = c, siteData = sd)
                if (sp) { app.sitePerms.clear(); siteRefresh++ }
                clearDialog = false; Toast.makeText(ctx, "Data cleared", Toast.LENGTH_SHORT).show()
            }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
    }
}
