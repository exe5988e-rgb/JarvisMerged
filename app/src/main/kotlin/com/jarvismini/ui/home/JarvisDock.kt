package com.jarvismini.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisCyan
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.pressScale

/** One primary action shown in [JarvisDock]. */
data class DockItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Bottom glass dock: the few actions people use most, plus a trailing "More" button
 * that the caller wires to a bottom sheet for everything else.
 *
 * Built on GlassSurface (glass-compose) with the outer radius (Large, 20dp) larger than
 * the inner button radius (Card, 12dp) so the corners stay concentric
 * (make-interfaces-feel-better). Each button is a 56dp-high target (>= 48dp, WCAG 2.2),
 * icon + text label, scaled to 0.96 on press via pressScale (honours the reduced-motion gate).
 * Intended for 3 to 4 [items]; more than 4 will crowd small phones, so move extras behind More.
 */
@Composable
fun JarvisDock(
    items: List<DockItem>,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(JarvisRadius.Large),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                DockButton(item.label, item.icon, item.onClick)
            }
            DockButton("More", Icons.Default.MoreHoriz, onMore)
        }
    }
}

@Composable
private fun RowScope.DockButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // indication = null removes the default focus highlight, so draw a visible ring for
    // keyboard / switch-access focus (WCAG 2.4.7).
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(JarvisRadius.Card)
    Column(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 56.dp)
            .pressScale(interaction)
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, JarvisCyan, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The clickable merges its children, so the button is announced by its text label.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JarvisCyan,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = JarvisTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
