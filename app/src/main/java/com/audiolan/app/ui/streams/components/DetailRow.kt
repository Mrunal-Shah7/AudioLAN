package com.audiolan.app.ui.streams.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.StatusError
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.SurfaceFocused
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary

@Composable
fun DetailRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    readOnly: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !readOnly,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        color = if (isPressed) SurfaceFocused else AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.RowCornerRadius),
        border = BorderStroke(
            width = Dimensions.CardBorderWidth,
            color = if (isPressed) MaterialTheme.colorScheme.primary else CardBorder,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimensions.RowHorizontalPadding,
                vertical = Dimensions.RowVerticalPadding,
            ),
        ) {
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = StatusError,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimensions.SpaceXXS),
                )
            }
        }
    }
}
