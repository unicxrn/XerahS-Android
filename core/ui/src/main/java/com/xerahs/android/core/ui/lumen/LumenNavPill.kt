package com.xerahs.android.core.ui.lumen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class NavPillItem(val key: String, val label: String, val icon: ImageVector)

/** Floating bottom nav. The selected item expands into an accent pill with its label. */
@Composable
fun LumenNavPill(items: List<NavPillItem>, selectedKey: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = Lumen.tokens
    Row(
        modifier
            .shadow(18.dp, CircleShape, ambientColor = t.shadow, spotColor = t.shadow)
            .clip(CircleShape)
            .background(t.navContainer)
            .border(1.dp, t.hairline, CircleShape)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val on = item.key == selectedKey
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, spring(), label = "nav-bg")
            val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.height(48.dp).widthIn(min = 48.dp).clip(CircleShape).background(bg)
                    .clickable(role = Role.Tab) { onSelect(item.key) }
                    .semantics { contentDescription = item.label; selected = on }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(item.icon, null, tint = fg, modifier = Modifier.size(21.dp))
                AnimatedVisibility(on, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}
