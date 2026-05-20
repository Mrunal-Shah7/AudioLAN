package com.audiolan.app.ui.streams.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.audiolan.app.ui.theme.DeleteBackground
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary

@Composable
fun DeleteConfirmDialog(
    streamName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AudioLANSurface,
        shape = RoundedCornerShape(Dimensions.SpaceM),
        title = {
            Text(
                text = "delete stream?",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = "\"$streamName\" will be permanently deleted. This action cannot be undone.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "delete",
                    color = DeleteBackground,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "cancel",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}
