package com.audiolan.app.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary

@Composable
fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requiresRestart: Boolean = false,
    readOnly: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (readOnly) 0.6f else 1.0f)
            .clickable(enabled = !readOnly, onClick = onClick),
        color = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.RowCornerRadius),
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
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
            if (requiresRestart) {
                Text(
                    text = "(requires restart)",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = value,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
