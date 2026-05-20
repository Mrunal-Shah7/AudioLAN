package com.audiolan.app.ui.streams.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.audiolan.app.domain.model.Stream
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.DeleteBackground
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.OnDelete
import com.audiolan.app.ui.theme.LocalToggleThumbColor
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary

@Composable
fun StreamCard(
    stream: Stream,
    onToggle: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onResetVolume: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visibleVolume by remember(stream.id) { mutableFloatStateOf(stream.volume) }
    LaunchedEffect(stream.volume) {
        visibleVolume = stream.volume
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.CardCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${stream.host}:${stream.port}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stream.transportMode.displayName,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = stream.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = if (stream.isEnabled) {
                            "disable ${stream.name}"
                        } else {
                            "enable ${stream.name}"
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalToggleThumbColor.current,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CardBorder,
                    ),
                )
            }
            Spacer(Modifier.height(Dimensions.SpaceS))
            VolumeSliderRow(
                volume = visibleVolume,
                onVolumeChange = {
                    visibleVolume = it
                    onVolumeChange(it)
                },
                onReset = {
                    visibleVolume = 1.0f
                    onResetVolume()
                },
                contentDescription = "volume for ${stream.name}, current value ${
                    String.format(java.util.Locale.US, "%.0f", visibleVolume * 100)
                } percent",
            )
            Spacer(Modifier.height(Dimensions.SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceXS)) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                    contentPadding = PaddingValues(
                        horizontal = Dimensions.SmallButtonHorizPadding,
                        vertical = Dimensions.SmallButtonVerticalPadding,
                    ),
                    modifier = Modifier.heightIn(min = Dimensions.SmallButtonMinHeight),
                ) {
                    Text("open", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeleteBackground,
                        contentColor = OnDelete,
                    ),
                    shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                    contentPadding = PaddingValues(
                        horizontal = Dimensions.SmallButtonHorizPadding,
                        vertical = Dimensions.SmallButtonVerticalPadding,
                    ),
                    modifier = Modifier.heightIn(min = Dimensions.SmallButtonMinHeight),
                ) {
                    Text("delete", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
