package com.nexvora.smartbrowser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector?, val title: String, val body: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen() {
    val app = LocalApp.current
    val pages = listOf(
        Page(null, "NexVora Smart AI Browser", "Browse • Search • Ask AI • Explore More.\nA fast browser that works fully on its own."),
        Page(Icons.Filled.AutoAwesome, "Smart AI, on your device", "Summarize pages, explain text and take notes. Add a model once and it runs offline. AI is optional; browsing never depends on it."),
        Page(Icons.Filled.Shield, "Private and yours to shape", "Private tabs, tracker blocking, clear-data controls, themes and a home page you arrange yourself."),
        Page(Icons.Filled.Tune, "Ready when you are", "NexVora asks for camera, microphone or location only when a site you visit needs it."),
    )
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val finish = { app.settings.onboarded.value = true }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state.currentPage < pages.lastIndex) TextButton(onClick = finish) { Text("Skip") } else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(state, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (p.icon == null) NxLogo(140.dp) else
                    Box(Modifier.size(120.dp).clip(CircleShape).background(Nx.Purple.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, tint = Nx.Cyan, modifier = Modifier.size(56.dp))
                    }
                Spacer(Modifier.height(32.dp))
                Text(p.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(Modifier.padding(4.dp).size(if (i == state.currentPage) 10.dp else 8.dp).clip(CircleShape)
                    .background(if (i == state.currentPage) Nx.Cyan else MaterialTheme.colorScheme.outline))
            }
        }
        GradientButton(if (state.currentPage == pages.lastIndex) "Get started" else "Next", Modifier.fillMaxWidth()) {
            if (state.currentPage == pages.lastIndex) finish() else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
        }
    }
}
