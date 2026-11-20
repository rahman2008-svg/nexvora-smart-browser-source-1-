package com.nexvora.smartbrowser.ui

import androidx.compose.runtime.*
import com.nexvora.smartbrowser.ai.PageContext

sealed interface Screen {
    data object Home : Screen
    data object Browser : Screen
    data object Tabs : Screen
    data object Ai : Screen
    data object Downloads : Screen
    data object Bookmarks : Screen
    data object History : Screen
    data object Privacy : Screen
    data object Reader : Screen
    data object QrScanner : Screen
    data object Tools : Screen
    data object About : Screen
    data class Settings(val section: String? = null) : Screen
}

/** An AI request created elsewhere (e.g. "Explain selection" in the browser) and consumed by the AI screen. */
data class PendingAi(val prompt: String, val shown: String, val page: PageContext?)

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top: Screen get() = stack.last()
    var pendingAi by mutableStateOf<PendingAi?>(null)
    var focusAddress by mutableStateOf(false)

    fun push(s: Screen) { if (top != s) stack.add(s) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    /** Switch to a top-level destination, clearing anything stacked above Home. */
    fun root(s: Screen) { stack.clear(); stack.add(Screen.Home); if (s != Screen.Home) stack.add(s) }
}
