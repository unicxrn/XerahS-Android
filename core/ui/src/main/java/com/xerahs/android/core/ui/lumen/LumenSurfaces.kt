package com.xerahs.android.core.ui.lumen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Surface card: hairline outline plus a soft, accent-tinted shadow. */
@Composable
fun LumenCard(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    contentPadding: Dp = 0.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = Lumen.tokens
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .shadow(if (t.isDark) 0.dp else elevation, shape, ambientColor = t.shadow, spotColor = t.shadow)
            .clip(shape)
            .background(color)
            .border(1.dp, t.hairline, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** Double-bezel card: tinted 5dp shell around an inner [LumenCard]. */
@Composable
fun BezelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shell = RoundedCornerShape(30.dp)
    Box(
        modifier
            .clip(shell)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Lumen.tokens.hairline, shell)
            .padding(5.dp),
        propagateMinConstraints = true
    ) {
        LumenCard(modifier = Modifier.fillMaxWidth(), radius = 25.dp, elevation = 0.dp, onClick = onClick, content = content)
    }
}

/** Rounded-square tinted icon holder. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    container: Color = Lumen.tokens.tint,
    tint: Color = Lumen.tokens.ink,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.33f)).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** App brand mark: a rounded primary tile with the focus-mark icon, plus the wordmark. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.32f))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CenterFocusWeak,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.59f)
            )
        }
        Text("XerahS", style = MaterialTheme.typography.titleLarge)
    }
}

/** Radial accent glow, placed behind a screen's header. Purely decorative. */
@Composable
fun AccentGlow(modifier: Modifier = Modifier, size: Dp = 360.dp) {
    val glow = Lumen.tokens.glow
    Box(
        modifier.size(size).background(
            Brush.radialGradient(listOf(glow, Color.Transparent))
        )
    )
}

/** Screenshot-style corner brackets over an image preview. */
@Composable
fun BoxScope.CaptureCorners(modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.9f), inset: Dp = 10.dp, arm: Dp = 16.dp) {
    Canvas(modifier.matchParentSize().padding(inset)) {
        val a = arm.toPx()
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        fun corner(p: Path) = drawPath(p, color, style = stroke)
        corner(Path().apply { moveTo(0f, a); lineTo(0f, 0f); lineTo(a, 0f) })
        corner(Path().apply { moveTo(w - a, 0f); lineTo(w, 0f); lineTo(w, a) })
        corner(Path().apply { moveTo(w, h - a); lineTo(w, h); lineTo(w - a, h) })
        corner(Path().apply { moveTo(a, h); lineTo(0f, h); lineTo(0f, h - a) })
    }
}

/** Placeholder art for files with no thumbnail: accent gradient with a hill line. */
@Composable
fun AccentArt(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = Lumen.tokens.tint
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(tint, accent), start = Offset.Zero, end = Offset(size.width, size.height)))
        val p = Path().apply {
            moveTo(0f, size.height * 0.78f)
            lineTo(size.width * 0.25f, size.height * 0.62f)
            lineTo(size.width * 0.5f, size.height * 0.74f)
            lineTo(size.width * 0.75f, size.height * 0.52f)
            lineTo(size.width, size.height * 0.68f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(p, Color.Black.copy(alpha = 0.22f))
    }
}

/**
 * The "done" moment: a tinted 74dp ring around a 58dp primary check circle, with a two-line
 * displaySmall headline whose second line sits in onSurfaceVariant. [scale] lets a caller animate
 * the check circle in (e.g. a spring from 0.6f to 1f on first composition).
 */
@Composable
fun SuccessBadge(headline: String, subline: String, modifier: Modifier = Modifier, scale: Float = 1f) {
    Column(modifier) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .background(Lumen.tokens.tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .scale(scale)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            buildAnnotatedString {
                append("$headline\n")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(subline)
                }
            },
            style = MaterialTheme.typography.displaySmall
        )
    }
}
