package com.jarvismini.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisOffline
import com.jarvismini.ui.theme.JarvisOnline
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary

/**
 * One live status shown in [StatusStrip].
 *
 * [state] is the word shown to the user ("Active", "Offline", "Unknown"), and [ok] picks the
 * dot colour: true = online, false = offline, null = unknown/neutral. Build these from real
 * state only; do not pass literal "Online" strings for things that are not being checked.
 */
data class StatusChipData(
    val label: String,
    val state: String,
    val ok: Boolean?,
)

/**
 * A single glass row that replaces a stack of status cards: 2 to 3 compact chips side by side.
 * State is carried by the word as well as the dot colour, so it does not rely on colour alone
 * (WCAG 1.4.1), and each chip is announced as one unit, "Voice, Active".
 * Pass [onChipClick] to make chips tappable (48dp minimum height), e.g. to open a detail sheet.
 */
@Composable
fun StatusStrip(
    chips: List<StatusChipData>,
    modifier: Modifier = Modifier,
    onChipClick: ((StatusChipData) -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(JarvisRadius.Card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips.forEach { chip ->
                StatusChip(chip, onChipClick)
            }
        }
    }
}

@Composable
private fun RowScope.StatusChip(
    chip: StatusChipData,
    onChipClick: ((StatusChipData) -> Unit)?,
) {
    val dot = when (chip.ok) {
        true -> JarvisOnline
        false -> JarvisOffline
        null -> JarvisTextSecondary
    }
    val base = Modifier
        .weight(1f)
        .defaultMinSize(minHeight = 48.dp)
        .clip(RoundedCornerShape(JarvisRadius.Small))
    val tappable = if (onChipClick != null) {
        base.clickable(role = Role.Button) { onChipClick(chip) }
    } else {
        base.semantics(mergeDescendants = true) {}
    }
    Column(
        modifier = tappable.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = chip.label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = JarvisTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dot, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = chip.state,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = JarvisTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
