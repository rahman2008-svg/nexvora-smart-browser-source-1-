package com.nexvora.smartbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexvora.smartbrowser.MainActivity
import com.nexvora.smartbrowser.NexVoraApp
import com.nexvora.smartbrowser.browser.UrlUtils

val LocalApp = staticCompositionLocalOf<NexVoraApp> { error("NexVoraApp not provided") }
val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }
val LocalMain = staticCompositionLocalOf<MainActivity> { error("MainActivity not provided") }

/** Opens user input (URL or search text) in the current blank tab, or in a new one. */
fun openInput(app: NexVoraApp, nav: Nav, input: String, isPrivate: Boolean = false, forceNew: Boolean = false) {
    val url = UrlUtils.resolve(input, app.settings.engineTemplate()); if (url.isEmpty()) return
    val tm = app.tabs
    val t = (if (!forceNew) tm.current?.takeIf { it.isHome && it.isPrivate == isPrivate } else null) ?: tm.newTab(isPrivate = isPrivate)
    tm.select(t.id); tm.load(t, url); nav.root(Screen.Browser)
}

@Composable
fun NexVoraRoot(app: NexVoraApp, activity: MainActivity) {
    val onboarded by app.settings.onboarded.flow.collectAsState()
    val nav = remember { Nav() }
    val tm = app.tabs

    CompositionLocalProvider(LocalApp provides app, LocalNav provides nav, LocalMain provides activity) {
        if (!onboarded) { OnboardingScreen(); return@CompositionLocalProvider }

        LaunchedEffect(activity.externalOpen) { if (activity.externalOpen) { nav.root(Screen.Browser); activity.externalOpen = false } }
        LaunchedEffect(Unit) {
            val start = app.settings.startupUrl.value
            if (activity.externalOpen) return@LaunchedEffect
            if (start.isNotBlank() && tm.tabs.isEmpty()) openInput(app, nav, start)
            else if (app.settings.restoreTabs.value && tm.restoreSaved()) nav.root(Screen.Tabs)
        }

        BackHandler {
            when {
                tm.customView != null -> tm.hideCustomView()
                nav.top == Screen.Browser && tm.current?.canBack == true -> tm.goBack(tm.current!!)
                !nav.pop() -> activity.moveTaskToBack(true)
            }
        }

        val top = nav.top
        val topLevel = top == Screen.Home || top == Screen.Tabs || top == Screen.Ai
        var menuOpen by remember { mutableStateOf(false) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { if (topLevel) BottomBar(onMenu = { menuOpen = true }) },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (top) {
                    Screen.Home -> HomeScreen()
                    Screen.Browser -> BrowserScreen()
                    Screen.Tabs -> TabsScreen()
                    Screen.Ai -> AiScreen()
                    Screen.Downloads -> DownloadsScreen()
                    Screen.Bookmarks -> BookmarksScreen()
                    Screen.History -> HistoryScreen()
                    Screen.Privacy -> PrivacyScreen()
                    Screen.Reader -> ReaderScreen()
                    Screen.Tools -> ToolsScreen()
                    Screen.QrScanner -> QrScannerScreen()
                    Screen.About -> AboutScreen()
                    is Screen.Settings -> SettingsScreen(top.section)
                }
            }
        }
        if (menuOpen) MenuSheet(onDismiss = { menuOpen = false })
    }
}

@Composable
private fun BottomBar(onMenu: () -> Unit) {
    val app = LocalApp.current; val nav = LocalNav.current
    val top = nav.top
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(Icons.Filled.Home, "Home", top == Screen.Home, Modifier.weight(1f)) { nav.root(Screen.Home) }
            NavItem(Icons.Filled.FilterNone, "Tabs (${app.tabs.tabs.size})", top == Screen.Tabs, Modifier.weight(1f), badge = app.tabs.tabs.size.takeIf { it > 0 }) { nav.root(Screen.Tabs) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Nx.brand)
                        .semantics { contentDescription = "New tab"; role = Role.Button }
                        .clickable { val t = app.tabs.newTab(); app.tabs.select(t.id); nav.root(Screen.Home); nav.focusAddress = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            NavItem(Icons.Filled.AutoAwesome, "AI", top == Screen.Ai, Modifier.weight(1f)) { nav.root(Screen.Ai) }
            NavItem(Icons.Filled.Apps, "Menu", false, Modifier.weight(1f)) { onMenu() }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, badge: Int? = null, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick).semantics { contentDescription = label; role = Role.Tab },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        BadgedBox(badge = { if (badge != null) Badge { Text("$badge") } }) { Icon(icon, null, tint = tint) }
        Text(label.substringBefore(" ("), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(onDismiss: () -> Unit) {
    val nav = LocalNav.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf(
            Triple(Icons.Filled.Download, "Downloads", Screen.Downloads), Triple(Icons.Filled.Star, "Bookmarks", Screen.Bookmarks),
            Triple(Icons.Filled.History, "History", Screen.History), Triple(Icons.Filled.Shield, "Privacy Center", Screen.Privacy),
            Triple(Icons.Filled.AutoStories, "Reader mode", Screen.Reader), Triple(Icons.Filled.Build, "Tools", Screen.Tools),
            Triple(Icons.Filled.Settings, "Settings", Screen.Settings()), Triple(Icons.Filled.Info, "About", Screen.About),
        )
        Column(Modifier.padding(bottom = 24.dp)) {
            items.forEach { (icon, label, screen) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onDismiss(); nav.push(screen) }.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.secondary); Text(label, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
