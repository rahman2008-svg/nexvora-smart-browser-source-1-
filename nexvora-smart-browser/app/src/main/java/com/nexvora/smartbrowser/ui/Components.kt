package com.nexvora.smartbrowser.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun cardColor() = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun NxCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val m = modifier.clip(shape).background(cardColor()).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    Column(if (onClick != null) m.clickable(onClick = onClick) else m, content = content)
}

/** The NexVora mark: a gradient N with an orbit arc, drawn in code so it scales cleanly. */
@Composable
fun NxLogo(size: Dp = 72.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size).semantics { contentDescription = "NexVora logo" }) {
        val s = this.size.minDimension / 108f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val n = Path().apply {
            moveTo(38 * s, 34 * s); lineTo(38 * s, 74 * s); lineTo(46 * s, 74 * s); lineTo(46 * s, 50 * s)
            lineTo(62 * s, 74 * s); lineTo(70 * s, 74 * s); lineTo(70 * s, 34 * s); lineTo(62 * s, 34 * s)
            lineTo(62 * s, 58 * s); lineTo(46 * s, 34 * s); close()
        }
        drawPath(n, Brush.linearGradient(listOf(Nx.Purple2, Nx.Cyan), p(38f, 34f), p(70f, 74f)))
        val arc = Path().apply {
            moveTo(31 * s, 62 * s); cubicTo(28 * s, 50 * s, 44 * s, 36 * s, 66 * s, 32 * s); cubicTo(80 * s, 30 * s, 84 * s, 36 * s, 78 * s, 44 * s)
        }
        drawPath(arc, Brush.linearGradient(listOf(Nx.Purple, Nx.Cyan), p(28f, 62f), p(82f, 32f)), style = androidx.compose.ui.graphics.drawscope.Stroke(2.4f * s, cap = StrokeCap.Round))
    }
}

@Composable
fun IconTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.secondary, onClick: () -> Unit) {
    NxCard(modifier.heightIn(min = 56.dp), onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp))

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Nx.brand.let { Brush.linearGradient(listOf(Nx.Purple.copy(.25f), Nx.Cyan.copy(.25f))) }), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NxTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(title, subtitle, trailing = { Switch(checked, onChange) }, onClick = { onChange(!checked) })

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Nx.brand).clickable(onClick = onClick).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}
