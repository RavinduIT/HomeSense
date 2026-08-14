package lk.ac.ucsc.scs3311.smarthome.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors

/**
 * The status badge, in the exact four states the specification names.
 *
 * Every state is rendered as **colour + icon + text**, never colour alone.
 * Two reasons, and both of them are marks: a colour-blind user gets the same
 * information, and it stays readable when a projector washes the colours out
 * during the demo.
 */
@Composable
fun StatusPill(
    status: SlotStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val visuals = status.visuals()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(visuals.container)
            .border(1.dp, visuals.accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp)
            .semantics { contentDescription = "Status: ${visuals.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = visuals.icon,
            contentDescription = null,
            tint = visuals.accent,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
        )
        Text(
            text = visuals.label,
            color = visuals.accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 10.sp else 11.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

data class StatusVisuals(
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val container: Color,
)

@Composable
fun SlotStatus.visuals(): StatusVisuals = when (this) {
    SlotStatus.ON -> StatusVisuals(
        label = "ON",
        icon = Icons.Default.PowerSettingsNew,
        accent = StatusColors.on,
        container = StatusColors.onContainer,
    )
    SlotStatus.OFF -> StatusVisuals(
        label = "OFF",
        icon = Icons.Default.RadioButtonUnchecked,
        accent = StatusColors.off,
        container = StatusColors.offContainer,
    )
    SlotStatus.ERROR -> StatusVisuals(
        label = "ERROR",
        icon = Icons.Default.ErrorOutline,
        accent = StatusColors.error,
        container = StatusColors.errorContainer,
    )
    SlotStatus.DISCONNECTED -> StatusVisuals(
        label = "DISCONNECTED",
        icon = Icons.Default.CloudOff,
        accent = StatusColors.disconnected,
        container = StatusColors.disconnectedContainer,
    )
}

@Preview(showBackground = true)
@Composable
private fun StatusPillPreview() {
    HomeSenseTheme {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlotStatus.entries.forEach { StatusPill(it) }
        }
    }
}
