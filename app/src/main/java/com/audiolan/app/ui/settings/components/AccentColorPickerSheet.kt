package com.audiolan.app.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorPickerSheet(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = AudioLANSurface,
        shape = RoundedCornerShape(
            topStart = Dimensions.SpaceM,
            topEnd = Dimensions.SpaceM,
        ),
    ) {
        Column {
            Text(
                text = "accent color",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    horizontal = Dimensions.SpaceM,
                    vertical = Dimensions.SpaceL,
                ),
            )
            AccentColor.entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(entry)
                            onDismiss()
                        }
                        .padding(
                            horizontal = Dimensions.SpaceM,
                            vertical = Dimensions.SpaceM,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(entry.primary)
                            .then(
                                if (entry == selected) {
                                    Modifier.border(2.dp, TextPrimary, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    Spacer(Modifier.width(Dimensions.SpaceM))
                    Text(
                        text = entry.displayName,
                        color = if (entry == selected) entry.primary else TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (entry == selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = entry.primary,
                        )
                    }
                }
                if (index != AccentColor.entries.lastIndex) {
                    HorizontalDivider(
                        color = CardBorder,
                        thickness = Dimensions.CardBorderWidth,
                    )
                }
            }
        }
    }
}
