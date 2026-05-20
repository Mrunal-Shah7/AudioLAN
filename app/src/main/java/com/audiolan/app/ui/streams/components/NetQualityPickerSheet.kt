package com.audiolan.app.ui.streams.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.audiolan.app.domain.model.NetQuality
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetQualityPickerSheet(
    selected: NetQuality,
    onSelect: (NetQuality) -> Unit,
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
                text = "net quality",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    horizontal = Dimensions.SpaceM,
                    vertical = Dimensions.SpaceL,
                ),
            )
            NetQuality.entries.forEachIndexed { index, entry ->
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
                    Text(
                        text = entry.name.lowercase(Locale.US).replace('_', ' '),
                        color = if (entry == selected) MaterialTheme.colorScheme.primary else TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (entry == selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (index != NetQuality.entries.lastIndex) {
                    HorizontalDivider(
                        color = CardBorder,
                        thickness = Dimensions.CardBorderWidth,
                    )
                }
            }
        }
    }
}
