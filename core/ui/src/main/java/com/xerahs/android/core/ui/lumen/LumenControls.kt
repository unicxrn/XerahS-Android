package com.xerahs.android.core.ui.lumen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Small uppercase mono label above a section or heading. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(fontFamily = Lumen.tokens.mono, fontSize = 11.sp, letterSpacing = 0.06.em, fontWeight = FontWeight.Medium),
        color = color
    )
}

/** Mono text style for links, sizes and timestamps. */
@Composable
fun monoStyle(size: Int = 13): TextStyle =
    TextStyle(fontFamily = Lumen.tokens.mono, fontSize = size.sp, fontWeight = FontWeight.Medium)

/** Full-width accent pill with the trailing icon nested in its own circle. Presses scale to 0.98. */
@Composable
fun PillCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.98f else 1f, spring(), label = "cta-scale")
    val nudge by animateDpAsState(if (pressed) 2.dp else 0.dp, spring(), label = "cta-nudge")
    val ripple = LocalIndication.current
    Row(
        modifier
            .scale(scale)
            .fillMaxWidth()
            .height(60.dp)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(interactionSource = source, indication = ripple, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .then(if (loading) Modifier.semantics { stateDescription = "Loading" } else Modifier)
            .padding(start = 24.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val foreground = if (enabled) content else content.copy(alpha = 0.6f)
        Text(text, color = foreground, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.size(46.dp).offset(x = nudge, y = -nudge / 2).clip(CircleShape).background(content.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = foreground, strokeWidth = 2.dp)
            else Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        }
    }
}

/** Host name with its brand dot. */
@Composable
fun HostChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.height(24.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Accent switch: 50x30 track, white knob that springs across. */
@Composable
fun LumenSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "switch-track")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.7f), label = "switch-knob")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .border(1.dp, Lumen.tokens.hairline, CircleShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(3.dp)
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

data class SegmentOption<T>(val value: T, val label: String, val icon: ImageVector)

/** Icon + label segmented picker inside a tinted track; the selected tile lifts to a card. */
@Composable
fun <T> SegmentedTiles(options: List<SegmentOption<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, RoundedCornerShape(24.dp)).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { o ->
            val on = o.value == selected
            Column(
                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(o.value) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(o.icon, null, tint = if (on) Lumen.tokens.ink else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(6.dp))
                Text(o.label, style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 44dp circular icon button on a surface card. */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Lumen.tokens.hairline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f), modifier = Modifier.size(20.dp))
    }
}

/**
 * Lumen screen header: circular back button (when [onBack] is set), a big Inter Tight title,
 * and trailing actions. Handles the status bar inset itself; use with Scaffold(topBar = ...).
 */
@Composable
fun LumenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    windowInsets: WindowInsets = WindowInsets.statusBars,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(windowInsets)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, onBack)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
