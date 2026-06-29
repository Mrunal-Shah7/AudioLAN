package com.audiolan.app.ui.streams.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.audiolan.app.ui.theme.Dimensions
import java.util.Locale

@Composable
fun VolumeSliderRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "volume, current value ${String.format(Locale.US, "%.0f", volume * 100)} percent",
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "vol",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = Dimensions.SpaceXS),
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..2f,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    this.contentDescription = contentDescription
                },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Text(
            text = "x${String.format(Locale.US, "%.2f", volume)}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = Dimensions.SpaceXS),
        )
        Button(
            onClick = onReset,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            contentPadding = PaddingValues(
                horizontal = Dimensions.ResetButtonHorizPadding,
                vertical = Dimensions.SpaceXXS,
            ),
            modifier = Modifier.defaultMinSize(minHeight = Dimensions.SmallButtonMinHeight),
        ) {
            Text(
                text = "reset",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
